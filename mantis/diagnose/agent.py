# mantis.inspect package submodule - AI Agent Harness
import os
import re
import json
import sys
import time
import asyncio
from pathlib import Path
from google import genai
from google.genai import types
from agentskills_core import SkillRegistry
from agentskills_fs import LocalFileSystemSkillProvider
from .tools import grep_codebase, read_file_lines, git_read_operations

# ANSI Terminal Font Color Constants
COLOR_RESET = "\033[0m"
COLOR_CYAN = "\033[36m"     # Phase 1: Timeline
COLOR_GREEN = "\033[32m"    # Phase 1.5: Intent
COLOR_MAGENTA = "\033[35m"  # Phase 1.75: Behavior Assessment
COLOR_RED = "\033[31m"      # Phase 2 / Guardrail (Red/Alert)
COLOR_YELLOW = "\033[33m"   # Agent Thought (Yellow/Gold)
COLOR_BLUE = "\033[34m"     # Inspect Tool (Blue)
COLOR_BOLD = "\033[1m"

async def initialize_skills_registry(skills_dir: Path) -> str:
    """Asynchronously boots and registers filesystem skill providers, returning the formatted catalog."""
    # 1. Set up the provider for your skill files
    provider = LocalFileSystemSkillProvider(skills_dir)
    
    # 2. Register the skill registry
    registry = SkillRegistry()
    
    # List of (skill_id, provider) tuples for atomic batch registration
    skills_batch = [
        ("progressive-triage-flow", provider),
        ("component-guide", provider),
        ("log-sources", provider),
        ("log-correlation", provider),
        ("evidence-gathering", provider),
        ("best-effort-triage", provider)
    ]
    
    await registry.register(skills_batch)
    
    # 3. Generate the XML skills catalog
    catalog = await registry.get_skills_catalog()
    return catalog

