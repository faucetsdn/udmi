"""Spotter Unified Discovery Manager.

Extends standard DiscoveryManager to natively handle both active protocol sweeps
(BACnet, Ether, Passive) and TRACE-level packet streaming (PCAP) over
events/streams.
"""

import base64
from datetime import datetime, timedelta, timezone
import logging
import time
from typing import Any, Dict, Optional

from udmi.constants import UDMI_VERSION
from udmi.core.managers import DiscoveryManager
from udmi.schema import (
    Config,
    DiscoveryEvents,
    Entry,
    FamilyDiscoveryConfig,
    FamilyDiscoveryState,
    StreamsEvents,
)
from udmi.schema.common import Depth
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase

try:
  from host_telemetry import check_safety_circuit_breaker
  from pcap import capture_packets
except ImportError:
  from edge.spotter.src.host_telemetry import check_safety_circuit_breaker
  from edge.spotter.src.pcap import capture_packets

LOGGER = logging.getLogger("spotter_agent")


def _parse_generation(gen_str: Optional[str]) -> Optional[datetime]:
  """Parses ISO-8601 generation timestamp into timezone-aware datetime."""
  if not gen_str:
    return None
  try:
    clean_str = gen_str.replace("Z", "+00:00")
    dt = datetime.fromisoformat(clean_str)
    if dt.tzinfo is None:
      dt = dt.replace(tzinfo=timezone.utc)
    return dt
  except (ValueError, TypeError):
    return None


def _format_generation(dt: datetime) -> str:
  """Formats datetime into UDMI-compliant RFC 3339 UTC string."""
  if dt.tzinfo is None:
    dt = dt.replace(tzinfo=timezone.utc)
  else:
    dt = dt.astimezone(timezone.utc)
  return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


