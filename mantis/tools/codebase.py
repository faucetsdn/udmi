"""Authoritative UDMI codebase, schema, specification, and sequencer test traversal tools."""

import fnmatch
import os
import re
from typing import Any, Dict, List, Optional, Tuple


def _get_udmi_root(udmi_root: Optional[str] = None) -> str:
    if udmi_root is not None:
        return os.path.abspath(udmi_root)
    # Default to repo root
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def _is_safe_path(target_path: str, root_path: str) -> bool:
    """Ensure target path resides within root path to prevent directory traversal."""
    try:
        common = os.path.commonpath([os.path.abspath(target_path), os.path.abspath(root_path)])
        return common == os.path.abspath(root_path)
    except Exception:
        return False


_TEST_DIR_SEGMENTS = ("test", "tests", "testing", "it")
_TEST_FILE_SUFFIXES = ("Test.java", "Tests.java", "TestBase.java", "IT.java", "_test.py")
_TEST_FILE_PREFIXES = ("test_",)

# Extensions search_codebase will read. `.log` is here because sequencer and
# device logs are the primary record of what a run actually did; omitting them
# left callers unable to read an available log and reduced to reconstructing
# runtime behaviour from Java source.
SEARCHABLE_EXTENSIONS = (
    ".java", ".py", ".json", ".md", ".yaml", ".yml", ".sh", ".txt", ".log",
)

# Directories skipped during traversal.
IGNORED_DIR_NAMES = frozenset({
    ".git", "build", "venv", ".venv", "node_modules", "target", ".idea",
    ".cache", "bin", "out", "var", ".gemini", "dist",
})

# The subset of skipped directories that holds run output rather than build or
# vendor content. Skipping these can hide the evidence a caller is looking for,
# so every such skip is reported back with the results: a zero-match search must
# never be indistinguishable from a search that never looked.
ARTIFACT_DIR_NAMES = frozenset({"out", "var"})



def _is_test_path(rel_path: str) -> bool:
    """Classify a repo-relative path as test source.

    Used to rank implementation files above tests in search results, so that the
    per-file and total result caps drop tests rather than the implementation the
    caller is actually looking for. Purely structural: no content inspection.
    """
    parts = rel_path.replace("\\", "/").split("/")
    filename = parts[-1]
    if any(segment in _TEST_DIR_SEGMENTS for segment in parts[:-1]):
        return True
    if filename.endswith(_TEST_FILE_SUFFIXES):
        return True
    if filename.startswith(_TEST_FILE_PREFIXES):
        return True
    return False