def execute_agent_loop(client, system_instruction: str, history: list, tools_list: list, required_headers: list = None) -> str:
    """Shared helper to execute the manual tool calling loop with pacing, retries, and strict guardrails."""
    config = types.GenerateContentConfig(
        tools=tools_list,
        temperature=0.1,  # Low temperature for deterministic analysis
        system_instruction=system_instruction,
        automatic_function_calling=types.AutomaticFunctionCallingConfig(disable=True)
    )
    
    loop_count = 0
    max_loops = 12
    response = None
    executed_tool_signatures = set()
    
    while loop_count < max_loops:
        # --- BAILOUT INJECTION ---
        if required_headers and loop_count == max_loops - 2:
            print(f"{COLOR_RED}{COLOR_BOLD}[Mantis Guardrail]:{COLOR_RESET}\n{COLOR_RED}Approaching loop limit. Forcing agent to conclude.{COLOR_RESET}")
            history.append(
                types.Content(
                    role="user",
                    parts=[types.Part.from_text(
                        text="SYSTEM WARNING: You have reached the maximum allowed codebase searches. "
                             "You must immediately stop searching and output your final analysis using the "
                             f"required markdown headers: {', '.join(required_headers)} based on the data you have."
                    )]
                )
            )

        max_retries = 5
        base_wait = 4  # seconds
        
        for attempt in range(max_retries):
            try:
                response = client.models.generate_content(
                    model="gemini-3.1-pro-preview",
                    contents=history,
                    config=config
                )
                break  # Success!
            except Exception as e:
                err_str = str(e)
                if "429" in err_str or "RESOURCE_EXHAUSTED" in err_str:
                    wait_time = base_wait * (2 ** attempt)
                    print(f"⚠️ Rate limit hit (429). Retrying in {wait_time} seconds (Attempt {attempt + 1}/{max_retries})...")
                    time.sleep(wait_time)
                    if attempt == max_retries - 1:
                        raise
                else:
                    raise e
                    
        if response.candidates and response.candidates[0].content:
            model_content = response.candidates[0].content
            model_content.role = "model"
            history.append(model_content)
            for part in model_content.parts:
                if part.text:
                    print(f"\n{COLOR_YELLOW}{COLOR_BOLD}[Agent Thought]:{COLOR_RESET}\n{part.text.strip()}\n")
                    
        function_calls = response.function_calls
        
        # Check if there is at least one text part containing the agent's thought/explanation
        has_thought = any(part.text and part.text.strip() for part in model_content.parts) if (response.candidates and response.candidates[0].content) else False
        
        if function_calls and not has_thought:
            print(f"{COLOR_RED}{COLOR_BOLD}[Mantis Guardrail]:{COLOR_RESET}\n{COLOR_RED}Intercepted naked tool calls (missing explanation/thought). Prompting model to explain.{COLOR_RESET}")
            history.append(
                types.Content(
                    role="user",
                    parts=[types.Part.from_text(
                        text="System Warning: Missing Hypothesis. Before EVERY tool call, you MUST output a brief text explanation/scratchpad "
                             "explaining exactly what hypothesis you are investigating and why. You are forbidden from executing 'naked' tool calls. "
                             "Please re-issue your tool calls, ensuring you explain your logic first in text."
                    )]
                )
            )
            loop_count += 1
            continue
            
        if not function_calls:
            text_content = response.text or ""
            if required_headers:
                if any(hdr in text_content for hdr in required_headers):
                    break
                else:
                    print(f"{COLOR_RED}{COLOR_BOLD}[Mantis Guardrail]:{COLOR_RESET}\n{COLOR_RED}Intercepted incomplete response. Prompting model to continue.{COLOR_RESET}")
                    history.append(
                        types.Content(
                            role="user",
                            parts=[types.Part.from_text(
                                text=f"System Reminder: Your response is incomplete. You must output using the required markdown headers: {', '.join(required_headers)}."
                            )]
                        )
                    )
                    loop_count += 1
                    continue
            else:
                break
                
        tool_parts = []
        for fc in function_calls:
            tool_name = fc.name
            tool_args = fc.args
            
            # Generate unique tool invocation signature
            sig = f"{tool_name}:{json.dumps(tool_args, sort_keys=True)}"
            
            result = ""
            try:
                if sig in executed_tool_signatures:
                    result = (
                        f"Tool Error: Duplicate Request. You have already executed this exact tool call '{tool_name}' "
                        f"with arguments {tool_args} in a previous turn. Running it again will return the exact same results.\n"
                        f"If your search query returned truncated output (e.g. 10000 characters) or did not find your target string, "
                        f"it means your query pattern (like '{tool_args.get('pattern', '')}') is too common and matches too many lines.\n"
                        f"You MUST refine your query pattern to be highly specific (such as grepping for unique error message substrings, "
                        f"precise class names, or exact method signatures) to isolate the breakpoint. Do not repeat the same query."
                    )
                    print(f"{COLOR_RED}{COLOR_BOLD}[Mantis Guardrail]:{COLOR_RESET}\n{COLOR_RED}Intercepted and blocked duplicate tool call '{tool_name}' with arguments: {tool_args}{COLOR_RESET}")
                elif tool_name == "grep_codebase":
                    executed_tool_signatures.add(sig)
                    result = grep_codebase(**tool_args)
                elif tool_name == "read_file_lines":
                    executed_tool_signatures.add(sig)
                    result = read_file_lines(**tool_args)
                elif tool_name == "git_read_operations":
                    executed_tool_signatures.add(sig)
                    result = git_read_operations(**tool_args)
                else:
                    result = f"Error: Unknown tool '{tool_name}'"
            except Exception as e:
                result = f"Error executing tool {tool_name}: {e}"
                
            print(f" └─ Tool execution complete ({len(result)} characters returned)")
            tool_parts.append(
                types.Part.from_function_response(
                    name=tool_name,
                    response={"result": result}
                )
            )
            
        history.append(
            types.Content(
                role="user",
                parts=tool_parts
            )
        )
        loop_count += 1
        print("\n")
        time.sleep(0.5)
        
    if response and response.text:
        return response.text
    return ""

