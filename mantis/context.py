"""Mantis Conversation Context, Active Pointer Tracking, and Memory Compaction."""

from datetime import datetime
import json
import os
import re
from typing import Any, Dict, List, Optional
from mantis.models import ChatMessage, MessageRole, SessionContext


class ContextManager:
    """Manages multi-turn conversation state, active target pointers, memory compaction, and optional disk persistence."""

    @staticmethod
    def get_default_cache_path(udmi_root: Optional[str] = None) -> str:
        """Get canonical path to persistent active context cache."""
        if udmi_root is not None:
            return os.path.abspath(os.path.join(udmi_root, "var", "instances", "active_context.json"))
        return os.path.abspath(
            os.path.join(os.path.dirname(__file__), "..", "var", "instances", "active_context.json")
        )

    def __init__(
        self,
        context: Optional[SessionContext] = None,
        max_history_turns: int = 20,
        max_token_chars_per_turn: int = 8000,
        cache_file: Optional[str] = None,
        persist: bool = False,
        udmi_root: Optional[str] = None,
    ):
        if cache_file is not None:
            self.cache_file = os.path.abspath(cache_file)
        elif persist:
            self.cache_file = self.get_default_cache_path(udmi_root)
        else:
            self.cache_file = None

        self.max_history_turns = max_history_turns
        self.max_token_chars_per_turn = max_token_chars_per_turn

        if context is not None:
            self.context = context
        elif self.cache_file:
            loaded = self.load_context()
            self.context = loaded if loaded is not None else SessionContext()
        else:
            self.context = SessionContext()

    def load_context(self) -> Optional[SessionContext]:
        """Load persisted session context from disk if present."""
        if not self.cache_file or not os.path.isfile(self.cache_file):
            return None
        try:
            with open(self.cache_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                return SessionContext(**data)
        except Exception:
            return None

    def save_context(self) -> bool:
        """Persist current session context to disk."""
        if not self.cache_file:
            return False
        try:
            os.makedirs(os.path.dirname(self.cache_file), exist_ok=True)
            with open(self.cache_file, "w", encoding="utf-8") as f:
                json.dump(self.context.model_dump(), f, indent=2)
            return True
        except Exception:
            return False

    def add_user_message(self, content: str) -> ChatMessage:
        """Record a user prompt, auto-detect target entities, compact memory, and sync to disk."""
        self._auto_update_pointers(content)
        msg = ChatMessage(
            role=MessageRole.USER,
            content=content,
            timestamp=datetime.now().isoformat(),
        )
        self.context.history.append(msg)
        self._compact_history()
        self.save_context()
        return msg

    def add_assistant_message(self, content: str, tool_name: Optional[str] = None) -> ChatMessage:
        """Record an agent response, compact memory, and sync to disk."""
        msg = ChatMessage(
            role=MessageRole.ASSISTANT,
            content=content,
            timestamp=datetime.now().isoformat(),
            tool_name=tool_name,
        )
        self.context.history.append(msg)
        self._compact_history()
        self.save_context()
        return msg

    def _auto_update_pointers(self, text: str) -> None:
        """Extract explicit entities from user prompt to update active session pointers."""
        # Device pattern (e.g. "device AHU-1", "dut AHU-1", "for device AHU-2", "on device AHU-1")
        dev_m = re.search(
            r"\b(?:device\s+|dut\s+|(?:for|on)\s+device\s+)([A-Za-z0-9_-]+)\b",
            text,
            re.IGNORECASE,
        )
        if not dev_m:
            # Match "on <ID>" or "for <ID>" only if ID has a hyphen or underscore with alphanumeric components (e.g. AHU-1, GAT-1, bacnet_1)
            dev_m = re.search(
                r"\b(?:for|on)\s+([A-Za-z0-9]+[-_][A-Za-z0-9_-]+)\b",
                text,
                re.IGNORECASE,
            )
        if dev_m:
            dev_candidate = dev_m.group(1).strip()
            if dev_candidate.lower() not in (
                "the", "this", "that", "all", "sites", "test", "site", "device", "model",
                "with", "a", "an", "for", "me", "us", "it", "them", "him", "her", "localhost",
                "cloud", "broker", "server", "monday", "tuesday", "wednesday", "thursday",
                "friday", "saturday", "sunday", "today", "yesterday", "tomorrow", "moxa"
            ):
                self.context.active_device_id = dev_candidate

        # Test sequence pattern (e.g. "test pointset_publish", "run pointset_publish", "did pointset_publish")
        test_m = re.search(
            r"\b(?:sequence\s+|test\s+(?:sequence\s+)?|run\s+(?:test\s+)?|did\s+)([a-z0-9_]{4,})\b",
            text,
            re.IGNORECASE,
        )
        if test_m:
            test_candidate = test_m.group(1).strip()
            if test_candidate.lower() not in (
                "site", "model", "with", "from", "this", "that", "message", "setup",
                "environment", "stack", "test", "local", "pointset", "sequencer", "execution"
            ) or test_candidate.lower().endswith("_publish") or "_" in test_candidate:
                self.context.active_test_id = test_candidate

        # Site model pattern (e.g. sites/udmi_site_model)
        site_m = re.search(r"\b(sites/[a-zA-Z0-9_\-\./]+)\b", text)
        if site_m:
            self.context.active_site_model = site_m.group(1)

        # Session ID pattern (e.g. "session dev_1", "environment run_123")
        sess_m = re.search(r"\b(?:session|environment|setup)\s+['\"]?([a-zA-Z0-9_-]+)['\"]?", text, re.IGNORECASE)
        if sess_m:
            sess_candidate = sess_m.group(1).strip()
            if sess_candidate.lower() not in ("for", "with", "the", "this", "that", "local", "isolated"):
                self.context.active_session_id = sess_candidate

    def _compact_history(self) -> None:
        """Enforce sliding window and compact overly large log outputs."""
        if len(self.context.history) > self.max_history_turns * 2:
            self.context.history = self.context.history[-(self.max_history_turns * 2):]

        for msg in self.context.history:
            if len(msg.content) > self.max_token_chars_per_turn:
                half = self.max_token_chars_per_turn // 2
                head = msg.content[:half]
                tail = msg.content[-half:]
                msg.content = f"{head}\n\n...[Log output truncated for context compaction]...\n\n{tail}"

    def get_llm_contents(self, current_prompt: str) -> List[Any]:
        """Convert conversation history and current prompt into google.genai.types.Content objects."""
        from google.genai import types

        contents: List[Any] = []
        # Exclude last item if it's the current prompt already appended
        history_items = self.context.history
        if history_items and history_items[-1].content == current_prompt and history_items[-1].role == MessageRole.USER:
            history_items = history_items[:-1]

        for msg in history_items:
            role = "user" if msg.role == MessageRole.USER else "model"
            contents.append(
                types.Content(
                    role=role,
                    parts=[types.Part.from_text(text=msg.content)],
                )
            )

        contents.append(
            types.Content(
                role="user",
                parts=[types.Part.from_text(text=current_prompt)],
            )
        )
        return contents

    def clear_history(self) -> None:
        """Clear conversation messages while preserving active device/session pointers and sync."""
        self.context.history.clear()
        self.save_context()

    def reset(self) -> None:
        """Reset all context pointers and history, and remove persisted disk cache."""
        self.context = SessionContext()
        if self.cache_file and os.path.isfile(self.cache_file):
            try:
                os.remove(self.cache_file)
            except Exception:
                pass
        self.save_context()
