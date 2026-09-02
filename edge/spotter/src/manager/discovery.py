"""Spotter Unified Discovery Manager.

Extends standard DiscoveryManager to natively handle both active protocol sweeps
(BACnet, Ether, Passive) and TRACE-level packet streaming (PCAP) over events/stream.
"""

import base64
import logging
import threading
import time
from datetime import datetime, timezone
from typing import Any, Dict

from udmi.constants import UDMI_VERSION
from udmi.core.managers import DiscoveryManager
from udmi.schema import (
    DiscoveryEvents,
    Entry,
    FamilyDiscoveryConfig,
    FamilyDiscoveryState,
    StreamEvents,
)
from udmi.schema.common import Depth
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase

LOGGER = logging.getLogger("spotter_agent")


class SpotterDiscoveryManager(DiscoveryManager):
    """Unified Discovery Manager for Spotter.

    Extends standard DiscoveryManager to natively handle both active protocol sweeps
    (BACnet, Ether, Passive) and TRACE-level packet streaming (PCAP) over events/stream.
    """

    def __init__(self) -> None:
        super().__init__()
        self._active_trace_threads: Dict[str, threading.Thread] = {}

    def _handle_scan_result(self, device_id: str, event: DiscoveryEvents) -> None:
        LOGGER.info("Discovery event received for device: %s", device_id)
        if not event.timestamp:
            event.timestamp = datetime.now(timezone.utc).isoformat()
        if not event.version:
            event.version = UDMI_VERSION
        self.publish_event(event, "discovery")

    def _should_scan(self, family: str, config: FamilyDiscoveryConfig) -> bool:
        depth_val = getattr(config, "depth", None)
        if depth_val in (Depth.trace, Depth.trace.value):
            state_gen = None
            if family in self._discovery_state.families:
                state_gen = self._discovery_state.families[family].generation
            return bool(config.generation and config.generation != state_gen)
        return super()._should_scan(family, config)

    def _run_scan(self, family: str, provider: Any) -> None:
        fam_config = None
        if self._config and self._config.families:
            fam_config = self._config.families.get(family)

        depth_val = getattr(fam_config, "depth", None) if fam_config else None
        if depth_val in (Depth.trace, Depth.trace.value):
            self._run_trace_capture(family, fam_config)
            return

        super()._run_scan(family, provider)

    def _run_trace_capture(self, family: str, fam_config: Any) -> None:
        """Executes ephemeral PCAP capture and streams chunks over events/stream."""
        f_state = self._discovery_state.families.get(family)
        if not f_state:
            f_state = FamilyDiscoveryState(generation=fam_config.generation)
            self._discovery_state.families[family] = f_state

        f_state.phase = DiscoveryPhase.active
        f_state.status = Entry(
            category="discovery.family",
            level=200,
            message=f"Starting trace capture for family '{family}'...",
        )
        self.trigger_state_update()

        try:
            try:
                from pcap import capture_packets
            except ImportError:
                from edge.spotter.src.pcap import capture_packets

            trace_cfg = getattr(fam_config, "trace", None)
            if isinstance(fam_config, dict):
                trace_cfg = fam_config.get("trace")

            if isinstance(trace_cfg, dict):
                interface = trace_cfg.get("interface") or "any"
                filter_str = trace_cfg.get("filter") or ""
                max_bytes = int(trace_cfg.get("max_bytes") or (10 * 1024 * 1024))
            elif trace_cfg:
                interface = getattr(trace_cfg, "interface", None) or "any"
                filter_str = getattr(trace_cfg, "filter", None) or ""
                max_bytes = int(getattr(trace_cfg, "max_bytes", None) or (10 * 1024 * 1024))
            else:
                interface = getattr(fam_config, "interface", None) or (fam_config.get("interface") if isinstance(fam_config, dict) else None) or "any"
                filter_str = getattr(fam_config, "filter", None) or (fam_config.get("filter") if isinstance(fam_config, dict) else None) or ""
                max_bytes = int(getattr(fam_config, "max_bytes", None) or (fam_config.get("max_bytes") if isinstance(fam_config, dict) else None) or (10 * 1024 * 1024))

            max_duration_sec = int(getattr(fam_config, "scan_duration_sec", None) or (fam_config.get("scan_duration_sec") if isinstance(fam_config, dict) else None) or 60)

            LOGGER.info(
                "Spawning TRACE capture worker on '%s' (filter: '%s', max_duration: %ds, max_bytes: %d)",
                interface,
                filter_str,
                max_duration_sec,
                max_bytes,
            )

            data_generator = capture_packets(
                interface=interface,
                filter_str=filter_str,
                max_duration_sec=max_duration_sec,
                max_bytes=max_bytes,
            )

            captured_chunks = list(data_generator)
            full_data = b"".join(captured_chunks)

            LOGGER.info("Capture complete (%d bytes). Emitting StreamEvents...", len(full_data))
            chunk_size = 128 * 1024  # 128KB chunks
            total_bytes = len(full_data)
            total_chunks = (total_bytes + chunk_size - 1) // chunk_size if total_bytes > 0 else 1
            session_id = f"trace-{family}-{int(time.time())}"

            for idx in range(total_chunks):
                start = idx * chunk_size
                end = min(start + chunk_size, total_bytes)
                chunk_data = full_data[start:end]
                b64_data = base64.b64encode(chunk_data).decode()

                chunk_event = StreamEvents(
                    timestamp=datetime.now(timezone.utc).isoformat(),
                    version=UDMI_VERSION,
                    session_id=session_id,
                    event_no=idx,
                    chunk_index=idx,
                    total_chunks=total_chunks,
                    data=b64_data,
                )
                self.publish_event(chunk_event, "stream")
                LOGGER.info("Published stream chunk %d/%d (event_no: %d, %d bytes)", idx + 1, total_chunks, idx, len(chunk_data))

            f_state.phase = DiscoveryPhase.stopped
            f_state.active_count = total_chunks
            f_state.status = Entry(
                category="discovery.family",
                level=200,
                message=f"Trace capture complete. {total_chunks} stream chunks emitted.",
            )

        except Exception as e:  # pylint: disable=broad-exception-caught
            LOGGER.error("Trace capture failed for family '%s': %s", family, e, exc_info=True)
            f_state.phase = DiscoveryPhase.stopped
            f_state.status = Entry(
                category="discovery.family",
                level=500,
                message=str(e),
            )
        finally:
            self.trigger_state_update()