def run_timeline_generation(client, prompt_payload: str, catalog: str) -> str:
    """PHASE 1: Constructs only the objective chronological event timeline without speculation or assessments."""
    print(f"\n{COLOR_CYAN}{COLOR_BOLD}--- PHASE 1: Generating Chronological Event Timeline ---{COLOR_RESET}")
    
    system_instruction = (
        "You are Mantis Timeline Harvester, a highly precise log extraction agent "
        "designed to map chronological traces in the UDMI codebase.\n\n"
        "Your SOLE purpose is to construct a complete, exhaustive chronological timeline of events "
        "from the provided logs. You are strictly FORBIDDEN from analyzing, speculating, diagnosing, "
        "assessing component behavior, or proposing fixes. Your output must consist ONLY of the markdown header "
        "'## 1. Detailed Timeline of Events' followed by a clean, chronological markdown table.\n\n"
        "CRITICAL CONTEXT FILTER: You must focus EXCLUSIVELY on the logs of the active run under triage "
        "(provided in '## Local Sequencer log' and matching global logs). Do NOT confuse these timestamps/events "
        "with any successful reference runs.\n\n"
        "You MUST assemble the timeline strictly adhering to this sequence:\n"
        "1. First capture the timestamp when the test started (e.g., notice 'Starting test...').\n"
        "2. Iteratively for each action taken by the Sequencer:\n"
        "   - Capture the timestamp for the action taken by the Sequencer (e.g. sending a config with transaction ID RC:xxxxxx).\n"
        "   - Capture if the corresponding transaction reached UDMIS and the exact logs showing where UDMIS processed this transaction. If the transaction failed to reach UDMIS or was not processed, explicitly note that UDMIS processing is missing.\n"
        "   - Capture the reaction by Pubber/Device (e.g. config received, applying config, publishing updated state/system log).\n"
        "   - Identify if the response was successfully received by the Sequencer, or note any issues/timeouts seen during the synchronization wait loop.\n"
        "3. Lastly, capture the timestamp when the test stopped/failed (e.g. notice 'Ending test...' or timeout error).\n\n"
        "Format of Table:\n"
        "| Timestamp (UTC) | Source | Log Message / Event | Significance |\n"
        "| :--- | :--- | :--- | :--- |\n"
        "| [HH:MM:SS] | [Component] | `Log Message Snippet` | [Relevance/Significance explanation] |\n\n"
        "Ensure the timeline table is completely filled out using facts exclusively from the active log payload. "
        "Do not jump into early analysis."
    )
    
    # Strip Reference Successful Run Details to prevent context confusion in Phase 1
    clean_payload = prompt_payload
    if "## Reference Successful Run Details" in prompt_payload:
        clean_payload = prompt_payload.split("## Reference Successful Run Details")[0]
        
    # Combine metadata/logs with the skill catalogs
    full_payload = clean_payload + "\n\n## Skill Library Context\n" + catalog
    
    history = [
        types.Content(
            role="user",
            parts=[types.Part.from_text(text=full_payload)]
        )
    ]
    
    # Timeline Harvester only needs log/file reading tools to extract details
    tools_list = [read_file_lines]
    required_headers = ["## 1. Detailed Timeline of Events"]
    
    timeline_out = execute_agent_loop(client, system_instruction, history, tools_list, required_headers)
    return timeline_out

def run_intent_harvesting(client, test_id: str, prompt_payload: str) -> str:
    """PHASE 1.5: Harvesting Static Test Intent and Golden Baseline Expectations from Codebase and etc/ files."""
    print(f"\n{COLOR_GREEN}{COLOR_BOLD}--- PHASE 1.5: Harvesting Static Test Intent & Golden Baseline Expectations ---{COLOR_RESET}")
    
    system_instruction = (
        "You are Mantis Test Intent Harvester, a specialized UDMI Codebase Triage Agent.\n\n"
        "Your purpose is to search the codebase to locate the static test design intent, and also inspect the "
        "golden baseline results files in 'etc/' to determine the expected outcome of the test run under the current options.\n\n"
        "Specifically, perform these sequential harvesting steps:\n"
        "1. Search the codebase and read the Java sequence test method definition for '{test_id}' under 'validator/src/' "
        "to summarize its functional design intent (e.g. expected config transitions, telemetry, and timeouts).\n"
        "2. Look at the active run options in the provided '## Metadata Context' (such as options flags e.g., badLevel or badCategory).\n"
        "3. Use the 'read_file_lines' tool to open and read 'etc/sequencer.out' and 'etc/test_itemized.out'. Locate all lines matching "
        "the test case '{test_id}'. Parse the expected golden outcomes (e.g. passes with 'STABLE 8/10' or expected 'CPBLTY fail system broken_config.status').\n"
        "4. Document how the active option configuration affects the expected test outcome based on these golden reference files.\n\n"
        "Provide your output strictly as a clean, structured Markdown document with headings: "
        "'## 1. Test Code Reference', '## 2. Expected Sequence Flow', '## 3. Key Assertions & Timeouts', and '## 4. Golden Baseline Expectations'.\n"
        "Do NOT attempt to analyze the actual run logs. Focus ONLY on extracting the static intent and expected baseline behavior."
    )
    
    prompt = (
        f"## Active Run Context\n{prompt_payload}\n\n"
        f"Task: Locate and harvest the test case definition and golden baseline expectations for: '{test_id}'.\n"
        f"Search the codebase, read the Java sequence file, and read 'etc/sequencer.out' and 'etc/test_itemized.out' "
        f"to summarize the expected baseline outcomes under the active options."
    )
    
    history = [
        types.Content(
            role="user",
            parts=[types.Part.from_text(text=prompt)]
        )
    ]
    
    # Ingests codebase search and file reading tools (needed for etc/ files)
    tools_list = [grep_codebase, read_file_lines]
    required_headers = [
        "## 1. Test Code Reference", 
        "## 2. Expected Sequence Flow", 
        "## 3. Key Assertions & Timeouts", 
        "## 4. Golden Baseline Expectations"
    ]
    
    intent_out = execute_agent_loop(client, system_instruction, history, tools_list, required_headers)
    return intent_out

