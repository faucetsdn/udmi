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
            "You are Mantis Diagnose, a ruthless, highly analytical AI diagnostic triage agent "
            "designed to investigate failed test runs in the UDMI codebase. "
            "Your goal is to inspect execution traces, code context, and Git history to "
            "determine why a test failed and isolate the breakpoint with exact evidence.\n\n"
            "Guidelines:\n"
            "1. You will be provided with log segments and metadata detailing available sources.\n"
            "2. You must attempt to discover the sequence of events leading to the failure.\n"
            "3. Use your grep_codebase and read_file_lines tools to dynamically retrieve relevant codebase context (Java sequence files, UDMIS processors, Pubber emulators).\n"
            "4. Use git_read_operations on the site model repo (e.g., 'sites/udmi_site_model') to query git log / show to extract past successful run logs for the same test.\n"
            "5. Use git_read_operations on the main repo to identify code regressions between the last successful commit and the current failure.\n"
            "6. CRITICAL SUFFICIENCY RULE: If the available logs, git history, and code logic are insufficient to isolate the breakpoint, "
            "you MUST return a summary starting with the exact header '⚠️ INSUFFICIENT DATA TO TRACE ROOT CAUSE' in your Breakpoint Summary. "
            "Do not speculate or make up hypothetical failures. List exactly what logs are missing and recommend how the developer can enable them on the next run."
        )
        
        # 4. Configure content generations config
        config = types.GenerateContentConfig(
            tools=tools_list,
            temperature=0.1,  # Low temperature for deterministic, highly logical logs analysis
            system_instruction=system_instruction
        )
        
        print("Invoking Gemini model for diagnostics... (This may take a few moments to search codebase/git logs)")
        # 5. Models generation content (resolves dynamic tool calling automatically)
        response = client.models.generate_content(
            model="gemini-3.1-flash-lite",
            contents=prompt_payload,
            config=config
        )
        
        return response.text
    except Exception as e:
        print(f"Error communicating with Gemini API: {e}", file=sys.stderr)
        raise e