class SpotterDiscoveryManager(DiscoveryManager):
  """Unified Discovery Manager for Spotter.

  Extends standard DiscoveryManager to handle active protocol sweeps and
  TRACE packet streaming over events/streams with safety circuit breaker checks
  and UDMI discovery phase lifecycle tracking (pending/active/stopped).
  """

  def __init__(self, max_mem_pct: float = 85.0) -> None:
    super().__init__()
    self.max_mem_pct = max_mem_pct
    self._active_event_counts: Dict[str, int] = {}

  def handle_config(self, config: Config) -> None:
    """Handles discovery config and marks upcoming generations as pending."""
    super().handle_config(config)
    if not self._config or not self._config.families:
      return

    now_dt = datetime.now(timezone.utc)
    state_updated = False
    for family, fam_config in self._config.families.items():
      f_state = self._discovery_state.families.get(family)
      state_gen = f_state.generation if f_state else None
      if (
          fam_config
          and fam_config.generation
          and fam_config.generation != state_gen
      ):
        if not f_state:
          f_state = FamilyDiscoveryState()
          self._discovery_state.families[family] = f_state
        if (
            getattr(f_state, "phase", None) != DiscoveryPhase.active
            and not getattr(f_state, "active", False)
        ):
          gen_dt = _parse_generation(fam_config.generation)
          interval_sec = getattr(fam_config, "scan_interval_sec", None)
          if gen_dt and gen_dt > now_dt:
            f_state.phase = DiscoveryPhase.pending
            f_state.generation = fam_config.generation
            state_updated = True
          elif interval_sec and interval_sec > 0 and gen_dt:
            elapsed = (now_dt - gen_dt).total_seconds()
            cycles = int(elapsed // interval_sec)
            target_dt = gen_dt + timedelta(seconds=interval_sec * cycles)
            if (now_dt - target_dt).total_seconds() > 10.0:
              target_dt += timedelta(seconds=interval_sec)
            f_state.generation = _format_generation(target_dt)
            f_state.phase = DiscoveryPhase.pending
            state_updated = True
          else:
            f_state.phase = DiscoveryPhase.pending
            f_state.generation = fam_config.generation
            state_updated = True

    if state_updated:
      self.trigger_state_update()

  def _get_check_interval(self) -> float:
    """Calculates check interval from pending generations and intervals."""
    min_wait = 60.0
    now_dt = datetime.now(timezone.utc)
    if self._config and self._config.families:
      for family, fam_config in self._config.families.items():
        f_state = self._discovery_state.families.get(family)
        if (
            f_state
            and f_state.phase == DiscoveryPhase.pending
            and f_state.generation
        ):
          target_dt = _parse_generation(f_state.generation)
          if target_dt:
            diff = (target_dt - now_dt).total_seconds()
            if diff > 0:
              min_wait = min(min_wait, diff)
            else:
              min_wait = min(min_wait, 1.0)
        if (
            fam_config
            and fam_config.scan_interval_sec
            and fam_config.scan_interval_sec > 0
        ):
          min_wait = min(min_wait, float(fam_config.scan_interval_sec))
    return max(1.0, min_wait)

  def _update_family_state(self, family: str, active: bool) -> None:
    """Updates family active flag and sets UDMI discovery phase."""
    if family not in self._discovery_state.families:
      self._discovery_state.families[family] = FamilyDiscoveryState()

    f_state = self._discovery_state.families[family]
    f_state.active = active

    if active:
      f_state.phase = DiscoveryPhase.active
    else:
      fam_config = None
      if self._config and self._config.families:
        fam_config = self._config.families.get(family)
      interval_sec = (
          getattr(fam_config, "scan_interval_sec", None)
          if fam_config
          else None
      )
      is_error = (
          f_state.status
          and f_state.status.level
          and f_state.status.level >= 500
      )

      if interval_sec and interval_sec > 0 and not is_error:
        # Schedule next recurring generation per UDMI specification
        current_dt = _parse_generation(f_state.generation) or datetime.now(
            timezone.utc
        )
        next_dt = current_dt + timedelta(seconds=interval_sec)
        f_state.generation = _format_generation(next_dt)
        f_state.phase = DiscoveryPhase.pending
        f_state.active_count = 0
      else:
        f_state.phase = DiscoveryPhase.stopped
        if fam_config and fam_config.generation and not f_state.generation:
          f_state.generation = fam_config.generation

  def _handle_scan_result(self, device_id: str, event: DiscoveryEvents) -> None:
    """Handles discovered device event, tracks active count, and publishes.

    Args:
        device_id: Discovered device address or identifier.
        event: Discovered entity event payload.
    """
    del device_id  # Unused; events are published to the local gateway topic.
    LOGGER.info(
        "Discovery event received for device: %s", event.addr or "unknown"
    )
    if not event.timestamp:
      event.timestamp = datetime.now(timezone.utc).isoformat()
    if not event.version:
      event.version = UDMI_VERSION

    # Track active count per family (ignoring boundary markers: event_no <= 0)
    fam = event.family
    if fam:
      if event.event_no is not None:
        if event.event_no > 0:
          self._active_event_counts[fam] = event.event_no
          f_state = self._discovery_state.families.get(fam)
          if f_state:
            f_state.active_count = self._active_event_counts[fam]
            self.trigger_state_update()
      else:
        self._active_event_counts[fam] = (
            self._active_event_counts.get(fam, 0) + 1
        )
        f_state = self._discovery_state.families.get(fam)
        if f_state:
          f_state.active_count = self._active_event_counts[fam]
          self.trigger_state_update()

    self.publish_event(event, "discovery")

  def _should_scan(self, family: str, config: FamilyDiscoveryConfig) -> bool:
    """Determines whether a discovery scan is due for the given family."""
    f_state = self._discovery_state.families.get(family)
    if not f_state:
      f_state = FamilyDiscoveryState()
      self._discovery_state.families[family] = f_state

    if (
        f_state.phase == DiscoveryPhase.active
        or getattr(f_state, "active", False)
    ):
      return False

    gen_str = getattr(config, "generation", None)
    now_dt = datetime.now(timezone.utc)
    gen_dt = _parse_generation(gen_str)
    state_gen = getattr(f_state, "generation", None) if f_state else None
    state_gen_dt = _parse_generation(state_gen)

    # Standard Protocol sweeps (BACnet, Ether, IPv4)
    interval_sec = getattr(config, "scan_interval_sec", None)
    if interval_sec and interval_sec > 0:
      # Recurring / Periodic scan
      if f_state and f_state.phase == DiscoveryPhase.pending and state_gen_dt:
        return now_dt >= state_gen_dt
      elif not state_gen_dt:
        if gen_dt and gen_dt > now_dt:
          if f_state:
            f_state.generation = gen_str
            f_state.phase = DiscoveryPhase.pending
          return False
        elif gen_dt:
          elapsed = (now_dt - gen_dt).total_seconds()
          cycles = int(elapsed // interval_sec)
          target_dt = gen_dt + timedelta(seconds=interval_sec * cycles)
          if (now_dt - target_dt).total_seconds() > 10.0:
            target_dt += timedelta(seconds=interval_sec)
          if f_state:
            f_state.generation = _format_generation(target_dt)
            f_state.phase = DiscoveryPhase.pending
          return now_dt >= target_dt
        else:
          last_run = self._last_scan_times.get(family, 0)
          return (time.time() - last_run) >= interval_sec
      else:
        last_run = self._last_scan_times.get(family, 0)
        return (time.time() - last_run) >= interval_sec

    # Sporadic / One-off scan
    if not gen_str:
      return False
    if (
        gen_str == state_gen
        and f_state
        and f_state.phase == DiscoveryPhase.stopped
    ):
      return False
    if gen_dt and gen_dt > now_dt:
      if f_state:
        f_state.phase = DiscoveryPhase.pending
        f_state.generation = gen_str
      return False
    return True

  def _run_scan(self, family: str, provider: Any) -> None:
    """Executes a protocol family scan with safety circuit breaker protection.

    Args:
        family: Discovery family identifier (e.g. 'bacnet', 'ether', 'ipv4').
        provider: FamilyProvider instance to execute the scan.
    """
    if check_safety_circuit_breaker(self.max_mem_pct):
      LOGGER.warning(
          "Safety circuit breaker active: Host memory exceeds %.1f%% threshold."
          " Throttling scan for family '%s'.",
          self.max_mem_pct,
          family,
      )
      f_state = self._discovery_state.families.get(family)
      if not f_state:
        f_state = FamilyDiscoveryState()
        self._discovery_state.families[family] = f_state
      f_state.active = False
      f_state.phase = DiscoveryPhase.stopped
      f_state.status = Entry(
          category="discovery.family",
          level=400,
          message=(
              f"Scan throttled by safety circuit breaker (memory usage exceeds"
              f" {self.max_mem_pct}%)"
          ),
      )
      if provider in self._active_providers:
        self._active_providers.remove(provider)
      self._update_family_state(family, False)
      self.trigger_state_update()
      return

    fam_config = None
    if self._config and self._config.families:
      fam_config = self._config.families.get(family)

    depth_val = getattr(fam_config, "depth", None) if fam_config else None
    if depth_val in (Depth.trace, Depth.trace.value):
      try:
        self._run_trace_capture(family, fam_config)
      finally:
        if provider in self._active_providers:
          self._active_providers.remove(provider)
        self._update_family_state(family, False)
        self.trigger_state_update()
      return

    # Standard Protocol Scan
    f_state = self._discovery_state.families.get(family)
    if not f_state:
      f_state = FamilyDiscoveryState()
      self._discovery_state.families[family] = f_state

    f_state.active = True
    f_state.phase = DiscoveryPhase.active
    f_state.active_count = 0
    self._active_event_counts[family] = 0
    self.trigger_state_update()

    LOGGER.info("Scanning family '%s'...", family)
    try:
      provider.start_scan(fam_config, self._handle_scan_result)
      LOGGER.info("Scan finished for '%s'.", family)
      f_state.status = None
    except Exception as e:  # pylint: disable=broad-exception-caught
      LOGGER.error("Scan failed for '%s': %s", family, e, exc_info=True)
      err_msg = str(e).strip() or type(e).__name__
      f_state.status = Entry(
          category="discovery.error",
          level=500,
          message=err_msg,
      )
    finally:
      if provider in self._active_providers:
        self._active_providers.remove(provider)
      self._update_family_state(family, False)
      self.trigger_state_update()

  def _run_trace_capture(self, family: str, fam_config: Any) -> None:
    """Executes PCAP capture and streams chunks over events/streams."""
    f_state = self._discovery_state.families.get(family)
    if not f_state:
      f_state = FamilyDiscoveryState()
      self._discovery_state.families[family] = f_state

    if check_safety_circuit_breaker(self.max_mem_pct):
      LOGGER.warning(
          "Safety circuit breaker active: Throttling trace capture for '%s'.",
          family,
      )
      f_state.active = False
      f_state.phase = DiscoveryPhase.stopped
      f_state.status = Entry(
          category="discovery.family",
          level=400,
          message="Trace capture throttled by safety circuit breaker",
      )
      return

    target_gen = None
    if fam_config:
      target_gen = getattr(fam_config, "generation", None) or (
          fam_config.get("generation") if isinstance(fam_config, dict) else None
      )
    if target_gen:
      f_state.generation = target_gen

    f_state.active = True
    f_state.phase = DiscoveryPhase.active
    f_state.status = Entry(
        category="discovery.family",
        level=200,
        message=f"Starting trace capture for family '{family}'...",
    )
    self.trigger_state_update()

    try:
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
        max_bytes = int(
            getattr(trace_cfg, "max_bytes", None) or (10 * 1024 * 1024)
        )
      else:
        interface = (
            getattr(fam_config, "interface", None)
            or (
                fam_config.get("interface")
                if isinstance(fam_config, dict)
                else None
            )
            or "any"
        )
        filter_str = (
            getattr(fam_config, "filter", None)
            or (
                fam_config.get("filter")
                if isinstance(fam_config, dict)
                else None
            )
            or ""
        )
        max_bytes = int(
            getattr(fam_config, "max_bytes", None)
            or (
                fam_config.get("max_bytes")
                if isinstance(fam_config, dict)
                else None
            )
            or (10 * 1024 * 1024)
        )

      max_duration_sec = int(
          getattr(fam_config, "scan_duration_sec", None)
          or (
              fam_config.get("scan_duration_sec")
              if isinstance(fam_config, dict)
              else None
          )
          or 60
      )

      LOGGER.info(
          "Spawning TRACE capture worker on '%s' (filter: '%s', max_duration:"
          " %ds, max_bytes: %d)",
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

      captured_chunks = []
      for chunk in data_generator:
        if check_safety_circuit_breaker(self.max_mem_pct):
          LOGGER.warning(
              "Safety circuit breaker triggered during trace capture. Halting"
              " capture loop."
          )
          break
        captured_chunks.append(chunk)
      full_data = b"".join(captured_chunks)

      LOGGER.info(
          "Capture complete (%d bytes). Emitting StreamsEvents...",
          len(full_data),
      )
      chunk_size = 128 * 1024  # 128KB chunks
      total_bytes = len(full_data)
      total_chunks = (
          (total_bytes + chunk_size - 1) // chunk_size if total_bytes > 0 else 1
      )
      session_id = f"trace-{family}-{int(time.time())}"

      for idx in range(total_chunks):
        start = idx * chunk_size
        end = min(start + chunk_size, total_bytes)
        chunk_data = full_data[start:end]
        b64_data = base64.b64encode(chunk_data).decode()

        chunk_event = StreamsEvents(
            timestamp=datetime.now(timezone.utc).isoformat(),
            version=UDMI_VERSION,
            session_id=session_id,
            event_no=idx,
            chunk_index=idx,
            total_chunks=total_chunks,
            data=b64_data,
        )
        self.publish_event(chunk_event, "streams")
        LOGGER.info(
            "Published stream chunk %d/%d (event_no: %d, %d bytes)",
            idx + 1,
            total_chunks,
            idx,
            len(chunk_data),
        )

      f_state.phase = DiscoveryPhase.stopped
      f_state.active_count = total_chunks
      f_state.status = Entry(
          category="discovery.family",
          level=200,
          message=(
              f"Trace capture complete. {total_chunks} stream chunks emitted."
          ),
      )

    except Exception as e:  # pylint: disable=broad-exception-caught
      LOGGER.error(
          "Trace capture failed for family '%s': %s", family, e, exc_info=True
      )
      f_state.phase = DiscoveryPhase.stopped
      f_state.status = Entry(
          category="discovery.family",
          level=500,
          message=str(e),
      )
    finally:
      f_state.active = False
      self.trigger_state_update()