def run_component_behavior_mapping(client, timeline_content: str, intent_content: str) -> str:
    """PHASE 1.75: Maps chronological events to expected intent and grades components."""
    print(f"\n{COLOR_MAGENTA}{COLOR_BOLD}--- PHASE 1.75: Assessing Component Behavior ---{COLOR_RESET}")
    
    system_instruction = (
        "You are Mantis Component Assessor. Your job is to read the expected test intent "
        "and the actual chronological timeline, then provide a strict, step-by-step assessment "
        "of how each major system component behaved.\n\n"
        "You MUST NOT search the codebase. Use only the provided context.\n\n"
        "Output your assessment using this exact Markdown format:\n"
        "## Component Behavior Mapping\n"
        "### 1. Sequencer\n"
        "- **Expected**: [What it should have done]\n"
        "- **Actual**: [What it did]\n"
        "- **Assessment**: [Pass/Fail/Partial]\n\n"
        "### 2. UDMIS\n"
        "- **Expected**: [What it should have done]\n"
        "- **Actual**: [What it did]\n"
        "- **Assessment**: [Pass/Fail/Partial]\n\n"
        "### 3. Device\n"
        "- **Expected**: [What it should have done]\n"
        "- **Actual**: [What it did]\n"
        "- **Assessment**: [Pass/Fail/Partial]\n\n"
        "### 4. Breakpoint Identification\n"
        "- **Exact Failure Point**: [Identify the exact step where the actual behavior diverged from intent]"
    )
    
    history = [
        types.Content(
            role="user",
            parts=[types.Part.from_text(text=f"## Intent\n{intent_content}\n\n## Timeline\n{timeline_content}")]
        )
    ]
    
    # Do NOT give this step codebase search tools!
    required_headers = ["## Component Behavior Mapping", "### 4. Breakpoint Identification"]
    return execute_agent_loop(client, system_instruction, history, tools_list=[], required_headers=required_headers)