def read_udmi_file(
    file_path: str,
    start_line: Optional[int] = None,
    end_line: Optional[int] = None,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Reads exact lines from any source file, schema, or specification document in the UDMI repository.
    
    Args:
        file_path: Relative or absolute path to the file within the UDMI repository.
        start_line: Optional 1-based start line index.
        end_line: Optional 1-based end line index (inclusive).
        udmi_root: Optional override for the UDMI repository root directory.
    """
    root = _get_udmi_root(udmi_root)
    full_path = file_path if os.path.isabs(file_path) else os.path.join(root, file_path)
    full_path = os.path.abspath(full_path)

    if not _is_safe_path(full_path, root):
        return {
            "status": "ERROR",
            "error": f"Path '{file_path}' is outside the UDMI repository root.",
        }

    if not os.path.isfile(full_path):
        return {
            "status": "ERROR",
            "error": f"File not found: '{file_path}'",
        }

    try:
        with open(full_path, "r", encoding="utf-8", errors="replace") as f:
            lines = f.readlines()
    except Exception as e:
        return {
            "status": "ERROR",
            "error": f"Failed to read file '{file_path}': {e}",
        }

    total_lines = len(lines)
    s_idx = max(1, start_line) if start_line is not None else 1
    e_idx = min(total_lines, end_line) if end_line is not None else total_lines

    max_default_lines = 400
    truncated = False
    if start_line is None and end_line is None:
        if total_lines > max_default_lines:
            e_idx = max_default_lines
            truncated = True
    elif (e_idx - s_idx + 1) > 1000:
        e_idx = s_idx + 999
        truncated = True

    if s_idx > total_lines:
        content_lines = []
    else:
        content_lines = lines[s_idx - 1 : e_idx]

    content_str = "".join(content_lines)
    if truncated:
        content_str += f"\n\n[TRUNCATED: showing lines {s_idx}-{e_idx} of {total_lines}. Use start_line and end_line parameters to view other sections.]"

    rel_path = os.path.relpath(full_path, root)
    return {
        "status": "SUCCESS",
        "file_path": rel_path,
        "absolute_path": full_path,
        "total_lines": total_lines,
        "start_line": s_idx,
        "end_line": e_idx,
        "truncated": truncated,
        "content": content_str,
    }


def search_codebase(
    query: str,
    path_prefix: Optional[str] = None,
    file_pattern: Optional[str] = None,
    max_results: int = 25,
    max_per_file: int = 3,
    is_regex: bool = False,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Search source, docs, config, and log files in the UDMI repository.

    Reads these extensions only: .java, .py, .json, .md, .yaml, .yml, .sh,
    .txt, .log. A file_pattern selecting anything else is rejected rather
    than silently returning no matches.

    Run-output directories (out/, out_*/, var/) are not traversed by default
    because they are large and regenerated. Test artifacts live there, so any
    that were skipped are listed in `skipped_artifact_dirs`; pass one as
    path_prefix to search inside it.

    Args:
        query: Text or regex pattern to search for.
        path_prefix: Optional directory path prefix (relative to repo root, e.g. 'udmis', 'validator', 'pubber', 'common', 'docs', or a test run directory under a site model's out/) to restrict search.
        file_pattern: Optional glob pattern to filter files (e.g. '*.java', '*.json', '*.log').
        max_results: Maximum number of matches to return (default 25).
        max_per_file: Maximum number of matches sampled per file (default 3) to prevent single-file flooding.
        is_regex: Whether to treat query as a regular expression.
        udmi_root: Optional override for the UDMI repository root directory.
    """
    root = _get_udmi_root(udmi_root)
    if not os.path.isdir(root):
        return {
            "status": "ERROR",
            "error": f"UDMI root directory not found: {root}",
        }

    search_dir = root
    if path_prefix:
        norm_prefix = os.path.normpath(path_prefix.strip().lstrip("/\\"))
        target_dir = os.path.abspath(os.path.join(root, norm_prefix))
        if not _is_safe_path(target_dir, root):
            return {
                "status": "ERROR",
                "error": f"Path prefix '{path_prefix}' is outside the UDMI repository root.",
            }
        if not os.path.isdir(target_dir):
            return {
                "status": "ERROR",
                "error": f"Directory not found for path prefix: '{path_prefix}'",
            }
        search_dir = target_dir

    # A pattern this tool can never satisfy is a caller error, not an empty
    # result. Returning SUCCESS with zero matches here told callers the repo
    # contained no such file when in fact it was never opened.
    if file_pattern:
        _, pattern_ext = os.path.splitext(file_pattern)
        if pattern_ext and pattern_ext.lower() not in SEARCHABLE_EXTENSIONS:
            return {
                "status": "ERROR",
                "error": (
                    f"file_pattern '{file_pattern}' selects '{pattern_ext}' files, which "
                    f"search_codebase does not read. Searchable extensions: "
                    f"{', '.join(SEARCHABLE_EXTENSIONS)}."
                ),
                "searchable_extensions": list(SEARCHABLE_EXTENSIONS),
            }

    ignored_dirs = set(IGNORED_DIR_NAMES)

    pattern = None
    if is_regex:
        try:
            pattern = re.compile(query, re.IGNORECASE)
        except re.error as e:
            return {"status": "ERROR", "error": f"Invalid regex pattern: {e}"}
    else:
        q_lower = query.lower()

    collected: List[Dict[str, Any]] = []
    locations_summary: Dict[str, int] = {}
    file_match_counts: Dict[str, int] = {}
    skipped_artifact_dirs: List[str] = []

    for dirpath, dirnames, filenames in os.walk(search_dir):
        # Exclude ignored directories in-place (and out_* test runs). Sorting keeps
        # traversal independent of filesystem inode ordering, so identical queries
        # return identical results across machines and runs.
        kept = []
        for d in dirnames:
            if d in ARTIFACT_DIR_NAMES or d.startswith("out_"):
                # Run output. Record the exact path so the caller can re-issue
                # the search against it via path_prefix instead of concluding
                # from an empty result that the evidence does not exist.
                skipped_artifact_dirs.append(
                    os.path.relpath(os.path.join(dirpath, d), root)
                )
                continue
            if d in ignored_dirs or d.startswith("."):
                continue
            kept.append(d)
        dirnames[:] = sorted(kept)

        for filename in sorted(filenames):
            if file_pattern and not fnmatch.fnmatch(filename, file_pattern):
                continue

            # Only search text-like source/doc/log extensions
            _, ext = os.path.splitext(filename)
            if ext.lower() not in SEARCHABLE_EXTENSIONS:
                continue

            filepath = os.path.join(dirpath, filename)
            rel_path = os.path.relpath(filepath, root)
            top_dir = rel_path.split(os.sep)[0]
            is_test = _is_test_path(rel_path)

            try:
                with open(filepath, "r", encoding="utf-8", errors="ignore") as f:
                    for line_no, line in enumerate(f, 1):
                        matched = False
                        if is_regex and pattern:
                            if pattern.search(line):
                                matched = True
                        elif not is_regex:
                            if q_lower in line.lower():
                                matched = True

                        if matched:
                            locations_summary[top_dir] = locations_summary.get(top_dir, 0) + 1
                            curr_file_count = file_match_counts.get(rel_path, 0)
                            if curr_file_count < max_per_file:
                                file_match_counts[rel_path] = curr_file_count + 1
                                collected.append({
                                    "file": rel_path,
                                    "line": line_no,
                                    "snippet": line.strip()[:200],
                                    "is_test": is_test,
                                })
            except Exception:
                continue

    # Rank implementation sources above tests before applying the result cap, so
    # truncation discards test matches rather than the implementation file the
    # caller is looking for. Secondary keys keep the ordering deterministic.
    collected.sort(key=lambda m: (m["is_test"], m["file"], m["line"]))
    matches = collected[:max_results]
    test_matches_dropped = sum(
        1 for m in collected[max_results:] if m["is_test"]
    )

    total_matches_across_locations = sum(locations_summary.values())
    skipped = sorted(set(skipped_artifact_dirs))
    response = {
        "status": "SUCCESS",
        "query": query,
        "path_prefix": path_prefix,
        "matches_count": len(matches),
        "total_matches_found": total_matches_across_locations,
        "locations_summary": locations_summary,
        "matches": matches,
        "ranking": "implementation sources ranked above test sources",
        "test_matches_dropped": test_matches_dropped,
        "truncated": len(matches) < total_matches_across_locations,
        "searchable_extensions": list(SEARCHABLE_EXTENSIONS),
        "excluded_paths": sorted(ignored_dirs) + ["out_*", ".*"],
        "skipped_artifact_dirs": skipped,
    }
    if skipped:
        # Without this, "0 matches" and "never looked" are the same answer.
        response["skipped_artifact_dirs_note"] = (
            f"{len(skipped)} run-output director{'y was' if len(skipped) == 1 else 'ies were'} "
            "not traversed. Test artifacts live there; pass one as path_prefix to search it."
        )
    return response


def locate_udmi_doc(
    topic: str,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Finds authoritative markdown guides and specs in docs/ matching a topic.
    
    Args:
        topic: Topic or keyword (e.g. 'writeback', 'gateway', 'state', 'pointset', 'discovery', 'bacnet').
        udmi_root: Optional override for the UDMI repository root directory.
    """
    root = _get_udmi_root(udmi_root)
    docs_dir = os.path.join(root, "docs")
    if not os.path.isdir(docs_dir):
        return {
            "status": "ERROR",
            "error": f"Docs directory not found: {docs_dir}",
        }

    keywords = [k.lower() for k in re.split(r"[\s_\-\./]+", topic) if len(k) > 2]
    if not keywords:
        keywords = [topic.lower().strip()]

    scored_docs = []
    for dirpath, _, filenames in os.walk(docs_dir):
        for filename in filenames:
            if not filename.endswith(".md"):
                continue

            full_path = os.path.join(dirpath, filename)
            rel_path = os.path.relpath(full_path, root)
            base_clean = filename[:-3].lower()

            title = filename
            sections = []
            preview_lines = []

            score = 0
            # Filename match bonus
            for kw in keywords:
                if kw in base_clean:
                    score += 15
                if kw in rel_path.lower():
                    score += 5

            try:
                with open(full_path, "r", encoding="utf-8", errors="replace") as f:
                    doc_lines = f.readlines()

                for line in doc_lines:
                    l_str = line.strip()
                    if l_str.startswith("# "):
                        title = l_str[2:].strip()
                    elif l_str.startswith("## ") or l_str.startswith("### "):
                        sec_name = l_str.lstrip("#").strip()
                        sections.append(sec_name)

                    # Check keywords in line
                    for kw in keywords:
                        if kw in l_str.lower():
                            score += 1
                            if len(preview_lines) < 3 and len(l_str) > 10:
                                preview_lines.append(l_str[:160])
            except Exception:
                continue

            if score > 0:
                scored_docs.append({
                    "file": rel_path,
                    "title": title,
                    "score": score,
                    "sections": sections[:8],
                    "preview": " ... ".join(preview_lines[:2]),
                })

    scored_docs.sort(key=lambda x: x["score"], reverse=True)
    return {
        "status": "SUCCESS",
        "topic": topic,
        "matches_count": len(scored_docs),
        "documents": scored_docs[:10],
    }


def inspect_sequencer_test(
    test_name: str,
    udmi_root: Optional[str] = None,
) -> Dict[str, Any]:
    """Dynamically locates and parses Java test classes in validator/.../sequencer/sequences/.
    
    Extracts the exact test method, @Feature annotations, stage, timeout, assertions, and Java source code.
    
    Args:
        test_name: Name of the test sequence (e.g. 'pointset_publish', 'system_last_start', 'broken_config').
        udmi_root: Optional override for the UDMI repository root directory.
    """
    root = _get_udmi_root(udmi_root)
    seq_dir = os.path.join(
        root, "validator", "src", "main", "java", "com", "google", "daq", "mqtt", "sequencer"
    )
    if not os.path.isdir(seq_dir):
        return {
            "status": "ERROR",
            "error": f"Sequencer directory not found: {seq_dir}",
        }

    clean_test = test_name.strip()
    # Find all java files under sequencer
    java_files = []
    for dirpath, dirnames, filenames in os.walk(seq_dir):
        dirnames.sort()
        for f in sorted(filenames):
            if f.endswith(".java"):
                java_files.append(os.path.join(dirpath, f))

    test_pattern = re.compile(
        rf"(@Test\b[^{{]*?public\s+void\s+{re.escape(clean_test)}\s*\([^\)]*\)[^{{]*?\{{)",
        re.MULTILINE | re.DOTALL,
    )
    feature_pattern = re.compile(
        r"@Feature\s*\(\s*(?:stage\s*=\s*(?:Feature\.Stage\.)?([A-Z_]+))?\s*,?\s*(?:bucket\s*=\s*Bucket\.([A-Z_]+))?\s*,?\s*(?:description\s*=\s*\"([^\"]+)\")?",
        re.DOTALL,
    )

    for jf in java_files:
        try:
            with open(jf, "r", encoding="utf-8", errors="replace") as f:
                content = f.read()
        except Exception:
            continue

        match = test_pattern.search(content)
        if not match:
            # Fallback: check without @Test or method name case-insensitively
            m_fallback = re.search(
                rf"(public\s+void\s+({re.escape(clean_test)})\s*\([^\)]*\)[^{{]*?\{{)",
                content,
                re.IGNORECASE,
            )
            if m_fallback:
                match = m_fallback

        if match:
            start_pos = match.start()
            brace_start = match.end() - 1

            # Extract the full method by counting matched braces
            brace_count = 1
            idx = brace_start + 1
            while idx < len(content) and brace_count > 0:
                char = content[idx]
                if char == "{":
                    brace_count += 1
                elif char == "}":
                    brace_count -= 1
                idx += 1

            method_body = content[start_pos:idx]

            # Look backwards for preceding javadoc / comments
            preceding_content = content[:start_pos]
            javadoc_match = re.search(r"(/\*\*[\s\S]*?\*/)\s*$", preceding_content)
            javadoc = javadoc_match.group(1).strip() if javadoc_match else ""

            # Extract @Feature details if present in method declaration
            feature_match = feature_pattern.search(content[start_pos:brace_start])
            stage = feature_match.group(1) if feature_match and feature_match.group(1) else "ALPHA"
            bucket = feature_match.group(2) if feature_match and feature_match.group(2) else ""
            description = feature_match.group(3) if feature_match and feature_match.group(3) else ""

            # Extract assertions and expectations from body
            assertions = []
            for a_match in re.finditer(
                r"(checkThat\([^\)]+\)|assertTrue\([^\)]+\)|checkState\([^\)]+\)|waitForState\([^\)]+\)|untilTrue\([^\)]+\)|untilUntrue\([^\)]+\))",
                method_body,
            ):
                assertions.append(a_match.group(1).strip())

            # Detect timeouts
            timeout_match = re.search(r"(?:timeout|wait|after)\s*\(?([0-9]+)\)?", method_body, re.IGNORECASE)
            timeout_sec = int(timeout_match.group(1)) if timeout_match else 120

            rel_file = os.path.relpath(jf, root)
            class_name = os.path.splitext(os.path.basename(jf))[0]

            return {
                "status": "SUCCESS",
                "test_name": clean_test,
                "class_name": class_name,
                "file_path": rel_file,
                "stage": stage,
                "bucket": bucket,
                "description": description,
                "javadoc": javadoc,
                "timeout_sec": timeout_sec,
                "assertions": assertions[:10],
                "source_code": method_body,
            }

    # If test method not found, list available test sequences
    available_tests = []
    for jf in java_files:
        if "sequences" in jf.lower() or "sequence" in jf.lower():
            try:
                with open(jf, "r", encoding="utf-8", errors="replace") as f:
                    c = f.read()
                for m in re.finditer(r"@Test\b[^{]*?public\s+void\s+([a-zA-Z0-9_]+)\s*\(", c):
                    available_tests.append(m.group(1))
            except Exception:
                pass

    return {
        "status": "ERROR",
        "error": f"Sequencer test '{test_name}' not found in Java sequence classes.",
        "available_tests": sorted(list(set(available_tests)))[:30],
    }
