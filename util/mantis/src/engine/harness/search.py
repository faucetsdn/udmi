import os
import shutil
import subprocess
from abc import ABC, abstractmethod
from typing import List

class CodeSearchProvider(ABC):
    """Abstract interface to decouple codebase search indexing from local disk walks."""
    
    @abstractmethod
    def grep_codebase(
        self,
        workspace_root: str,
        pattern: str,
        search_dirs: List[str],
        exclude_dirs: List[str],
        exclude_files: List[str],
        include_files: List[str]
    ) -> str:
        """Searches the repository index for the given string or regex pattern."""
        pass


class LocalCodeSearchProvider(CodeSearchProvider):
    """Default local codebase search provider executing local git, ripgrep, or grep processes."""

    def grep_codebase(
        self,
        workspace_root: str,
        pattern: str,
        search_dirs: List[str],
        exclude_dirs: List[str],
        exclude_files: List[str],
        include_files: List[str]
    ) -> str:
        results = []
        for sdir in search_dirs:
            full_sdir = os.path.join(workspace_root, sdir)
            if not os.path.exists(full_sdir):
                continue

            # 1. Try git grep
            try:
                cmd = ["git", "grep", "-nIE", pattern, "--", sdir]
                out = subprocess.check_output(cmd, cwd=workspace_root, text=True)
                if out.strip():
                    results.append(out.strip())
                continue
            except subprocess.CalledProcessError as e:
                if e.returncode == 1:
                    continue
            except Exception:
                pass

            # 2. Try ripgrep (rg)
            if shutil.which("rg"):
                try:
                    cmd = ["rg", "--line-number", "--no-heading", "--color=never", "-e", pattern]
                    default_excludes = {"out", "build", "node_modules", ".git", "venv", "bin", "__pycache__", ".idea"}
                    for edir in default_excludes.union(exclude_dirs):
                        cmd.extend(["--glob", f"!{edir}/*"])
                    for efile in exclude_files:
                        cmd.extend(["--glob", f"!{efile}"])
                    for ifile in include_files:
                        cmd.extend(["--glob", ifile])
                    
                    cmd.extend([sdir])
                    out = subprocess.check_output(cmd, cwd=workspace_root, text=True)
                    if out.strip():
                        results.append(out.strip())
                    continue
                except subprocess.CalledProcessError as e:
                    if e.returncode == 1:
                        continue
                except Exception:
                    pass

            # 3. Fallback to standard grep
            try:
                cmd = ["grep", "-rnIE"]
                default_excludes = {"out", "build", "node_modules", ".git", "venv", "bin", "__pycache__", ".idea"}
                for edir in default_excludes.union(exclude_dirs):
                    cmd.append(f"--exclude-dir={edir}")
                for efile in exclude_files:
                    cmd.append(f"--exclude={efile}")
                for ifile in include_files:
                    cmd.append(f"--include={ifile}")
                
                cmd.extend([pattern, sdir])
                out = subprocess.check_output(cmd, cwd=workspace_root, text=True)
                if out.strip():
                    results.append(out.strip())
            except subprocess.CalledProcessError:
                continue
            except Exception as e:
                results.append(f"Error searching in {sdir}: {e}")

        if not results:
            return f"No matches found for pattern: '{pattern}' in configured codebase search directories."

        combined_results = "\n".join(results)
        if len(combined_results) > 30000:
            return combined_results[:30000] + "\n\n... [RESULTS TRUNCATED. PLEASE REFINE GREP PATTERN] ..."
        return combined_results