def run_defect_analysis(client, prompt_payload: str, catalog: str, timeline_content: str, intent_content: str, behavior_content: str) -> str:
    """PHASE 2: Performs root-cause differential analysis and codebase research on top of the pre-established timeline."""
    print(f"\n{COLOR_RED}{COLOR_BOLD}--- PHASE 2: Running Defect Root Cause Analysis & Codebase Triage ---{COLOR_RESET}")
    
    system_instruction = (
        "You are Mantis Defect Triage Analyst, a senior analytical AI debugger designed to "
        "perform root cause analysis of failed staging runs in the UDMI codebase.\n\n"
        "Your task is to read the provided logs, available code context, pre-established chronological events timeline, "
        "pre-established component behavior mapping, and expected test case design intent, perform differential analysis "
        "against the provided golden reference successful runs, search the codebase for logic bugs, and compile the "
        "final triage report.\n\n"
        "Guidelines:\n"
        "1. DO NOT re-generate, modify, or speculate on the timeline. You MUST insert the pre-established timeline "
        "content verbatim under Section 1 ('## 1. Detailed Timeline of Events').\n"
        "2. STRICT SCHEMA AND ENUM SEARCH BAN: You are FORBIDDEN from searching for or reading schema definitions (e.g., under 'schema/') or basic schema enum files (such as 'Category.java', 'Level.java', 'Entry.java', 'Category.json' etc.) in the codebase. Assume all schemas, enums, and standard types are completely correct. Do not query them.\n"
        "3. HIGH EFFICIENCY CODEBASE SEARCHES: Do NOT run duplicate, small, or repetitive tool calls. When you search the codebase, request a generous contiguous line block (e.g. 100-250 lines at once) using 'read_file_lines' to capture full methods and context in a single call.\n"
        "4. SINGLE-RUN TRIAGE RULE (BAN ON GIT SEARCH): If only a single run is under triage (i.e. '## Reference Successful Run Details' is marked as '[No successful reference runs found in sibling directories]'), you are strictly FORBIDDEN from calling 'git_read_operations'. Git regression or history analysis is completely meaningless without a successful baseline run to compare against. Focus your investigation entirely on the local sequence logs, UDMIS logs, and codebase grep instead.\n"
        "5. DIFFERENTIAL ANALYSIS (SCREEN OUT FALSE POSITIVES): You MUST do a side-by-side chronological comparison of all log messages, warnings, exceptions, and stack traces against the provided golden reference successful run details. If any log entry is present in BOTH the failed run and the successful reference run, it is a harmless trace event by design. You are strictly FORBIDDEN from attributing the failure to any log entry common to both runs! Pinpoint exactly where the failed run diverged from the successful run.\n"
        "6. PREFER LOCAL FILES: Always use the `read_file_lines` tool to read site model files directly from the local disk. Do NOT use `git_read_operations` to read these files unless you specifically need to view past committed history or diffs. When git search is banned by Rule 4, you are forbidden from calling 'git_read_operations' under any circumstances.\n"
        "7. PROPOSE RESOLUTIONS & CODE FIXES: You MUST explicitly identify the root cause bug or race condition and propose the concrete source code modifications (including file paths, approximate line ranges, and a standard unified diff or code block) needed to fix the bug in the Java emulators, sequence files, or processors.\n"
        "8. COMPARE FIRST, INVESTIGATE SECOND (CRITICAL REASONING FLOW):\n"
        "   - Before EVERY tool call, you MUST output a brief text explanation/scratchpad explaining exactly what hypothesis you are investigating and why. Never execute a tool call without explaining your logic first in text.\n"
        "   - You MUST begin your thinking loop by immediately reading and side-by-side comparing the actual event transitions in 'raw_timeline.md' against the static expected design intent in 'test_intent.md'.\n"
        "   - Identify the exact step or transition where the actual behavior diverged from expected behavior (rely heavily on the pre-established behavior mapping).\n"
        "   - ONLY AFTER you have logically identified this divergence point, may you call codebase tools (grep_codebase, read_file_lines) to investigate the specific class files, variables, or exception handlers associated with that failure breakpoint.\n"
        "   - You are strictly FORBIDDEN from executing random, exploratory, or un-targeted codebase searches before establishing this timeline mismatch.\n"
        "9. BEST-EFFORT TRIAGE AND HYPOTHESES: Follow the guardrails in 'best-effort-triage'. If the remaining logs, git history, and codebase logic are mathematically insufficient to definitively isolate the root cause, you MUST still output the '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' header under Proposed Code Fix (Section 4). However, you are REQUIRED to present the highly plausible defect hypotheses (specifically detailing asynchronous transport QOS packet drops, message delivery out-of-order races, or multi-threaded client deadlocks) and clearly state what missing log streams or broker telemetry would be required to definitively conclude or eliminate each possibility. You are FORBIDDEN from using this header in the timeline or assessment sections.\n"
        "10. INFRASTRUCTURE VS DEVICE FAILURES: If a test fails because middleware (like UDMIS) intercepts, drops, or crashes on a payload BEFORE it reaches the device, state this explicitly in the RCA. Do NOT spend multiple turns trying to rewrite core infrastructure serialization classes (like IotAccessBase.java) if the architecture simply prevents the test's intent from reaching the device.\n\n"
        "REQUIRED OUTPUT FORMAT:\n"
        "You MUST format your final response strictly using the following markdown headers, structures, and sections in this exact order:\n\n"
        "# UDMI Sequencer <Run Environment> Triage Analysis: <Device ID> <Test ID> Failure\n\n"
        "Provide a single-sentence introductory explanation of the document (e.g., 'This document presents a comprehensive root cause analysis of the <Test ID> test failure for device <Device ID> during <Run Environment> validation.'). Dynamically substitute the '<Run Environment>' placeholder using the actual 'Run Environment' value from your '## Metadata Context' (e.g., use 'Staging / Cloud Run' or 'Local / Mock Run').\n\n"
        "---\n\n"
        "## 1. Detailed Timeline of Events\n"
        "[INSERT THE PRE-ESTABLISHED TIMELINE CONTENT VERBATIM HERE]\n\n"
        "---\n\n"
        "## 2. Executive Defect Summary\n"
        "> [Provide a concise, single-line summary starting with a blockquote '>' identifying the primary component failure and exact error, e.g. 'Sequencer assertion timed out during config sync: last_start not synced in config' or 'EM-1 failed to satisfy pointset state update specifications'. This exact line is parsed by the triage consolidation engine.]\n\n"
        "[Provide a structured, detailed summary (3-4 bullets or paragraphs) detailing exactly how the Sequencer, UDMIS, and Device/Gateway interacted, using transaction IDs (RC:xxxxxx) and timestamps to pinpoint the defect.]\n\n"
        "---\n\n"
        "## 3. Component Assessment\n"
        "- **Did the device work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n"
        "- **Did sequencer work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n"
        "- **Did udmis work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n\n"
        "---\n\n"
        "## 4. Proposed Code Fix (or Technical Concurrency RCA)\n"
        "Identify the root cause bug or race condition and propose the concrete source code modifications (including exact file paths, approximate line ranges, and a standard unified diff or code block) needed to fix the bug in the Java emulators, sequence files, or processors.\n\n"
        "If the available logs, git history, and code logic are insufficient to isolate the breakpoint, you MUST output the header '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' under this section and list exactly what log streams/configurations are missing."
    )
    
    # Pass the timeline directly to the defect analysis payload as instructions
    analyst_payload = (
        f"{prompt_payload}\n\n"
        f"## Expected Test Case Design Intent Context\n"
        f"Refer to the static test intent harvested directly from the codebase below to understand what the test is designed to do:\n\n"
        f"{intent_content}\n\n"
        f"## Pre-Established Chronological Timeline of Events\n"
        f"Use the verbatim chronological timeline below for Section 1 of your report. "
        f"Do not modify it:\n\n"
        f"{timeline_content}\n\n"
        f"## Pre-Established Component Behavior Mapping\n"
        f"Refer to the component assessment and failure breakpoint below to guide your codebase research:\n\n"
        f"{behavior_content}\n\n"
        f"## Skill Library Context\n"
        f"{catalog}"
    )
    
    history = [
        types.Content(
            role="user",
            parts=[types.Part.from_text(text=analyst_payload)]
        )
    ]
    
    # Analyst needs codebase search & git tools for deep code diagnostics
    tools_list = [grep_codebase, read_file_lines, git_read_operations]
    required_headers = ["## 2. Executive Defect Summary", "## 3. Component Assessment", "## 4. Proposed Code Fix"]
    
    report_out = execute_agent_loop(client, system_instruction, history, tools_list, required_headers)
    return report_out

