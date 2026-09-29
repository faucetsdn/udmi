"""Dynamic skill discovery, YAML frontmatter parsing, and JIT context loader for Mantis."""

import os
import re
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Set


@dataclass
class Skill:
    name: str
    description: str
    path: str
    content: str
    is_builtin: bool = False


# Generic stop words to filter out when matching skills against user prompts
STOP_WORDS: Set[str] = {
    "where", "which", "using", "error", "point", "times", "these", "those",
    "there", "their", "about", "after", "before", "between", "could", "would",
    "should", "might", "shall", "under", "above", "other", "every", "first",
    "guide", "system", "device", "devices", "udmi", "skills", "skill",
    "perform", "inspect", "inspecting", "provides", "provide", "handling",
    "allows", "allow", "based", "helps", "including", "related", "common",
    "with", "from", "that", "this", "have", "been", "when", "what", "some",
    "and", "the", "for", "are", "can", "into", "then", "also", "just",
    "more", "than", "will", "over", "each", "both", "such", "how", "does",
    "test", "tests", "testing", "triage", "investigate", "investigation",
    "diagnose", "diagnosis", "check", "checking", "report", "reporting"
}


class SkillManager:
    """Discovers, indexes, and dynamically loads Markdown SKILL.md files."""

    def __init__(self, udmi_root: Optional[str] = None):
        if udmi_root is not None:
            self.udmi_root = os.path.abspath(udmi_root)
        else:
            self.udmi_root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
        self._skills: Dict[str, Skill] = {}
        self.refresh()

    def _parse_skill_file(self, filepath: str, is_builtin: bool = False) -> Optional[Skill]:
        """Parse YAML frontmatter and body from a SKILL.md file with robust YAML support."""
        if not os.path.isfile(filepath):
            return None

        try:
            with open(filepath, "r", encoding="utf-8") as f:
                raw_text = f.read()
        except Exception:
            return None

        # Extract frontmatter between --- and ---
        fm_match = re.match(r"^---\s*\n(.*?)\n---\s*\n(.*)$", raw_text, re.DOTALL)
        if not fm_match:
            return None

        frontmatter_text = fm_match.group(1)
        body_content = fm_match.group(2).strip()

        name = ""
        description = ""

        # Robust YAML parsing (supports multi-line folded strings '>', lists, etc.)
        try:
            import yaml
            data = yaml.safe_load(frontmatter_text)
            if isinstance(data, dict):
                name = str(data.get("name", "")).strip()
                desc_val = data.get("description", "")
                if isinstance(desc_val, list):
                    description = " ".join(str(d).strip() for d in desc_val)
                else:
                    description = str(desc_val).strip()
        except Exception:
            pass

        # Fallback to line-by-line parsing if YAML parser failed
        if not name or not description:
            desc_lines = []
            in_desc = False
            for line in frontmatter_text.splitlines():
                stripped = line.strip()
                if stripped.startswith("name:") and not name:
                    name = stripped[5:].strip().strip("\"'")
                    in_desc = False
                elif stripped.startswith("description:") and not description:
                    val = stripped[12:].strip().strip("\"'")
                    if val in (">", "|"):
                        in_desc = True
                    else:
                        description = val
                        in_desc = False
                elif in_desc:
                    if line.startswith(" ") or line.startswith("\t"):
                        desc_lines.append(stripped)
                    else:
                        in_desc = False
            if desc_lines and not description:
                description = " ".join(desc_lines)

        if not name:
            name = os.path.basename(os.path.dirname(filepath))

        return Skill(
            name=name,
            description=description,
            path=filepath,
            content=body_content,
            is_builtin=is_builtin,
        )

    def refresh(self) -> None:
        """Scan mantis/skills/ and sites/*/skills/ for SKILL.md files."""
        self._skills.clear()

        # 1. Global Built-in Skills: mantis/skills/
        builtin_dir = os.path.join(self.udmi_root, "mantis", "skills")
        if os.path.isdir(builtin_dir):
            for entry in sorted(os.listdir(builtin_dir)):
                skill_file = os.path.join(builtin_dir, entry, "SKILL.md")
                skill = self._parse_skill_file(skill_file, is_builtin=True)
                if skill:
                    self._skills[skill.name] = skill

        # 2. Site-Specific Skills: sites/*/skills/
        sites_dir = os.path.join(self.udmi_root, "sites")
        if os.path.isdir(sites_dir):
            for site_name in sorted(os.listdir(sites_dir)):
                site_skills_dir = os.path.join(sites_dir, site_name, "skills")
                if os.path.isdir(site_skills_dir):
                    for entry in sorted(os.listdir(site_skills_dir)):
                        skill_file = os.path.join(site_skills_dir, entry, "SKILL.md")
                        skill = self._parse_skill_file(skill_file, is_builtin=False)
                        if skill:
                            self._skills[skill.name] = skill

    def list_skills(self) -> List[Dict[str, Any]]:
        """List all discovered skills with metadata."""
        return [
            {
                "name": s.name,
                "description": s.description,
                "path": s.path,
                "is_builtin": s.is_builtin,
            }
            for s in self._skills.values()
        ]

    def get_skill(self, name: str) -> Optional[Skill]:
        """Retrieve a specific skill by name."""
        return self._skills.get(name.strip())

    def match_skills(self, query: str) -> List[Skill]:
        """Match relevant skills based on user instruction using token boundaries and stop-word filtering."""
        q_lower = query.lower()
        q_words = set(re.findall(r"[a-z0-9]+", q_lower))
        q_tokens = set(re.findall(r"[a-z0-9_-]+", q_lower)) | q_words
        meaningful_q_tokens = {w for w in q_words if len(w) > 2 and w not in STOP_WORDS}

        matched = []
        for s in self._skills.values():
            s_name_lower = s.name.lower()
            name_tokens = set(s_name_lower.replace("-", " ").replace("_", " ").split())
            meaningful_name_tokens = {w for w in name_tokens if len(w) > 2 and w not in STOP_WORDS}

            # 1. Exact skill name match in query
            if s_name_lower in q_lower:
                matched.append(s)
                continue

            # 2. Significant name token overlap (all meaningful name tokens present)
            if meaningful_name_tokens and meaningful_name_tokens.issubset(q_words):
                matched.append(s)
                continue

            # 3. Specific domain keyword matching (excluding stop words)
            desc_tokens = set(re.findall(r"[a-z0-9]+", s.description.lower()))
            meaningful_desc = {w for w in desc_tokens if len(w) > 3 and w not in STOP_WORDS}

            # Check overlap between query terms and meaningful description terms
            overlap = meaningful_q_tokens.intersection(meaningful_desc)
            name_overlap = meaningful_q_tokens.intersection(meaningful_name_tokens)
            # Require at least 2 distinct meaningful keywords or at least 1 distinct name token
            if len(overlap) >= 2 or len(name_overlap) >= 1:
                matched.append(s)

        return matched

    def get_system_prompt_catalog(self) -> str:
        """Render a concise skill catalog for system prompt progressive disclosure."""
        if not self._skills:
            return "No skills currently loaded."

        lines = ["## Available Domain Skills Catalog:"]
        for s in self._skills.values():
            tier = "Global Built-in" if s.is_builtin else "Site-Specific"
            lines.append(f"- **`{s.name}`** ({tier}): {s.description}")
        return "\n".join(lines)

    def load_skills_for_context(self, query: str) -> str:
        """Dynamically load the full content of matching skills for JIT injection into prompt.
        Does not inject arbitrary fallback skills when no skills match."""
        matched = self.match_skills(query)
        if not matched:
            return ""

        blocks = []
        for s in matched:
            blocks.append(f"### Skill Reference: {s.name}\n{s.content}")
        return "\n\n".join(blocks)
