# mantis.inspect package submodule - dynamic AI tools
import os
import subprocess
import sys

# Resolve root directory
MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

# Allowed read-only git subcommands
ALLOWED_GIT_COMMANDS = {"log", "show", "diff", "status", "branch"}

def grep_codebase(pattern: str) -> str:
    """
    Searches the UDMI codebase (validator/, udmis/, pubber/) for a specified string pattern.
    
    Args:
        pattern: The text pattern or regex to search for.
        
    Returns:
        A string containing matched lines with their file paths and line numbers.
    """
    print(f"[Inspect Tool] grep_codebase called with pattern: '{pattern}'")
    
    search_dirs = ["validator/src", "udmis/src", "pubber/src", "common/src", "gencode", "schema"]
    results = []
    
    for sdir in search_dirs:
        full_sdir = os.path.join(UDMI_ROOT, sdir)
        if not os.path.exists(full_sdir):
            continue
        try:
            # Use standard grep recursively with line numbers, ignoring binary files, out and build dirs
            cmd = [
                "grep", 
                "-rnI", 
                "--max-count=50", 
                "--exclude-dir=out", 
                "--exclude-dir=build", 
                pattern, 
                sdir
            ]
            out = subprocess.check_output(cmd, cwd=UDMI_ROOT, text=True)
            if out.strip():
                results.append(out.strip())
        except subprocess.CalledProcessError:
            # grep returns 1 if no matches are found, ignore
            continue
        except Exception as e:
            results.append(f"Error searching in {sdir}: {e}")
            
    if not results:
        return f"No matches found for pattern: '{pattern}' in codebase."
    return "\n".join(results)[:10000]  # Limit size to protect context window

def _read_single_file_helper(filepath: str, start_line: int, end_line: int) -> str:
    if not filepath:
        return "Error: Missing filepath parameter."
    # Protect against arbitrary system access (must be within UDMI_ROOT)
    full_path = os.path.abspath(os.path.join(UDMI_ROOT, filepath))
    if not full_path.startswith(UDMI_ROOT):
        return "Error: Permission denied. Cannot read files outside the workspace root."
        
    if not os.path.exists(full_path):
        return f"Error: File not found at '{filepath}'."
        
    if start_line < 1:
        start_line = 1
    if end_line < start_line:
        return "Error: end_line must be greater than or equal to start_line."
        
    # Enforce maximum chunk sizes to prevent context overload
    max_lines = 300
    if (end_line - start_line + 1) > max_lines:
        end_line = start_line + max_lines - 1
        print(f"[Inspect Tool] Warning: Truncating read request to max limit of {max_lines} lines.")

    try:
        lines = []
        with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
            for idx, line in enumerate(f, start=1):
                if start_line <= idx <= end_line:
                    lines.append(f"{idx}: {line.rstrip()}")
                elif idx > end_line:
                    break
        return "\n".join(lines)
    except Exception as e:
        return f"Error reading file '{filepath}': {e}"

def read_file_lines(filepath: str = None, start_line: int = 1, end_line: int = 100, files_to_read: list[dict] = None) -> str:
    """
    Reads specific line ranges from one or more files in the workspace.
    You can read a single file using 'filepath', 'start_line', and 'end_line', OR
    you can read multiple files in a single call by passing a list of dicts to 'files_to_read'.
    
    Args:
        filepath: Contiguous read path relative to repo root (e.g., 'validator/src/main/...').
        start_line: The 1-indexed starting line number (inclusive).
        end_line: The 1-indexed ending line number (inclusive).
        files_to_read: Optional list of dicts, each containing: 'filepath' (str),
                       'start_line' (int), and 'end_line' (int) to perform batch reads in a single turn.
                       
    Returns:
        A combined text string representing all requested file lines.
    """
    if files_to_read:
        print(f"[Inspect Tool] read_file_lines batch called for {len(files_to_read)} files.")
        outputs = []
        for idx, item in enumerate(files_to_read, start=1):
            f_path = item.get("filepath")
            s_line = int(item.get("start_line", 1))
            e_line = int(item.get("end_line", 100))
            
            content = _read_single_file_helper(f_path, s_line, e_line)
            outputs.append(f"--- File {idx}: '{f_path}' (Lines {s_line}-{e_line}) ---\n{content}\n")
        return "\n".join(outputs)[:25000]  # Protect context window size limit
        
    if filepath:
        print(f"[Inspect Tool] read_file_lines called for '{filepath}' (lines {start_line}-{end_line})")
        return _read_single_file_helper(filepath, start_line, end_line)
        
    return "Error: No filepath or files_to_read parameter was supplied."

def git_read_operations(repo_path: str, command: str, args: list[str] = None) -> str:
    """
    Runs a safe, read-only git command inside the main repo or site model repo.
    
    Args:
        repo_path: Directory path relative to the repository root (e.g., '.', or 'sites/udmi_site_model').
        command: The git subcommand (e.g. 'log', 'diff', 'show', 'status').
        args: A list of additional command-line arguments (e.g., ['-n', '5', '--', 'file.log']).
        
    Returns:
        A string containing the standard output or error of the git command execution.
    """
    print(f"[Inspect Tool] git_read_operations called inside '{repo_path}': git {command} {args or []}")
    
    # Resolve target repo path
    full_repo_path = os.path.abspath(os.path.join(UDMI_ROOT, repo_path))
    if not full_repo_path.startswith(UDMI_ROOT):
        return "Error: Permission denied. Target repository path is outside the workspace root."
        
    if not os.path.exists(full_repo_path):
        return f"Error: Target path '{repo_path}' does not exist."

    # 1. Enforce Security Guardrails (Strict read-only verification)
    if command not in ALLOWED_GIT_COMMANDS:
        return f"Security Error: Git command '{command}' is rejected. Only safe read-only operations are permitted: {', '.join(ALLOWED_GIT_COMMANDS)}."

    # 2. Compile safe execution arguments
    cmd_args = ["git", command]
    if args:
        # Strip any shell expansion characters or path attempts
        cleaned_args = []
        for arg in args:
            if ';' in arg or '&&' in arg or '|' in arg or '`' in arg:
                return "Security Error: Detected dangerous shell characters in arguments."
            cleaned_args.append(arg)
        cmd_args.extend(cleaned_args)

    try:
        # Run command with PAGER=cat to prevent blocking/paging issues
        env = os.environ.copy()
        env["PAGER"] = "cat"
        
        out = subprocess.check_output(cmd_args, cwd=full_repo_path, env=env, text=True, stderr=subprocess.STDOUT)
        return out.strip()[:12000]  # Protect context window size
    except subprocess.CalledProcessError as e:
        return f"Git Command Failed (code {e.returncode}):\n{e.output}"
    except Exception as e:
        return f"Error executing git command: {e}"
