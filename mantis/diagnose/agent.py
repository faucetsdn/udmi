# mantis.inspect package submodule - AI Agent Harness
import os
import sys
import asyncio
from pathlib import Path
from google import genai
from google.genai import types
from agentskills_core import SkillRegistry
from agentskills_fs import LocalFileSystemSkillProvider
from .tools import grep_codebase, read_file_lines, git_read_operations

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

    # Resolve and load formal Agent Skills SDK catalog
    skills_dir = Path(os.path.dirname(os.path.abspath(__file__))) / "skills"
    try:
        catalog = asyncio.run(initialize_skills_registry(skills_dir))
    except Exception as e:
        print(f"Error initializing skill registry: {e}", file=sys.stderr)
        catalog = ""

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
            "MANDATORY FIRST STEP - CONSULT YOUR SKILL LIBRARY:\n"
            "Before doing any codebase research or log analysis, you MUST read the detailed instructions "
            "for the relevant skills from your Skill Library using the 'read_file_lines' tool. "
            "The skills in your registered Skill Library are:\n"
            f"{catalog}\n\n"
            "Guidelines:\n"
            "1. You MUST follow the sequential investigation steps in 'mantis/diagnose/skills/progressive-triage-flow/SKILL.md' (Intent ➔ Active Run Correlation ➔ Sibling Comparison ➔ Git Regression ➔ Code Fix).\n"
            "2. Always use the guidelines in 'mantis/diagnose/skills/log-sources/SKILL.md' and 'mantis/diagnose/skills/log-correlation/SKILL.md' to reconstruct the timeline and trace asynchronous transactions across component boundaries.\n"
            "3. DO NOT use git_read_operations to search for past successful runs in the site model repo. Sibling logs of a successful golden reference run are already loaded and provided directly in the prompt payload under '## Reference Successful Run Details'. Focus entirely on comparing the failed and successful log traces side-by-side to locate differences.\n"
            "4. Run codebase searches using your grep_codebase tool and read file details with read_file_lines, following the investigation practices in 'mantis/diagnose/skills/evidence-gathering/SKILL.md'.\n"
            "5. Use git_read_operations on the main repo ONLY if you suspect a recent codebase commit introduced a regression, to query recent logs/diffs.\n"
            "6. DIFFERENTIAL ANALYSIS: You MUST compare the failed log traces side-by-side to the successful reference log traces to locate differences and pinpoint exactly where they diverged.\n"
            "7. PREFER LOCAL FILES: Always use the `read_file_lines` tool to read site model files directly from the local disk. Do NOT use `git_read_operations` to read these files unless you specifically need to view past committed history or diffs.\n"
            "8. CRITICAL SUFFICIENCY RULE: Follow the guardrails in 'mantis/diagnose/skills/best-effort-triage/SKILL.md'. If the available logs, git history, and code logic are insufficient to isolate the breakpoint, you MUST return a summary starting with the exact header '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' in your Breakpoint Summary blockquote and Proposed Code Fix section. Do not speculate or make up hypothetical failures.\n"
            "9. PROPOSE RESOLUTIONS & CODE FIXES: You MUST explicitly identify the root cause bug or race condition and propose the concrete source code modifications (including file paths, approximate line ranges, and a standard unified diff or code block) needed to fix the bug in the Java emulators, sequence files, or processors.\n\n"
            "REQUIRED OUTPUT FORMAT:\n"
            "You MUST format your final response strictly using the following markdown headers, structures, and sections:\n\n"
            "# UDMI Sequencer Staging Run Triage Analysis: <Device ID> <Test ID> Failure\n\n"
            "Provide a single-sentence introductory explanation of the document (e.g., 'This document presents a comprehensive root cause analysis of the <Test ID> test failure for device <Device ID> during...').\n\n"
            "---\n\n"
            "## 1. Executive Defect Summary\n"
            "> [Provide a concise, single-line summary starting with a blockquote '>' identifying the primary component failure and exact error, e.g. 'Sequencer assertion timed out during config sync: last_start not synced in config' or 'EM-1 failed to satisfy pointset state update specifications'. This exact line is parsed by the triage consolidation engine.]\n\n"
            "[Provide a structured, detailed summary (3-4 bullets or paragraphs) detailing exactly how the Sequencer, UDMIS, and Device/Gateway interacted, using transaction IDs (RC:xxxxxx) and timestamps to pinpoint the defect.]\n\n"
            "---\n\n"
            "## 2. Component Assessment\n"
            "- **Did the device work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n"
            "- **Did sequencer work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n"
            "- **Did udmis work as expected?** [Yes/No/Partial + concise 1-2 sentence justification based on log evidence]\n\n"
            "---\n\n"
            "## 3. Detailed Timeline of Events\n"
            "Construct a clean chronological markdown table aligning all correlated logs (Sequencer, UDMIS, Gateway, Pubber/Device) to show precisely when and why the runs diverged. Format:\n\n"
            "| Timestamp (UTC) | Source | Log Message / Event | Significance |\n"
            "| :--- | :--- | :--- | :--- |\n"
            "| [HH:MM:SS] | [Component] | `Log Message Snippet` | [Relevance/Significance explanation] |\n\n"
            "---\n\n"
            "## 4. Proposed Code Fix (or Technical Concurrency RCA)\n"
            "Identify the root cause bug or race condition and propose the concrete source code modifications (including exact file paths, approximate line ranges, and a standard unified diff or code block) needed to fix the bug in the Java emulators, sequence files, or processors.\n\n"
            "If the available logs, git history, and code logic are insufficient to isolate the breakpoint, you MUST output the header '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' under this section and list exactly what log streams/configurations are missing."
        )
        
        # 4. Configure content generations config (without automaticFunctionCalling)
        config = types.GenerateContentConfig(
            tools=tools_list,
            temperature=0.1,  # Low temperature for deterministic, highly logical logs analysis
            system_instruction=system_instruction,
        )
        
        print("Invoking Gemini model for diagnostics... (Live agent decisions will display below)")
        
        # 5. Manage conversation history manually for absolute turn interception control
        history = [
            types.Content(
                role="user",
                parts=[types.Part.from_text(text=prompt_payload)]
            )
        ]
        
        loop_count = 0
        max_loops = 50
        
        while loop_count < max_loops:
            # Generate content with current history list
            response = client.models.generate_content(
                model="gemini-3.1-pro-preview",
                contents=history,
                config=config
            )
            
            # Record model content response to history
            if response.candidates and response.candidates[0].content:
                model_content = response.candidates[0].content
                model_content.role = "model"
                history.append(model_content)
                
            # Display any live intermediate reasoning generated by the agent in this turn
            if response.text:
                print(f"\n🧠 [Agent Thought]:\n{response.text}\n")
                
            # Check if the model requested function calls
            function_calls = response.function_calls
            if not function_calls:
                break
                
            tool_parts = []
            for fc in function_calls:
                tool_name = fc.name
                tool_args = fc.args
                
                # Print the decision to the terminal logs live!
                print(f"🤖 [Agent Decision]: Calling tool '{tool_name}' with arguments: {tool_args}")
                
                # Execute the tool
                result = ""
                try:
                    if tool_name == "grep_codebase":
                        result = grep_codebase(pattern=tool_args.get("pattern"))
                    elif tool_name == "read_file_lines":
                        result = read_file_lines(
                            filepath=tool_args.get("filepath"),
                            start_line=int(tool_args.get("start_line")),
                            end_line=int(tool_args.get("end_line"))
                        )
                    elif tool_name == "git_read_operations":
                        result = git_read_operations(
                            repo_path=tool_args.get("repo_path"),
                            command=tool_args.get("command"),
                            args=tool_args.get("args")
                        )
                    else:
                        result = f"Error: Unknown tool '{tool_name}'"
                except Exception as e:
                    result = f"Error executing tool {tool_name}: {e}"
                    
                print(f"   └─ Tool execution complete ({len(result)} characters returned)")
                
                # Format part response
                tool_parts.append(
                    types.Part.from_function_response(
                        name=tool_name,
                        response={"result": result}
                    )
                )
                
            # Add tool responses as the next user turn in history to continue the chain
            history.append(
                types.Content(
                    role="user",
                    parts=tool_parts
                )
            )
            loop_count += 1
            
        # Check if final response has text
        if response.text:
            return response.text
            
        # Fallback if response.text is None
        fallback_text = (
            "⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE\n\n"
            "The Gemini diagnostic agent completed its tool execution but did not return a final text analysis.\n"
            "This typically indicates the model reached the maximum tool-calling loop limit (50 turns) while recursively searching codebase or git logs."
        )
        return fallback_text
    except Exception as e:
        print(f"Error communicating with Gemini API: {e}", file=sys.stderr)
        raise e