def run_triage_analysis(prompt_payload: str) -> str:
    """
    Instantiates a Google GenAI session, boots the skills registry,
    and runs the three-stage Triage pipeline:
    1. Stage 1: Timeline Harvesting -> raw_timeline.md
    2. Stage 2: Test Intent Harvesting -> test_intent.md
    3. Stage 3: Defect RCA & Codebase Triage -> triage_analysis.md
    """
    token = os.getenv("GEMINI_API_KEY")
    if not token:
        print("Error: The environment variable 'GEMINI_API_KEY' is not set.", file=sys.stderr)
        print("Please set your Gemini API Key before executing the triage agent.", file=sys.stderr)
        sys.exit(1)

    skills_dir = Path(os.path.dirname(os.path.abspath(__file__))) / "skills"
    try:
        catalog = asyncio.run(initialize_skills_registry(skills_dir))
    except Exception as e:
        print(f"Error initializing skill registry: {e}", file=sys.stderr)
        catalog = ""

    print("Initializing Gemini Triage Agent (Mantis Diagnose)...")
    try:
        client = genai.Client()
        
        # Resolve test name dynamically
        project_id, site_id, device_id, test_id = "unknown", "unknown", "unknown", "unknown"
        try:
            project_id_match = re.search(r'- \*\*Project ID\*\*: (.*)', prompt_payload)
            site_id_match = re.search(r'- \*\*Site ID\*\*: (.*)', prompt_payload)
            device_id_match = re.search(r'- \*\*Device ID\*\*: (.*)', prompt_payload)
            test_id_match = re.search(r'- \*\*Test ID\*\*: (.*)', prompt_payload)
            
            if project_id_match: project_id = project_id_match.group(1).strip()
            if site_id_match: site_id = site_id_match.group(1).strip()
            if device_id_match: device_id = device_id_match.group(1).strip()
            if test_id_match: test_id = test_id_match.group(1).strip()
        except Exception:
            pass
            
        mantis_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        nested_out_dir = os.path.join(mantis_dir, "out", "diagnose", project_id, site_id, device_id, test_id)
        os.makedirs(nested_out_dir, exist_ok=True)
        
        # 1. STAGE 1: Generate Timeline
        timeline_path = os.path.join(nested_out_dir, "raw_timeline.md")
        if not os.path.exists(timeline_path):
            timeline_content = run_timeline_generation(client, prompt_payload, catalog)
            with open(timeline_path, 'w', encoding='utf-8') as f:
                f.write(timeline_content)
            print(f"Raw chronological timeline successfully saved: {timeline_path}")
        else:
            print(f"\n{COLOR_CYAN}{COLOR_BOLD}--- PHASE 1: Loading Cached Chronological Event Timeline ---{COLOR_RESET}")
            print(f"Loading cached raw timeline: {timeline_path}")
            with open(timeline_path, 'r', encoding='utf-8') as f:
                timeline_content = f.read()
            print(timeline_content)
                
        # 2. STAGE 2: Test Intent Harvesting
        intent_path = os.path.join(nested_out_dir, "test_intent.md")
        if not os.path.exists(intent_path):
            intent_content = run_intent_harvesting(client, test_id, prompt_payload)
            with open(intent_path, 'w', encoding='utf-8') as f:
                f.write(intent_content)
            print(f"Static expected test intent successfully saved: {intent_path}")
        else:
            print(f"\n{COLOR_GREEN}{COLOR_BOLD}--- PHASE 1.5: Loading Cached Static Test Intent from Codebase ---{COLOR_RESET}")
            print(f"Loading cached test intent: {intent_path}")
            with open(intent_path, 'r', encoding='utf-8') as f:
                intent_content = f.read()
            print(intent_content)
            
        # 2.5. STAGE 2.5: Component Behavior Mapping (Phase 1.75)
        behavior_path = os.path.join(nested_out_dir, "behavior_mapping.md")
        if not os.path.exists(behavior_path):
            behavior_content = run_component_behavior_mapping(client, timeline_content, intent_content)
            with open(behavior_path, 'w', encoding='utf-8') as f:
                f.write(behavior_content)
            print(f"Component behavior mapping successfully saved: {behavior_path}")
        else:
            print(f"\n{COLOR_MAGENTA}{COLOR_BOLD}--- PHASE 1.75: Loading Cached Component Behavior Mapping ---{COLOR_RESET}")
            print(f"Loading cached behavior mapping: {behavior_path}")
            with open(behavior_path, 'r', encoding='utf-8') as f:
                behavior_content = f.read()
            print(behavior_content)

        # 3. STAGE 3: Defect Triage Root Cause Analysis
        final_report = run_defect_analysis(client, prompt_payload, catalog, timeline_content, intent_content, behavior_content)
        return final_report
        
    except Exception as e:
        print(f"Error communicating with Gemini API: {e}", file=sys.stderr)
        raise e
