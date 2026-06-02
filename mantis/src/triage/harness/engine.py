import asyncio
import inspect
import json
import os
from google.genai import types
from typing import Any
from typing import Callable
from typing import Dict
from typing import List

from .constants import DEFAULT_GEMINI_PRO_MODEL

from .ui import color_text, YELLOW, RED, GREEN, BLUE


def _get_response_text(response: Any) -> str:
    """Safely extracts text from a GenerateContentResponse without triggering warnings."""
    if not response or not response.candidates:
        return ""
    try:
        parts = response.candidates[0].content.parts
        if parts:
            text_parts = [part.text for part in parts if part.text]
            if text_parts:
                return "\n".join(text_parts)
    except Exception:
        pass
    return ""


class AsyncTriageEngine:
    """
    A high-performance, asynchronous GenAI orchestration engine for log triage.
    Handles rate limits (429), automatic tool execution, loop guardrails,
    and concurrency control across multi-triage jobs.
    """

    def __init__(
        self,
        client: Any,
        model_name: str = DEFAULT_GEMINI_PRO_MODEL,
        concurrency_semaphore: asyncio.Semaphore = None,
        max_loops: int = 15,
        base_wait_seconds: float = 4.0,
        max_retries: int = 5
    ):
        self.client = client
        self.model_name = model_name
        self.semaphore = concurrency_semaphore or asyncio.Semaphore(3)
        self.max_loops = max_loops
        self.base_wait_seconds = base_wait_seconds
        self.max_retries = max_retries

    async def _condense_tool_result(self, name: str, result: str) -> str:
        """Strategy 1: Structurally truncates and dynamically condenses large tool outputs via Gemini 2.5 Flash Lite."""
        if len(result) <= 25000:
            return result

        print(color_text(f" └─ [Guardrail]: Condensing large output from tool '{name}' ({len(result)} characters)...", BLUE))

        # 1. First, perform quick structural truncation if extremely large
        lines = result.splitlines()
        if len(lines) > 120:
            truncated = "\n".join(lines[:50]) + \
                        f"\n\n... [ {len(lines) - 100} lines of output structurally truncated by Mantis Engine to conserve tokens ] ...\n\n" + \
                        "\n".join(lines[-50:])
        else:
            truncated = result

        # 2. Use super-cheap Gemini 2.5 Flash Lite to distill/summarize
        try:
            summary_prompt = (
                f"You are a Triage Tool Output Condenser. The following is the raw output from executing the tool '{name}'. "
                f"Summarize, compact, or prune this output, retaining ONLY the specific file paths, line numbers, "
                f"method signatures, variable declarations, or failing log tracks relevant to diagnosing the system failure. "
                f"Discard all imports, boilerplate, normal success pathways, or redundant data. "
                f"Keep your output extremely dense, concise, and strictly technical. Omit all introductory/concluding filler.\n\n"
                f"Raw Tool Output:\n{truncated}"
            )
            # Using standard flash-lite for safe, cheap, high-speed token distillation
            response = await self.client.aio.models.generate_content(
                model="gemini-2.5-flash-lite",
                contents=summary_prompt,
                config=types.GenerateContentConfig(temperature=0.1)
            )
            if response.text:
                condensed = response.text.strip()
                print(color_text(f" └─ [Guardrail]: Condensed '{name}' output from {len(result)} to {len(condensed)} characters.", GREEN))
                return condensed
        except Exception as e:
            print(f"Warning: Failed LLM-driven tool output condensation: {e}")

        return truncated

    async def _compact_history_if_bloated(self, history: List[Any]) -> None:
        """Strategy 2: Compacts active history to compile a Consolidated Progress Checkpoint and reset token context."""
        # Only compact if the history has accumulated multiple conversation turns
        if len(history) < 12:
            return

        print(color_text("\n[Guardrail]: Compacting active context history to prevent token bloat & 429 rate limits...", RED, bold=True))
        try:
            compaction_prompt = (
                "You are a Triage State Conservator. Read the active diagnostic session history above. "
                "Compile a single, highly condensed markdown progress summary. You must list:\n"
                "1. The exact directories and codebase files we successfully listed or searched.\n"
                "2. The exact class, interface, method, or function definitions we retrieved.\n"
                "3. A bulleted chronological summary of the system log files we evaluated.\n"
                "4. Our current hypotheses, identified vulnerabilities, and eliminated possibilities.\n\n"
                "Discard all intermediate raw tool output blocks, duplicate log messages, and conversational logs. "
                "Keep the output extremely dense, technical, and structured. Omit all introduction or conclusion text."
            )
            # Prepare a temporary history copy containing the compaction prompt
            temp_history = history.copy()
            temp_history.append(types.Content(role="user", parts=[types.Part.from_text(text=compaction_prompt)]))

            response = await self.client.aio.models.generate_content(
                model="gemini-2.5-flash-lite",
                contents=temp_history,
                config=types.GenerateContentConfig(temperature=0.1)
            )
            if response.text:
                checkpoint_text = (
                    f"### 🔔 SYSTEM CONTEXT COMPACTION CHECKPOINT 🔔\n"
                    f"The Mantis engine has compacted the previous execution history to optimize token usage and rate limits. "
                    f"Here is the consolidated state of our diagnostics so far:\n\n{response.text.strip()}"
                )
                # Retain the very first prompt (user instruction/payload context)
                first_prompt = history[0]
                # Replace history in-place to keep the original list reference
                history[:] = [
                    first_prompt,
                    types.Content(role="user", parts=[types.Part.from_text(text=checkpoint_text)])
                ]
                print(color_text("[Guardrail]: Active context history compacted successfully! Memory reset achieved.", GREEN, bold=True))
        except Exception as e:
            print(f"Warning: History compaction bypassed due to error: {e}")

    async def execute_loop(
        self,
        system_instruction: str,
        history: List[Any],
        tools_map: Dict[str, Callable],
        required_headers: List[str] = None,
        model_name: str = None,
        executed_tool_signatures: set = None
    ) -> str:
        """
        Executes the asynchronous agent loop with pacing, rate limits, and tool dispatches.
        """
        tools_list = list(tools_map.values())
        config = types.GenerateContentConfig(
            tools=tools_list,
            temperature=0.1,
            system_instruction=system_instruction,
            automatic_function_calling=types.AutomaticFunctionCallingConfig(
                disable=True)
        )

        loop_count = 0
        if executed_tool_signatures is None:
            executed_tool_signatures = set()
        response = None

        while loop_count < self.max_loops:
            # If approaching loop limit, force the model to conclude
            if required_headers and loop_count == self.max_loops - 2:
                print(
                    color_text("[Guardrail]:", RED, bold=True) + "\n" +
                    color_text("Approaching loop limit. Forcing agent to conclude.", RED))
                history.append(
                    types.Content(role="user", parts=[types.Part.from_text(
                        text=f"SYSTEM WARNING: Loop limit reached. Immediately output your final analysis using headers: {', '.join(required_headers)}."
                    )])
                )

            # Generate content with concurrency limiting and rate limit retry logic
            model_content = None
            async with self.semaphore:
                for attempt in range(self.max_retries):
                    try:
                        active_model = model_name or self.model_name
                        response = await self.client.aio.models.generate_content(
                            model=active_model,
                            contents=history,
                            config=config
                        )
                        break
                    except Exception as e:
                        err_str = str(e)
                        is_transient = any(token in err_str.upper() for token in [
                            "429", "503", "500", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL", "OVERLOADED"
                        ])
                        if is_transient:
                            wait_time = self.base_wait_seconds * (2 ** attempt)
                            print(
                                f"⚠️ Transient GenAI API Error/Overload hit on {active_model}. Retrying in {wait_time:.1f}s... (Error: {err_str[:120]})")
                            await asyncio.sleep(wait_time)
                            if attempt == self.max_retries - 1:
                                raise
                        else:
                            raise e

            if response.candidates and response.candidates[0].content:
                model_content = response.candidates[0].content
                model_content.role = "model"
                history.append(model_content)
                for part in model_content.parts:
                    if part.text:
                        print(
                            f"\n" + color_text("[Mantis]:", YELLOW, bold=True) + f"\n{part.text.strip()}\n")

            function_calls = response.function_calls
            has_thought = any(part.text and part.text.strip() for part in
                              model_content.parts) if response.candidates else False

            if function_calls and not has_thought:
                history.append(
                    types.Content(role="user", parts=[types.Part.from_text(
                        text="System Warning: Missing Hypothesis. Before EVERY tool call, you MUST output a brief text explanation/scratchpad."
                    )])
                )
                loop_count += 1
                continue

            if not function_calls:
                text_content = _get_response_text(response)
                if required_headers and not any(
                    hdr in text_content for hdr in required_headers):
                    history.append(
                        types.Content(role="user", parts=[types.Part.from_text(
                            text=f"System Reminder: Incomplete response. You must output using headers: {', '.join(required_headers)}."
                        )])
                    )
                    loop_count += 1
                    continue
                else:
                    break

            # Execute the function calls concurrently in parallel
            async def execute_single_tool(fc):
                sig = f"{fc.name}:{json.dumps(fc.args, sort_keys=True)}"
                try:
                    if sig in executed_tool_signatures:
                        return fc.name, f"Tool Error: Duplicate Request '{fc.name}'. Refine your query."
                    elif fc.name in tools_map:
                        executed_tool_signatures.add(sig)
                        tool_func = tools_map[fc.name]
                        if inspect.iscoroutinefunction(tool_func):
                            res = await tool_func(**fc.args)
                        else:
                            res = await asyncio.to_thread(tool_func, **fc.args)
                        return fc.name, res
                    else:
                        return fc.name, f"Error: Unknown tool '{fc.name}'"
                except Exception as e:
                    return fc.name, f"Error executing tool {fc.name}: {e}"

            tool_parts = []
            tool_tasks = [execute_single_tool(fc) for fc in function_calls]
            results = await asyncio.gather(*tool_tasks)

            for name, result in results:
                print(
                    f" └─ Tool '{name}' execution complete ({len(result)} characters returned)")
                condensed_result = await self._condense_tool_result(name, result)
                tool_parts.append(
                    types.Part.from_function_response(name=name, response={
                        "result": condensed_result}))

            history.append(types.Content(role="user", parts=tool_parts))
            loop_count += 1

            if loop_count > 0 and loop_count % 5 == 0:
                await self._compact_history_if_bloated(history)

            use_vertex = os.getenv("MANTIS_USE_VERTEXAI", "").lower() in ("true", "1", "yes")
            pacing_delay = 0.5 if use_vertex else 3.0
            await asyncio.sleep(pacing_delay)

        clean_break = False
        if response and not response.function_calls:
            text_content = _get_response_text(response)
            if not required_headers or any(hdr in text_content for hdr in required_headers):
                clean_break = True

        if response and not clean_break:
            print(
                color_text("[Guardrail]:", RED, bold=True) + " " +
                color_text("Loop limit reached or final report missing. Forcing final summary generation...", RED)
            )
            # Construct a config with no tools
            final_config = types.GenerateContentConfig(
                temperature=0.2,
                system_instruction=system_instruction + "\n\nIMPORTANT: You must now output your final complete diagnostic report using the required headers. Do not attempt to call any more tools."
            )
            history.append(
                types.Content(role="user", parts=[types.Part.from_text(
                    text=f"SYSTEM REQUEST: Loop limit reached. You can no longer call any tools. Please synthesize all the information gathered above and output your final, complete, comprehensive diagnostic report using the required headers: {', '.join(required_headers) if required_headers else ''}"
                )])
            )
            async with self.semaphore:
                for attempt in range(self.max_retries):
                    try:
                        active_model = model_name or self.model_name
                        response = await self.client.aio.models.generate_content(
                            model=active_model,
                            contents=history,
                            config=final_config
                        )
                        break
                    except Exception as e:
                        err_str = str(e)
                        is_transient = any(token in err_str.upper() for token in [
                            "429", "503", "500", "RESOURCE_EXHAUSTED", "UNAVAILABLE", "INTERNAL", "OVERLOADED"
                        ])
                        if is_transient:
                            wait_time = self.base_wait_seconds * (2 ** attempt)
                            print(
                                f"⚠️ Transient GenAI API Error/Overload hit during final summary on {active_model}. Retrying in {wait_time:.1f}s... (Error: {err_str[:120]})")
                            await asyncio.sleep(wait_time)
                            if attempt == self.max_retries - 1:
                                raise
                        else:
                            raise e

        return _get_response_text(response)

