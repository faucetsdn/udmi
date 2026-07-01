import json
import math
import os
import asyncio
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple
from google import genai


class SemanticCache:
    """
    Lightweight, async-first vector database / semantic similarity store.
    Caches triaged failures using Gemini embeddings to deliver millisecond-level
    zero-shot triages for recurrent or flaky failures.
    """

    def __init__(
        self,
        client: genai.Client,
        cache_filepath: Path,
        embedding_model: str = "models/gemini-embedding-2",
        similarity_threshold: float = 0.90,
        rate_limiter: Optional[Any] = None,
    ):
        self.client = client
        self.cache_filepath = Path(cache_filepath)
        self.embedding_model = embedding_model
        self.similarity_threshold = similarity_threshold
        self.rate_limiter = rate_limiter
        self.lock = asyncio.Lock()
        self.entries: List[Dict[str, Any]] = []

    async def load_async(self):
        """Asynchronously loads the cache from disk."""
        async with self.lock:
            loop = asyncio.get_running_loop()
            await loop.run_in_executor(None, self._load)

    def _load(self):
        if not self.cache_filepath.exists():
            self.entries = []
            return
        try:
            with open(self.cache_filepath, "r", encoding="utf-8") as f:
                self.entries = json.load(f)
            print(
                f"[Semantic Cache] Loaded {len(self.entries)} entries from {self.cache_filepath}"
            )
        except Exception as e:
            print(
                f"Warning: Failed to load semantic cache from {self.cache_filepath}: {e}"
            )
            self.entries = []

    async def save_async(self):
        """Asynchronously saves the cache to disk."""
        async with self.lock:
            os.makedirs(self.cache_filepath.parent, exist_ok=True)
            loop = asyncio.get_running_loop()
            await loop.run_in_executor(None, self._save)

    def _save(self):
        try:
            # Use a temporary file and rename to ensure atomic writes
            tmp_path = self.cache_filepath.with_suffix(".tmp")
            with open(tmp_path, "w", encoding="utf-8") as f:
                json.dump(self.entries, f, indent=2)
            os.replace(tmp_path, self.cache_filepath)
        except Exception as e:
            print(
                f"Error: Failed to save semantic cache to {self.cache_filepath}: {e}"
            )

    async def get_embedding(self, text: str) -> List[float]:
        """Generates an embedding for the given text using the GenAI SDK."""
        # Truncate text if too long to fit in embedding model limit
        truncated_text = text[:10000]

        if self.rate_limiter:
            from engine.harness.rate_limiter import RateLimitTimeoutError
            acquired = await self.rate_limiter.acquire(timeout_seconds=45.0)
            if not acquired:
                raise RateLimitTimeoutError("Timeout budget exceeded waiting for Gemini embedding rate-limiter tokens.")

        # Call embedding API asynchronously
        response = await self.client.aio.models.embed_content(
            model=self.embedding_model, contents=truncated_text
        )

        if not response.embeddings:
            raise ValueError("No embeddings returned from the model.")

        return response.embeddings[0].values

    def _cosine_similarity(self, v1: List[float], v2: List[float]) -> float:
        if len(v1) != len(v2):
            return 0.0
        dot_product = sum(a * b for a, b in zip(v1, v2))
        magnitude_v1 = math.sqrt(sum(a * a for a in v1))
        magnitude_v2 = math.sqrt(sum(b * b for b in v2))
        if magnitude_v1 * magnitude_v2 == 0:
            return 0.0
        return dot_product / (magnitude_v1 * magnitude_v2)

    async def lookup(
        self,
        query_text: str,
        namespace: Optional[str] = None,
        fallback_namespaces: Optional[List[str]] = None,
    ) -> Tuple[Optional[Dict[str, Any]], float]:
        """
        Looks up the query text in the semantic cache.
        Supports namespaced lookups and falling back to a list of cascading namespaces.
        Returns the best matching entry and its similarity score if it exceeds the threshold.
        """
        if not self.entries:
            return None, 0.0

        from engine.util.template import normalize_log_text
        normalized_query = normalize_log_text(query_text)
        query_vector = await self.get_embedding(normalized_query)

        target_ns = namespace or "global"
        namespaces_to_try = [target_ns]
        if fallback_namespaces:
            namespaces_to_try.extend(fallback_namespaces)

        # Remove duplicate namespace checks while preserving order
        seen = set()
        ordered_namespaces = [ns for ns in namespaces_to_try if not (ns in seen or seen.add(ns))]

        for ns in ordered_namespaces:
            best_match = None
            best_score = -1.0

            # Filter entries matching current namespace index
            ns_entries = [
                e for e in self.entries
                if e.get("namespace", "global") == ns
            ]

            for entry in ns_entries:
                entry_vector = entry.get("embedding")
                if not entry_vector:
                    continue

                score = self._cosine_similarity(query_vector, entry_vector)
                if score > best_score:
                    best_score = score
                    best_match = entry

            if best_score >= self.similarity_threshold:
                return best_match, best_score

        # If no namespace match reached similarity_threshold, return None and final checked score
        return None, best_score

    async def add(
        self,
        failure_text: str,
        triage_report: str,
        metadata: Dict[str, Any] = None,
        namespace: Optional[str] = None,
    ) -> Dict[str, Any]:
        """
        Adds a new triage result to the cache under a specific namespace index.
        Generates its embedding and stores it.
        """
        from engine.util.template import normalize_log_text
        normalized_failure = normalize_log_text(failure_text)
        embedding = await self.get_embedding(normalized_failure)

        # Capture current timestamp
        from datetime import datetime, timezone

        timestamp = datetime.now(timezone.utc).isoformat()
        ns = namespace or "global"

        new_entry = {
            "failure_text": failure_text[:2000],  # store a longer snippet for context
            "embedding": embedding,
            "triage_report": triage_report,
            "metadata": metadata or {},
            "timestamp": timestamp,
            "namespace": ns,
        }

        async with self.lock:
            # Avoid duplicate additions of exact same failure logs in same namespace
            exists = any(
                e["failure_text"] == new_entry["failure_text"] and e.get("namespace", "global") == ns
                for e in self.entries
            )
            if not exists:
                self.entries.append(new_entry)
                print(
                    f"[Semantic Cache] Added new failure entry (size: {len(failure_text)} chars) to cache under namespace '{ns}'."
                )

        await self.save_async()
        return new_entry

