"""Handler for UDMI upload/discovery chunk reassembly, SHA-256 verification, and GCS archival."""

import base64
import gzip
import hashlib
import json
import os
import re
import threading
import time
from typing import Any, Dict, Optional, Tuple

from butler.src.handlers.base import BaseHandler
from udmi.common.db.influx import InfluxManager
from udmi.common.db.postgres import PostgresManager

GZIP_MAGIC = b"\x1f\x8b"
DEFAULT_SESSION_TTL_SEC = 14400.0
MAX_COMPLETED_CACHE = 256


def _decode_chunk_payload(b64_data: str) -> bytes:
    """Base64-decodes and decompresses a gzip PCAP chunk payload."""
    raw = base64.b64decode(b64_data)
    if raw.startswith(GZIP_MAGIC):
        return gzip.decompress(raw)
    return raw


def _sanitize_path_component(val: str) -> str:
    """Sanitizes a string for safe filesystem/object path usage."""
    return re.sub(r"[^A-Za-z0-9._-]", "_", str(val or "unknown"))


class _PcapSession:
    """In-memory reassembly state for a single (site, device_id, family, generation) stream."""

    def __init__(self, site: str, device_id: str, family: str, generation: str) -> None:
        self.site = site
        self.device_id = device_id
        self.family = family
        self.generation = generation
        self.created_at = time.monotonic()
        self.chunks: Dict[int, bytes] = {}
        self.expected_chunks: Optional[int] = None
        self.expected_sha256: Optional[str] = None
        self.scan_manifest: Optional[Dict[str, Any]] = None
        self.completed: bool = False
        self.pcap_uri: Optional[str] = None
        self.manifest_uri: Optional[str] = None
        self.actual_sha256: Optional[str] = None
        self.total_bytes: int = 0


