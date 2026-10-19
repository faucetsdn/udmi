"""Generic chunked upload reassembly utility for UDMI upload/* streams."""

import base64
import hashlib
import json
import os
import sys
from typing import Any, Dict, List

PCAP_MAGIC_NUMBERS = (
    b"\xa1\xb2\xc3\xd4",  # Standard pcap (same-endian)
    b"\xd4\xc3\xb2\xa1",  # Standard pcap (swapped-endian)
    b"\xa1\xb2\x3c\x4d",  # Nanosecond pcap (same-endian)
    b"\x4d\x3c\xb2\xa1",  # Nanosecond pcap (swapped-endian)
)


def parse_stream_events(raw_text: str) -> List[Dict[str, Any]]:
    """Parse upload chunk objects from JSON array, JSONL, or mosquitto_sub output."""
    raw_text = raw_text.strip()
    if not raw_text:
        return []

    if raw_text.startswith("["):
        try:
            data = json.loads(raw_text)
            if isinstance(data, list):
                return [
                    item.get("payload", item)
                    if isinstance(item, dict) and isinstance(item.get("payload"), dict)
                    else item
                    for item in data
                    if isinstance(item, dict)
                ]
        except json.JSONDecodeError:
            pass

    events: List[Dict[str, Any]] = []
    for line in raw_text.splitlines():
        line = line.strip()
        if not line:
            continue
        if " " in line and not line.startswith("{"):
            _, _, payload = line.partition(" ")
        else:
            payload = line

        try:
            obj = json.loads(payload)
            if isinstance(obj, dict):
                inner = (
                    obj["payload"]
                    if isinstance(obj.get("payload"), dict)
                    and ("event_no" in obj["payload"] or "data" in obj["payload"])
                    else obj
                )
                if "event_no" in inner or "data" in inner or "sha256" in inner:
                    events.append(inner)
        except json.JSONDecodeError:
            continue

    return events


def _assemble_and_verify(
    sorted_events: List[Dict[str, Any]], eof_events: List[Dict[str, Any]]
) -> bytes:
    """Validates sequential event_no, decodes Base64 chunks, and checks SHA-256."""
    total_chunks = len(sorted_events)
    if eof_events:
        terminal_event_no = eof_events[-1].get("event_no")
        if terminal_event_no is not None and int(terminal_event_no) < 0:
            expected_chunks = -(int(terminal_event_no) + 1)
            if expected_chunks != total_chunks:
                raise ValueError(
                    f"Chunk count mismatch: terminal event_no {terminal_event_no} "
                    f"expects {expected_chunks} chunks, found {total_chunks}."
                )

    assembled_data = bytearray()
    for idx, evt in enumerate(sorted_events, start=1):
        event_no = evt.get("event_no")
        if event_no is not None and int(event_no) != idx:
            raise ValueError(
                f"Non-contiguous upload chunk sequence: expected event_no {idx}, got {event_no}."
            )
        try:
            assembled_data.extend(base64.b64decode(evt["data"]))
        except Exception as err:
            raise ValueError(
                f"Failed to decode base64 chunk at event_no {idx}: {err}"
            ) from err

    if not assembled_data:
        raise ValueError("Reassembled upload payload is empty (0 bytes).")

    if eof_events and eof_events[-1].get("sha256"):
        expected_sha256 = str(eof_events[-1]["sha256"]).lower()
        actual_sha256 = hashlib.sha256(assembled_data).hexdigest().lower()
        if actual_sha256 != expected_sha256:
            raise ValueError(
                f"SHA-256 mismatch (expected {expected_sha256}, got {actual_sha256})."
            )
    return bytes(assembled_data)


def run_reassemble(input_source: str, output_path: str) -> None:
    """Reassembles chunked UDMI upload records into a binary artifact file."""
    if input_source == "-":
        raw_content = sys.stdin.read()
    else:
        if not os.path.exists(input_source):
            raise FileNotFoundError(f"Input file '{input_source}' does not exist.")
        with open(input_source, "r", encoding="utf-8", errors="replace") as f:
            raw_content = f.read()

    events = parse_stream_events(raw_content)
    if not events:
        raise ValueError("No valid upload records found in input.")

    data_events = [
        e for e in events if e.get("data") and (e.get("event_no") or 1) > 0
    ]
    if not data_events:
        raise ValueError("Found upload records but none contained base64 'data'.")

    eof_events = [
        e for e in events if e.get("sha256") or (e.get("event_no") or 0) < 0
    ]
    sorted_events = sorted(data_events, key=lambda e: int(e.get("event_no", 0)))
    assembled_data = _assemble_and_verify(sorted_events, eof_events)

    os.makedirs(os.path.dirname(os.path.abspath(output_path)), exist_ok=True)
    with open(output_path, "wb") as f:
        f.write(assembled_data)

    magic = assembled_data[:4]
    magic_status = (
        "VALID PCAP" if any(magic == m for m in PCAP_MAGIC_NUMBERS) else "BINARY"
    )
    first_event = events[0]
    print("========================================")
    print("Upload Reassembly Completed Successfully")
    print("========================================")
    print(
        f"Family / Gen   : {first_event.get('family', 'unknown')} / "
        f"{first_event.get('generation', 'unknown')}"
    )
    print(f"Chunks Decoded : {len(sorted_events)}")
    print(f"Total Bytes    : {len(assembled_data)}")
    print(f"Magic Bytes    : {magic.hex()} ({magic_status})")
    print(f"Output File    : {output_path}")
