import os
import re
import subprocess
from datetime import datetime
from datetime import timedelta
from typing import Any
from typing import Dict
from typing import List
from typing import Optional
from .ui import color_text, BLUE
from .harness.search import CodeSearchProvider, LocalCodeSearchProvider

ALLOWED_GIT_COMMANDS = {"log", "show", "diff", "status", "branch", "blame"}


class ToolBelt:
    """
    A generic suite of repository exploration, git auditing, and log timeline analysis tools.
    Designed to be instantiated with a specific workspace root, making it immediately
    reusable by any project.
    """

    def __init__(self, workspace_root: str, search_dirs: List[str] = None, exclude_dirs: List[str] = None, exclude_files: List[str] = None, include_files: List[str] = None, search_provider: Optional[CodeSearchProvider] = None):
        self.workspace_root = os.path.abspath(workspace_root)
        self.search_dirs = search_dirs or ["."]
        self.exclude_dirs = exclude_dirs or []
        self.exclude_files = exclude_files or ["*.log", "*.md", "*.json"]
        self.include_files = include_files or []
        self.search_provider = search_provider or LocalCodeSearchProvider()
        self._directory_cache = {}
        self._build_directory_cache()

    def _build_directory_cache(self):
        """Recursively builds a lightweight cache of directories and their immediate contents, ignoring build artifacts."""
        default_excludes = {
            "out", "build", "node_modules", ".git", "venv", "bin", "__pycache__", ".idea"
        }
        excluded_dirs = default_excludes.union(self.exclude_dirs)
        for root, dirs, files in os.walk(self.workspace_root):
            dirs[:] = [
                d for d in dirs 
                if d not in excluded_dirs and not d.startswith("udmi-support_") and not d.startswith("out_")
            ]

            rel_path = os.path.relpath(root, self.workspace_root)
            if rel_path == ".":
                rel_path = ""

            cached_items = []
            for d in sorted(dirs):
                cached_items.append((d, True))  # (name, is_dir)
            for f in sorted(files):
                cached_items.append((f, False))  # (name, is_dir)

            normalized_key = rel_path.replace(os.path.sep, "/")
            self._directory_cache[normalized_key] = cached_items

    def get_tools_map(self) -> Dict[str, Any]:
        """Returns a dictionary mapping tool name to its bound method."""
        return {
            "list_directory": self.list_directory,
            "grep_codebase": self.grep_codebase,
            "read_file_lines": self.read_file_lines,
            "git_read_operations": self.git_read_operations,
            "grep_file": self.grep_file,
            "expand_log_window": self.expand_log_window,
            "read_method_definition": self.read_method_definition,
            "lookup_symbol": self.lookup_symbol
        }

    def _log_tool_call(self, msg: str):
        print(color_text("[Inspect Tool]:", BLUE, bold=True) + "\n" +
              color_text(msg, BLUE) + "\n")

    def list_directory(self, directory_path: str = ".") -> str:
        """
        Lists the contents of a specified directory within the workspace.
        Uses the ultra-fast in-memory directory tree cache, falling back to filesystem lookup if not found.
        """
        self._log_tool_call(f"list_directory called for path: '{directory_path}'")

        # Normalize directory path to lookup in cache
        norm_path = directory_path.strip("/").replace(os.path.sep, "/")
        if norm_path == ".":
            norm_path = ""

        # 1. Try in-memory cache lookup
        if norm_path in self._directory_cache:
            items = self._directory_cache[norm_path]
            output = [f"Contents of /{directory_path}:"]
            for name, is_dir in items:
                if is_dir:
                    output.append(f"  [DIR]  {name}/")
                else:
                    output.append(f"  [FILE] {name}")
            return "\n".join(output)[:10000]

        # 2. Fallback to live filesystem lookup
        full_path = os.path.abspath(
            os.path.join(self.workspace_root, directory_path))
        if not full_path.startswith(self.workspace_root):
            return "Error: Permission denied. Cannot list directories outside the workspace root."

        if not os.path.exists(full_path):
            return f"Error: Directory not found at '{directory_path}'."

        if not os.path.isdir(full_path):
            return f"Error: '{directory_path}' is a file, not a directory."

        try:
            items = sorted(os.listdir(full_path))
            output = [f"Contents of /{directory_path}:"]
            for item in items:
                item_path = os.path.join(full_path, item)
                if os.path.isdir(item_path):
                    output.append(f"  [DIR]  {item}/")
                else:
                    output.append(f"  [FILE] {item}")
            return "\n".join(output)[:10000]
        except Exception as e:
            return f"Error listing directory '{directory_path}': {e}"

    def grep_codebase(self, pattern: str) -> str:
        """
        Searches the codebase for a specified string pattern or regex across configured search directories.
        Delegates search execution to the registered CodeSearchProvider.
        """
        self._log_tool_call(f"grep_codebase called with pattern: '{pattern}'")

        return self.search_provider.grep_codebase(
            workspace_root=self.workspace_root,
            pattern=pattern,
            search_dirs=self.search_dirs,
            exclude_dirs=self.exclude_dirs,
            exclude_files=self.exclude_files,
            include_files=self.include_files
        )

    def _read_single_file_helper(self, filepath: str, start_line: int,
        end_line: int) -> str:
        if not filepath:
            return "Error: Missing filepath parameter."

        full_path = os.path.abspath(os.path.join(self.workspace_root, filepath))
        if not full_path.startswith(self.workspace_root):
            return "Error: Permission denied. Cannot read files outside the workspace root."

        if not os.path.exists(full_path):
            return f"Error: File not found at '{filepath}'."

        if start_line < 1:
            start_line = 1
        if end_line < start_line:
            return "Error: end_line must be greater than or equal to start_line."

        max_lines = 2000
        if (end_line - start_line + 1) > max_lines:
            end_line = start_line + max_lines - 1
            self._log_tool_call(f"Warning: Truncating read request to max limit of {max_lines} lines.")

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

    def read_file_lines(
        self,
        filepath: str = None,
        start_line: int = 1,
        end_line: int = 200,
        files_to_read: List[Dict[str, Any]] = None
    ) -> str:
        """
        Reads specific line ranges from one or more files in the workspace.
        You can read a single file using 'filepath', 'start_line', and 'end_line', OR
        you can read multiple files in a single call by passing a list of dicts to 'files_to_read'.
        """
        if files_to_read:
            self._log_tool_call(f"read_file_lines batch called for {len(files_to_read)} files.")
            outputs = []
            for idx, item in enumerate(files_to_read, start=1):
                f_path = item.get("filepath")
                s_line = int(item.get("start_line", 1))
                e_line = int(item.get("end_line", 200))

                content = self._read_single_file_helper(f_path, s_line, e_line)
                outputs.append(
                    f"--- File {idx}: '{f_path}' (Lines {s_line}-{e_line}) ---\n{content}\n")
            return "\n".join(outputs)[:80000]

        if filepath:
            self._log_tool_call(f"read_file_lines called for '{filepath}' (lines {start_line}-{end_line})")
            return self._read_single_file_helper(filepath, start_line, end_line)

        return "Error: No filepath or files_to_read parameter was supplied."

    def git_read_operations(self, repo_path: str, command: str,
        args: List[str] = None) -> str:
        """
        Runs a safe, read-only git command inside the main repo or site model repo.
        """
        self._log_tool_call(f"git_read_operations called inside '{repo_path}': git {command} {args or []}")

        full_repo_path = os.path.abspath(
            os.path.join(self.workspace_root, repo_path))
        if not full_repo_path.startswith(self.workspace_root):
            return "Error: Permission denied. Target repository path is outside the workspace root."

        if not os.path.exists(full_repo_path):
            return f"Error: Target path '{repo_path}' does not exist."

        if command not in ALLOWED_GIT_COMMANDS:
            return f"Security Error: Git command '{command}' is rejected. Only safe read-only operations are permitted: {', '.join(ALLOWED_GIT_COMMANDS)}."

        cmd_args = ["git", command]
        if args:
            cleaned_args = []
            for arg in args:
                if ';' in arg or '&&' in arg or '|' in arg or '`' in arg:
                    return "Security Error: Detected dangerous shell characters in arguments."
                cleaned_args.append(arg)
            cmd_args.extend(cleaned_args)

        try:
            env = os.environ.copy()
            env["PAGER"] = "cat"

            out = subprocess.check_output(cmd_args, cwd=full_repo_path, env=env,
                                          text=True, stderr=subprocess.STDOUT)
            return out.strip()[:30000]
        except subprocess.CalledProcessError as e:
            return f"Git Command Failed (code {e.returncode}):\n{e.output}"
        except Exception as e:
            return f"Error executing git command: {e}"

    def grep_file(self, pattern: str, filepath: str) -> str:
        """
        Searches for a specified pattern or transaction ID in a specific file under the workspace.
        """
        self._log_tool_call(f"grep_file called with pattern: '{pattern}' in file: '{filepath}'")

        if not filepath:
            return "Error: Missing filepath parameter."

        full_path = os.path.abspath(os.path.join(self.workspace_root, filepath))
        if not full_path.startswith(self.workspace_root):
            return "Error: Permission denied. Cannot read files outside the workspace root."

        if not os.path.exists(full_path):
            return f"Error: File not found at '{filepath}'."

        try:
            cmd = [
                "grep",
                "-nI",
                pattern,
                full_path
            ]
            out = subprocess.check_output(cmd, text=True)
            if not out.strip():
                return f"No matches found for pattern: '{pattern}' in '{filepath}'."

            return out.strip()[:40000]
        except subprocess.CalledProcessError:
            return f"No matches found for pattern: '{pattern}' in '{filepath}'."
        except Exception as e:
            return f"Error searching in {filepath}: {e}"

    def expand_log_window(self, filepath: str, center_timestamp: str,
        window_seconds: int = 30) -> str:
        """
        Extracts a precise time window of raw logs from a specified file.
        Use this when you suspect a temporal anomaly occurred around a specific time.
        """
        self._log_tool_call(f"expand_log_window called for '{filepath}' at {center_timestamp} (+/- {window_seconds}s)")

        full_path = os.path.abspath(os.path.join(self.workspace_root, filepath))
        if not full_path.startswith(self.workspace_root):
            return "Error: Permission denied. Cannot read files outside the workspace root."

        if not os.path.exists(full_path):
            return f"Error: File not found at '{filepath}'."

        def parse_ts(ts_str):
            ts_str = ts_str.strip("[] ")
            formats = ["%Y-%m-%dT%H:%M:%S.%fZ", "%Y-%m-%dT%H:%M:%SZ",
                       "%H:%M:%S.%f", "%H:%M:%S"]
            for fmt in formats:
                try:
                    if "Y" not in fmt:
                        t = datetime.strptime(ts_str, fmt).time()
                        return datetime.combine(datetime.min.date(), t)
                    return datetime.strptime(ts_str, fmt)
                except ValueError:
                    continue
            return None

        target_dt = parse_ts(center_timestamp)
        if not target_dt:
            return f"Error: Could not parse timestamp '{center_timestamp}'. Please use ISO format."

        start_bound = target_dt - timedelta(seconds=window_seconds)
        end_bound = target_dt + timedelta(seconds=window_seconds)

        ts_pattern = re.compile(r'^([\d\-T:Z\.,]+)\s+')
        results = []

        try:
            with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
                for line in f:
                    m = ts_pattern.match(line)
                    if m:
                        line_dt = parse_ts(m.group(1))
                        if line_dt and (start_bound <= line_dt <= end_bound):
                            results.append(line.strip())

            if not results:
                return f"No logs found in '{filepath}' between {start_bound.strftime('%H:%M:%S')} and {end_bound.strftime('%H:%M:%S')}."

            return "\n".join(results)[:40000]
        except Exception as e:
            return f"Error reading log window: {e}"

    def read_method_definition(self, filepath: str, method_name: str) -> str:
        """
        Extracts the entire method definition for a specified method name from a Java or Python file.
        Eliminates sequential reading of method contents, returning the complete block in a single call.
        """
        self._log_tool_call(f"read_method_definition called for '{method_name}' in '{filepath}'")

        if not filepath or not method_name:
            return "Error: Missing filepath or method_name parameter."

        full_path = os.path.abspath(os.path.join(self.workspace_root, filepath))
        if not full_path.startswith(self.workspace_root):
            return "Error: Permission denied. Cannot read files outside the workspace root."

        if not os.path.exists(full_path):
            return f"Error: File not found at '{filepath}'."

        try:
            with open(full_path, 'r', encoding='utf-8', errors='replace') as f:
                content = f.read()

            lines = content.splitlines()
            is_java = filepath.endswith(".java")

            if is_java:
                # Scan for Java method pattern (balanced braces)
                pattern = re.compile(r'\b' + re.escape(method_name) + r'\s*\([^)]*\)\s*(?:throws\s+\w+(?:\s*,\s*\w+)*)?\s*\{')
                match = pattern.search(content)
                if not match:
                    # Try fallback pattern with multi-line params
                    pattern_fallback = re.compile(r'\b' + re.escape(method_name) + r'\s*\([\s\S]*?\)\s*(?:throws\s+[\s\S]+?)?\s*\{')
                    match = pattern_fallback.search(content)

                if not match:
                    return f"Error: Could not find Java method definition for '{method_name}' in '{filepath}'."

                start_idx = match.start()
                start_line = content[:start_idx].count('\n') + 1
                
                # Balance braces to find the end of the method
                brace_count = 0
                end_idx = -1
                for i in range(start_idx, len(content)):
                    char = content[i]
                    if char == '{':
                        brace_count += 1
                    elif char == '}':
                        brace_count -= 1
                        if brace_count == 0:
                            end_idx = i + 1
                            break

                if end_idx == -1:
                    end_line = min(start_line + 100, len(lines))
                else:
                    end_line = content[:end_idx].count('\n') + 1

            else:
                # Python method extraction (indentation-based)
                pattern = re.compile(r'^\s*def\s+' + re.escape(method_name) + r'\b')
                start_line = -1
                for idx, line in enumerate(lines, start=1):
                    if pattern.match(line):
                        start_line = idx
                        break

                if start_line == -1:
                    return f"Error: Could not find Python def block for '{method_name}' in '{filepath}'."

                start_indent = len(lines[start_line-1]) - len(lines[start_line-1].lstrip())
                end_line = start_line
                for idx in range(start_line, len(lines)):
                    line = lines[idx]
                    if not line.strip():
                        continue
                    indent = len(line) - len(line.lstrip())
                    if indent <= start_indent:
                        break
                    end_line = idx + 1

            # Extract target lines
            extracted_lines = []
            for idx in range(start_line, end_line + 1):
                extracted_lines.append(f"{idx}: {lines[idx-1]}")

            return "\n".join(extracted_lines)[:40000]

        except Exception as e:
            return f"Error extracting method '{method_name}' from '{filepath}': {e}"

    def lookup_symbol(self, symbol_name: str) -> str:
        """
        Locates the exact file and line number where a class, interface, method, or function is declared.
        Use this to instantly jump to a definition rather than searching all text references.
        
        Args:
            symbol_name: The exact name of the class, interface, method, or function to find (e.g. 'SequenceBase' or 'waitUntil').
        """
        self._log_tool_call(f"lookup_symbol called for symbol: '{symbol_name}'")

        # Build declaration patterns:
        # - Java/Python classes/interfaces/enums: 'class SymbolName', 'interface SymbolName', etc.
        # - Java/Python methods/functions: def symbol_name, void symbol_name, type symbol_name, etc.
        pattern = rf'\b(class|interface|enum|def)\s+{re.escape(symbol_name)}\b|\b\w+\s+{re.escape(symbol_name)}\s*\('

        res = self.search_provider.grep_codebase(
            workspace_root=self.workspace_root,
            pattern=pattern,
            search_dirs=self.search_dirs,
            exclude_dirs=self.exclude_dirs,
            exclude_files=self.exclude_files,
            include_files=self.include_files
        )

        if "No matches found for pattern" in res:
            return f"Could not find any class, method, or function declaration for symbol: '{symbol_name}'."

        return res
