# mantis.inspect package submodule - AI Agent Harness
import os
import sys
from google import genai
from google.genai import types
from .tools import grep_codebase, read_file_lines, git_read_operations

def run_triage_analysis(prompt_payload: str) -> str:
    """
    Instantiates a Google GenAI session, binds codebase & git tools,
    and executes an automated diagnostic triage using gemini-2.5-pro.
    
    Args:
        prompt_payload: A highly structured string containing the failed run logs,
                        available context catalog, and reference timelines.
                        
    Returns:
        The standard markdown diagnostic analysis text from Gemini.
    """
    token = os.getenv("GEMINI_API_KEY")
    if not token:
        print("Error: The environment variable 'GEMINI_API_KEY' is not set.", file=sys.stderr)
        print("Please set your Gemini API Key before executing the triage agent.", file=sys.stderr)
        print("Example: export GEMINI_API_KEY=\"AIzaSy...\"", file=sys.stderr)
        sys.exit(1)

    print("Initializing Gemini Triage Agent (Mantis Diagnose)...")
    try:
        # 1. Initialize client (standard google.genai SDK)
        client = genai.Client()
        
        # 2. Compile tool belt
        tools_list = [grep_codebase, read_file_lines, git_read_operations]
        
        # 3. Define system guidelines
        system_instruction = (
            "You are Mantis Diagnose, a highly analytical AI diagnostic triage agent "
            "designed to investigate failed test runs in the UDMI codebase. "
            "Your goal is to inspect execution traces, code context, and Git history to "
            "determine why a test failed and isolate the breakpoint with exact evidence.\n\n"
            "Guidelines:\n"
            "1. You will be provided with log segments and metadata detailing available sources.\n"
            "2. You must attempt to discover the sequence of events leading to the failure.\n"
            "3. Use your grep_codebase and read_file_lines tools to dynamically retrieve relevant codebase context (Java sequence files, UDMIS processors, Pubber emulators).\n"
            "4. DO NOT use git_read_operations to search for past successful runs in the site model repo. Sibling logs of a successful golden reference run are already loaded and provided directly in the prompt payload under '## Reference Successful Run Details'. Focus entirely on comparing the failed and successful log traces side-by-side to locate differences.\n"
            "5. Use git_read_operations on the main repo ONLY if you suspect a recent codebase commit introduced a regression, to query recent logs/diffs.\n"
            "6. CRITICAL SUFFICIENCY RULE: If the available logs, git history, and code logic are insufficient to isolate the breakpoint, "
            "you MUST return a summary starting with the exact header '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' in your Breakpoint Summary. "
            "Do not speculate or make up hypothetical failures. List exactly what logs are missing and recommend how the developer can enable them on the next run.\n"
            "7. DIFFERENTIAL ANALYSIS: You MUST compare the failed log traces side-by-side to the successful reference log traces to locate differences and pinpoint exactly where they diverged.\n"
            "8. PREFER LOCAL FILES: Always use the `read_file_lines` tool to read site model files (like `sites/udmi_site_model/devices/AHU-1/metadata.json`) directly from the local disk. Do NOT use `git_read_operations` to read these files unless you specifically need to view past committed history or diffs.\n\n"
            "REQUIRED OUTPUT FORMAT:\n"
            "You MUST format your final response strictly using the following markdown headers and structure:\n\n"
            "## Summary\n"
            "Provide a clear overview of the failure (2-3 sentences): what test is failing, on which device/gateway, and during which execution phase.\n\n"
            "## 2. Breakpoint Summary\n"
            "> [Provide a concise, single-line summary starting with a blockquote '>' identifying the primary component failure and exact error, e.g. 'Pubber simulator failed to transition state in time' or 'Sequencer assertion timed out during config sync'. This exact line is parsed by the triage consolidation engine.]\n\n"
            "## Component Assessment\n"
            "Answer these three specific questions clearly:\n"
            "1. **Did the device work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n"
            "2. **Did sequencer work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n"
            "3. **Did udmis work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n\n"
            "## Expected Behavior (When and Why it Passed Previously)\n"
            "Explain the execution flow when the test was successful in the reference sibling run (what actions the Sequencer, Device, and UDMIS took and why it succeeded).\n\n"
            "## Current Behavior (When and Why it Fails Now)\n"
            "Detail the execution flow in the current failing run (what events occurred, what values were transmitted, and what error/timeout was triggered).\n\n"
            "## Root Cause & Logic Conflict\n"
            "Analyze the underlying conflict or code issue causing the discrepancy (e.g. strictness of sequencer checks vs standard device capabilities, timeout configurations, or software regressions).\n\n"
            "## Logs / Evidence\n"
            "Present matching snippets from the failing log run alongside matching snippets from the passing reference run to illustrate and prove your findings."
        )
        
        # 4. Configure content generations config
        config = types.GenerateContentConfig(
            tools=tools_list,
            temperature=0.1,  # Low temperature for deterministic, highly logical logs analysis
            system_instruction=system_instruction,
            automaticFunctionCalling=types.AutomaticFunctionCallingConfig(
                maximumRemoteCalls=25
            )
        )
        
        print("Invoking Gemini model for diagnostics... (This may take a few moments to search codebase/git logs)")
        # 5. Models generation content (resolves dynamic tool calling automatically)
        response = client.models.generate_content(
            model="gemini-3.1-flash-lite",
            contents=prompt_payload,
            config=config
        )
        
        if response.text:
            return response.text
            
        # Fallback if tool recursion limit was hit or response.text is None
        parts_desc = []
        if response.candidates and response.candidates[0].content:
            for part in response.candidates[0].content.parts:
                if part.function_call:
                    parts_desc.append(f"Function Call: {part.function_call.name}({part.function_call.args})")
                if part.text:
                    parts_desc.append(part.text)
                    
        fallback_text = (
            "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE\n\n"
            "The Gemini diagnostic agent completed its tool execution but did not return a final text analysis.\n"
            "This typically indicates the model reached the maximum tool-calling loop limit (10 turns) while recursively searching codebase or git logs.\n\n"
            "**Last Executed Model Actions:**\n" + "\n".join(f"- {p}" for p in parts_desc)
        )
        return fallback_text
    except Exception as e:
        print(f"Error communicating with Gemini API: {e}", file=sys.stderr)
        raise e