class PcapUploadHandler(BaseHandler):
    """Reassembles upload/discovery chunks, verifies sha256, and writes standard .pcap + scan_manifest.json."""

    def __init__(
        self,
        archive_dir: Optional[str] = None,
        gcs_bucket: Optional[str] = None,
        gcs_client: Optional[Any] = None,
        session_ttl_sec: float = DEFAULT_SESSION_TTL_SEC,
    ) -> None:
        self.archive_dir = archive_dir or os.environ.get("BUTLER_PCAP_DIR", "out/pcap")
        self.gcs_bucket = gcs_bucket or os.environ.get("BUTLER_PCAP_GCS_BUCKET")
        self.gcs_client = gcs_client
        self.session_ttl_sec = session_ttl_sec
        self._sessions: Dict[Tuple[str, str, str, str], _PcapSession] = {}
        self._completed_results: Dict[Tuple[str, str, str, str], Dict[str, Any]] = {}
        self._lock = threading.Lock()

    def _evict_expired_sessions(self) -> None:
        """Evicts incomplete sessions older than session_ttl_sec."""
        now = time.monotonic()
        expired_keys = [
            key
            for key, sess in self._sessions.items()
            if (now - sess.created_at) > self.session_ttl_sec
        ]
        for key in expired_keys:
            self._sessions.pop(key, None)

    def can_handle(
        self,
        sub_folder: Optional[str],
        sub_type: Optional[str],
        payload: Dict[str, Any],
    ) -> bool:
        del payload
        return sub_type == "upload" and sub_folder == "discovery"

    def _upsert_capture_metadata(
        self,
        postgres_manager: Optional[PostgresManager],
        session: _PcapSession,
        status: str,
        error_message: Optional[str] = None,
    ) -> None:
        """Persists lightweight PCAP capture status and URIs (never raw chunk data) to PostgreSQL."""
        if not postgres_manager:
            return
        if isinstance(postgres_manager, PostgresManager):
            conn = postgres_manager.get_connection()
            try:
                with conn.cursor() as cur:
                    manifest_json = (
                        json.dumps(session.scan_manifest)
                        if session.scan_manifest is not None
                        else None
                    )
                    cur.execute(
                        """
                        INSERT INTO udmi_pcap_captures (
                            site, device_id, family, generation, status,
                            chunks_received, expected_chunks, expected_sha256,
                            actual_sha256, total_bytes, pcap_uri, manifest_uri,
                            scan_manifest, error_message, updated_at
                        )
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s::jsonb, %s, CURRENT_TIMESTAMP)
                        ON CONFLICT (site, device_id, family, generation)
                        DO UPDATE SET
                            status = EXCLUDED.status,
                            chunks_received = EXCLUDED.chunks_received,
                            expected_chunks = COALESCE(EXCLUDED.expected_chunks, udmi_pcap_captures.expected_chunks),
                            expected_sha256 = COALESCE(EXCLUDED.expected_sha256, udmi_pcap_captures.expected_sha256),
                            actual_sha256 = COALESCE(EXCLUDED.actual_sha256, udmi_pcap_captures.actual_sha256),
                            total_bytes = EXCLUDED.total_bytes,
                            pcap_uri = COALESCE(EXCLUDED.pcap_uri, udmi_pcap_captures.pcap_uri),
                            manifest_uri = COALESCE(EXCLUDED.manifest_uri, udmi_pcap_captures.manifest_uri),
                            scan_manifest = COALESCE(EXCLUDED.scan_manifest, udmi_pcap_captures.scan_manifest),
                            error_message = EXCLUDED.error_message,
                            updated_at = CURRENT_TIMESTAMP;
                        """,
                        (
                            session.site,
                            session.device_id,
                            session.family,
                            session.generation,
                            status,
                            len(session.chunks),
                            session.expected_chunks,
                            session.expected_sha256,
                            session.actual_sha256,
                            session.total_bytes,
                            session.pcap_uri,
                            session.manifest_uri,
                            manifest_json,
                            error_message,
                        ),
                    )
                conn.commit()
            finally:
                conn.close()
        elif hasattr(postgres_manager, "insert_row"):
            postgres_manager.insert_row(
                "udmi_pcap_captures",
                {
                    "site": session.site,
                    "device_id": session.device_id,
                    "family": session.family,
                    "generation": session.generation,
                    "status": status,
                    "chunks_received": len(session.chunks),
                    "expected_chunks": session.expected_chunks,
                    "expected_sha256": session.expected_sha256,
                    "actual_sha256": session.actual_sha256,
                    "total_bytes": session.total_bytes,
                    "pcap_uri": session.pcap_uri,
                    "manifest_uri": session.manifest_uri,
                    "scan_manifest": session.scan_manifest,
                    "error_message": error_message,
                },
            )

    def _upload_to_gcs(
        self, blob_path: str, content: bytes, content_type: str
    ) -> None:
        """Uploads a reassembled artifact to GCS when gcs_client is configured."""
        if not self.gcs_bucket or self.gcs_client is None:
            return
        bucket = self.gcs_client.bucket(self.gcs_bucket)
        blob = bucket.blob(blob_path)
        blob.upload_from_string(content, content_type=content_type)

    def _finalize_session(
        self,
        session: _PcapSession,
        postgres_manager: Optional[PostgresManager],
    ) -> Dict[str, Any]:
        """Concatenates chunks 1..N, verifies SHA-256, and writes .pcap + scan_manifest.json."""
        expected_n = session.expected_chunks or 0
        if expected_n <= 0:
            err_msg = (
                f"PCAP capture for {session.site}/{session.device_id}/"
                f"{session.generation} has 0 expected chunks"
            )
            self._upsert_capture_metadata(
                postgres_manager, session, status="FAILED", error_message=err_msg
            )
            raise ValueError(err_msg)

        assembled = bytearray()
        for idx in range(1, expected_n + 1):
            assembled.extend(session.chunks[idx])

        pcap_bytes = bytes(assembled)
        if not pcap_bytes:
            err_msg = (
                f"PCAP capture for {session.site}/{session.device_id}/"
                f"{session.generation} reassembled to 0 bytes"
            )
            self._upsert_capture_metadata(
                postgres_manager, session, status="FAILED", error_message=err_msg
            )
            raise ValueError(err_msg)

        actual_sha256 = hashlib.sha256(pcap_bytes).hexdigest()
        session.actual_sha256 = actual_sha256
        session.total_bytes = len(pcap_bytes)

        if session.expected_sha256 and actual_sha256 != session.expected_sha256:
            err_msg = (
                f"PCAP SHA-256 digest mismatch for {session.site}/{session.device_id}/"
                f"{session.generation}: expected {session.expected_sha256}, got {actual_sha256}"
            )
            self._upsert_capture_metadata(
                postgres_manager, session, status="FAILED", error_message=err_msg
            )
            raise ValueError(err_msg)

        safe_site = _sanitize_path_component(session.site)
        safe_gen = _sanitize_path_component(session.generation)
        target_dir = os.path.join(self.archive_dir, safe_site)
        os.makedirs(target_dir, exist_ok=True)

        local_pcap_path = os.path.abspath(
            os.path.join(target_dir, f"{safe_gen}.pcap")
        )
        with open(local_pcap_path, "wb") as pf:
            pf.write(pcap_bytes)

        local_manifest_path = os.path.abspath(
            os.path.join(target_dir, f"{safe_gen}_scan_manifest.json")
        )
        manifest_obj = session.scan_manifest or {}
        manifest_bytes = json.dumps(manifest_obj, indent=2).encode("utf-8")
        with open(local_manifest_path, "wb") as mf:
            mf.write(manifest_bytes)

        if self.gcs_bucket:
            pcap_blob_path = f"{session.site}/{session.generation}.pcap"
            manifest_blob_path = (
                f"{session.site}/{session.generation}_scan_manifest.json"
            )
            self._upload_to_gcs(
                pcap_blob_path,
                pcap_bytes,
                content_type="application/vnd.tcpdump.pcap",
            )
            self._upload_to_gcs(
                manifest_blob_path,
                manifest_bytes,
                content_type="application/json",
            )
            session.pcap_uri = f"gs://{self.gcs_bucket}/{pcap_blob_path}"
            session.manifest_uri = f"gs://{self.gcs_bucket}/{manifest_blob_path}"
        else:
            session.pcap_uri = local_pcap_path
            session.manifest_uri = local_manifest_path

        session.completed = True
        self._upsert_capture_metadata(postgres_manager, session, status="COMPLETED")

        return {
            "target": "gcs",
            "table": "udmi_pcap_captures",
            "status": "COMPLETED",
            "count": 1,
            "chunks": expected_n,
            "bytes": len(pcap_bytes),
            "sha256": actual_sha256,
            "pcap_uri": session.pcap_uri,
            "manifest_uri": session.manifest_uri,
            "local_pcap_path": local_pcap_path,
            "local_manifest_path": local_manifest_path,
        }

    def process_message(
        self,
        envelope: Dict[str, Any],
        payload: Dict[str, Any],
        postgres_manager: PostgresManager,
        influx_manager: InfluxManager,
    ) -> Dict[str, Any]:
        del influx_manager
        if (
            isinstance(payload, dict)
            and "payload" in payload
            and isinstance(payload.get("payload"), dict)
        ):
            payload = payload["payload"]

        site = str(envelope.get("deviceRegistryId") or "unknown")
        device_id = str(envelope.get("deviceId") or "unknown")
        family = str(payload.get("family") or "bacnet")
        generation = str(payload.get("generation") or "unknown")
        event_no = int(payload.get("event_no", 0))

        session_key = (site, device_id, family, generation)

        with self._lock:
            self._evict_expired_sessions()

            if session_key in self._completed_results:
                return dict(self._completed_results[session_key])

            session = self._sessions.get(session_key)
            if event_no == 0:
                if session is None:
                    session = _PcapSession(site, device_id, family, generation)
                    self._sessions[session_key] = session
                    self._upsert_capture_metadata(
                        postgres_manager, session, status="RECEIVING"
                    )
                return {
                    "target": "gcs",
                    "table": "udmi_pcap_captures",
                    "status": "RECEIVING",
                    "count": 0,
                    "chunks_received": len(session.chunks),
                }

            if session is None:
                session = _PcapSession(site, device_id, family, generation)
                self._sessions[session_key] = session

            if event_no > 0:
                b64_data = payload.get("data")
                if not b64_data:
                    raise ValueError(
                        f"Missing 'data' in upload/discovery chunk event_no={event_no}"
                    )
                raw_chunk = _decode_chunk_payload(b64_data)
                session.chunks[event_no] = raw_chunk
                session.total_bytes = sum(len(c) for c in session.chunks.values())
            else:
                # Terminal EOF marker: event_no = -(N + 1)
                session.expected_chunks = (-event_no) - 1
                session.expected_sha256 = payload.get("sha256")
                if isinstance(payload.get("scan_manifest"), dict):
                    session.scan_manifest = payload["scan_manifest"]

            if (
                not session.completed
                and session.expected_chunks is not None
                and all(
                    idx in session.chunks
                    for idx in range(1, session.expected_chunks + 1)
                )
            ):
                try:
                    result = self._finalize_session(session, postgres_manager)
                    if len(self._completed_results) >= MAX_COMPLETED_CACHE:
                        oldest_key = next(iter(self._completed_results))
                        self._completed_results.pop(oldest_key, None)
                    self._completed_results[session_key] = result
                    return result
                finally:
                    self._sessions.pop(session_key, None)

            self._upsert_capture_metadata(
                postgres_manager, session, status="RECEIVING"
            )
            return {
                "target": "gcs",
                "table": "udmi_pcap_captures",
                "status": "RECEIVING",
                "count": 0,
                "chunks_received": len(session.chunks),
            }
