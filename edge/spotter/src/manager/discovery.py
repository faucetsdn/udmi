"""Spotter Unified Discovery Manager.

Extends standard DiscoveryManager to natively handle active protocol sweeps
(BACnet, Ether), passive network monitoring (IPv4), self-enumeration, and
TRACE-level packet streaming (PCAP) over upload/discovery.
"""

import base64
import copy
from datetime import datetime, timedelta, timezone
import gzip
import hashlib
import json
import logging
import threading
import time
from typing import Any, Dict, List, Optional, Set

from udmi.constants import UDMI_VERSION
from udmi.core.managers import DiscoveryManager
from udmi.schema import (
    Config,
    DataModel,
    DiscoveryConfig,
    DiscoveryEvents,
    DiscoveryUpload,
    Entry,
    Enumerations,
    FamilyDiscovery,
    FamilyDiscoveryConfig,
    FamilyDiscoveryState,
    FeatureDiscovery,
    ScanManifest,
    State,
)
from udmi.schema.common import Depth, Stage
from udmi.schema.state_discovery_family import Phase as DiscoveryPhase

try:
  from host_telemetry import check_safety_circuit_breaker
  from pcap import (
      capture_packets,
      extract_routed_networks_from_pcap_chunk,
      resolve_gateway_ip,
      run_bacnet_trace_whois_rounds,
  )
except ImportError:
  from edge.spotter.src.host_telemetry import check_safety_circuit_breaker
  from edge.spotter.src.pcap import (
      capture_packets,
      extract_routed_networks_from_pcap_chunk,
      resolve_gateway_ip,
      run_bacnet_trace_whois_rounds,
  )

LOGGER = logging.getLogger("spotter_agent")

# Clock skew tolerance (in seconds) for newly issued 'now' scan generations
GENERATION_PAST_TOLERANCE_SEC = 15.0


def strip_none_fields(obj: Any) -> Any:
  """Recursively strips None values from dictionaries and lists."""
  if isinstance(obj, dict):
    return {
        k: strip_none_fields(v)
        for k, v in obj.items()
        if v is not None
    }
  if isinstance(obj, list):
    return [strip_none_fields(v) for v in obj if v is not None]
  return obj


if not getattr(DataModel, "_spotter_none_stripping_patched", False):
  _orig_to_json = DataModel.to_json

  def _clean_to_json(self: DataModel, *args: Any, **kwargs: Any) -> str:
    raw_json = _orig_to_json(self, *args, **kwargs)
    cleaned = strip_none_fields(json.loads(raw_json))
    return json.dumps(cleaned)

  DataModel.to_json = _clean_to_json  # type: ignore[method-assign]
  setattr(DataModel, "_spotter_none_stripping_patched", True)


def _parse_generation(gen_str: Optional[str]) -> Optional[datetime]:
  """Parses ISO-8601 generation timestamp into timezone-aware datetime."""
  if not gen_str or not isinstance(gen_str, str):
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


def _should_enumerate_depth(depth: Any) -> bool:
  """Returns True if the enumeration depth is 'entries' or 'details'."""
  if depth is None:
    return False
  val = getattr(depth, "value", depth)
  return str(val).lower() in ("entries", "details")


class SpotterDiscoveryManager(DiscoveryManager):
  """Unified Discovery Manager for Spotter.

  Handles active protocol sweeps, passive monitoring, self-enumeration, and
  TRACE packet streaming over upload/discovery with safety circuit breaker checks
  and strict UDMI discovery phase lifecycle tracking (pending/active/stopped).
  """

  def __init__(self, max_mem_pct: float = 85.0) -> None:
    super().__init__()
    self.max_mem_pct = max_mem_pct
    self._start_time = datetime.now(timezone.utc)
    self._active_event_counts: Dict[str, int] = {}
    self._applied_family_configs: Dict[str, Any] = {}
    self._family_providers: Dict[str, Any] = {}
    self._trace_stop_events: Dict[str, threading.Event] = {}

  def _stop_family_scan(self, family: str) -> None:
    """Stops an active provider scan for the specified family, if running."""
    trace_stop = self._trace_stop_events.pop(family, None)
    if trace_stop is not None:
      trace_stop.set()
    provider = self._family_providers.pop(family, None)
    if provider is not None:
      try:
        provider.stop_scan()
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Error stopping provider for '%s': %s", family, err)
      if provider in self._active_providers:
        self._active_providers.remove(provider)

  def _stop_all_family_scans(self) -> None:
    """Stops all currently running family scans."""
    for trace_stop in list(self._trace_stop_events.values()):
      trace_stop.set()
    self._trace_stop_events.clear()
    for family in list(self._family_providers.keys()):
      self._stop_family_scan(family)
    for provider in list(self._active_providers):
      try:
        provider.stop_scan()
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Error stopping active provider: %s", err)
    self._active_providers.clear()

  def _has_registered_provider(self, family: str) -> bool:
    """Checks if a FamilyProvider is registered in LocalnetManager (when wired)."""
    if not self._device or not hasattr(self._device, "get_manager"):
      return True
    # pylint: disable=import-outside-toplevel
    from udmi.core.managers.localnet_manager import LocalnetManager

    localnet = self._device.get_manager(LocalnetManager)
    if not localnet or not hasattr(localnet, "get_provider"):
      return True
    return localnet.get_provider(family) is not None

  def _handle_self_enumeration(
      self, discovery_cfg: DiscoveryConfig
  ) -> bool:
    """Handles self-enumeration triggered by config.discovery.generation."""
    enum_gen = getattr(discovery_cfg, "generation", None)
    if enum_gen is None:
      if self._discovery_state.generation is not None:
        self._discovery_state.generation = None
        return True
      return False

    cur_gen = self._discovery_state.generation
    if enum_gen == cur_gen:
      return False

    enum_dt = _parse_generation(enum_gen)
    cur_dt = _parse_generation(cur_gen)
    if cur_dt is not None and enum_dt is not None and enum_dt <= cur_dt:
      return False

    self._discovery_state.generation = enum_gen
    self._enumerate_capabilities(
        generation=enum_gen,
        depths=getattr(discovery_cfg, "enumerations", None),
    )
    return True

  def _enumerate_capabilities(
      self,
      generation: Optional[str] = None,
      depths: Optional[Enumerations] = None,
  ) -> None:
    """Publishes a self-enumeration DiscoveryEvents payload per UDMI spec."""
    # pylint: disable=import-outside-toplevel
    from udmi.core.managers.localnet_manager import LocalnetManager

    localnet: Optional[LocalnetManager] = None
    if self._device and hasattr(self._device, "get_manager"):
      localnet = self._device.get_manager(LocalnetManager)

    families_map: Optional[Dict[str, FamilyDiscovery]] = None
    features_map: Optional[Dict[str, FeatureDiscovery]] = None
    points_map: Optional[Dict[str, Any]] = None

    if depths is not None:
      if _should_enumerate_depth(getattr(depths, "families", None)):
        reg_families = (
            localnet.get_registered_families() if localnet else []
        )
        families_map = {f: FamilyDiscovery() for f in reg_families}

      if _should_enumerate_depth(getattr(depths, "features", None)):
        features_map = {
            "discovery.scan": FeatureDiscovery(stage=Stage.stable),
            "enumeration": FeatureDiscovery(stage=Stage.stable),
            "enumeration.families": FeatureDiscovery(stage=Stage.stable),
            "enumeration.features": FeatureDiscovery(stage=Stage.stable),
            "system": FeatureDiscovery(stage=Stage.stable),
        }

      if _should_enumerate_depth(getattr(depths, "points", None)):
        points_map = {}

    event = DiscoveryEvents(
        timestamp=_format_generation(datetime.now(timezone.utc)),
        version=UDMI_VERSION,
        generation=generation,
        families=families_map,
        features=features_map,
        points=points_map,
    )
    LOGGER.info(
        "Publishing self-enumeration event (generation: %s)", generation
    )
    self.publish_event(event, "discovery")

  def handle_config(self, config: Config) -> None:
    """Reconciles discovery configuration, self-enumeration, and family scans."""
    if not config or config.discovery is None:
      self._stop_all_family_scans()
      self._config = None
      self._applied_family_configs.clear()
      self._discovery_state.generation = None
      self._discovery_state.families = None
      self.trigger_state_update()
      return

    self._config = config.discovery
    if self._discovery_state.families is None:
      self._discovery_state.families = {}

    state_updated = self._handle_self_enumeration(config.discovery)

    if config.discovery.families is None:
      if self._discovery_state.families:
        self._stop_all_family_scans()
        self._applied_family_configs.clear()
        self._discovery_state.families = {}
        state_updated = True
      if state_updated:
        self.trigger_state_update()
      return

    # Remove families that disappeared from config.discovery.families
    configured_keys = set(config.discovery.families.keys())
    for existing_fam in list(self._discovery_state.families.keys()):
      if existing_fam not in configured_keys:
        self._stop_family_scan(existing_fam)
        self._applied_family_configs.pop(existing_fam, None)
        self._discovery_state.families.pop(existing_fam, None)
        state_updated = True

    now_dt = datetime.now(timezone.utc)
    for family, fam_config in config.discovery.families.items():
      prev_cfg = self._applied_family_configs.get(family)
      f_state = self._discovery_state.families.get(family)

      # Empty or cleared family config stops active/pending scan
      if not fam_config or (
          not getattr(fam_config, "generation", None)
          and not getattr(fam_config, "scan_interval_sec", None)
          and not getattr(fam_config, "passive_sec", None)
      ):
        if f_state and f_state.phase in (
            DiscoveryPhase.active,
            DiscoveryPhase.pending,
        ):
          self._stop_family_scan(family)
          f_state.phase = DiscoveryPhase.stopped
          state_updated = True
        self._applied_family_configs[family] = copy.deepcopy(fam_config)
        continue

      # No-op if family config is unchanged and already tracked in state
      if prev_cfg == fam_config and f_state is not None:
        continue

      # Config changed for this family: stop any currently running scan first
      if f_state and f_state.phase == DiscoveryPhase.active:
        self._stop_family_scan(family)

      prev_state_gen = f_state.generation if f_state else None
      if not f_state:
        f_state = FamilyDiscoveryState()
        self._discovery_state.families[family] = f_state

      self._applied_family_configs[family] = copy.deepcopy(fam_config)

      # Validate provider availability if device LocalnetManager is wired
      if not self._has_registered_provider(family):
        f_state.phase = DiscoveryPhase.stopped
        f_state.generation = getattr(fam_config, "generation", None)
        f_state.status = Entry(
            category="discovery.family.scan",
            level=500,
            message=f"No provider registered for family '{family}'",
            timestamp=_format_generation(now_dt),
        )
        state_updated = True
        continue

      interval_sec = getattr(fam_config, "scan_interval_sec", None)
      duration_sec = getattr(fam_config, "scan_duration_sec", None)
      if (
          interval_sec is not None
          and duration_sec is not None
          and interval_sec > 0
          and duration_sec > interval_sec
      ):
        f_state.phase = DiscoveryPhase.stopped
        f_state.generation = getattr(fam_config, "generation", None)
        f_state.status = Entry(
            category="discovery.family.scan",
            level=500,
            message=(
                f"scan_duration_sec ({duration_sec}) cannot exceed"
                f" scan_interval_sec ({interval_sec})"
            ),
            timestamp=_format_generation(now_dt),
        )
        state_updated = True
        continue

      gen_str = getattr(fam_config, "generation", None)
      passive_sec = getattr(fam_config, "passive_sec", None)

      # Case 1: Purely passive scan (no generation marker)
      if not gen_str and passive_sec and passive_sec > 0:
        f_state.generation = None
        f_state.status = None
        f_state.phase = DiscoveryPhase.pending
        state_updated = True
        continue

      gen_dt = _parse_generation(gen_str)
      if gen_str and gen_dt is None:
        f_state.phase = DiscoveryPhase.stopped
        f_state.generation = gen_str
        f_state.status = Entry(
            category="discovery.family.scan",
            level=500,
            message=f"Invalid generation timestamp: {gen_str!r}",
            timestamp=_format_generation(now_dt),
        )
        state_updated = True
        continue

      # Case 2: Periodic / Recurring scan (generation + scan_interval_sec > 0)
      if interval_sec and interval_sec > 0 and gen_dt:
        f_state.status = None
        if gen_dt > now_dt:
          f_state.generation = gen_str
          f_state.phase = DiscoveryPhase.pending
        else:
          elapsed = (now_dt - gen_dt).total_seconds()
          cycles = int(elapsed // interval_sec)
          target_dt = gen_dt + timedelta(seconds=interval_sec * cycles)
          if (now_dt - target_dt).total_seconds() > GENERATION_PAST_TOLERANCE_SEC:
            target_dt += timedelta(seconds=interval_sec)
          f_state.generation = _format_generation(target_dt)
          f_state.phase = DiscoveryPhase.pending
        state_updated = True
        continue

      # Case 3: Sporadic / One-time active scan
      if gen_dt:
        prev_gen_dt = _parse_generation(prev_state_gen)
        min_allowed_dt = self._start_time - timedelta(
            seconds=GENERATION_PAST_TOLERANCE_SEC
        )
        if gen_dt < min_allowed_dt or (
            prev_gen_dt is not None and gen_dt < prev_gen_dt
        ):
          LOGGER.info(
              "Skipping stale past generation %s for family '%s' (start_time:"
              " %s)",
              gen_str,
              family,
              _format_generation(self._start_time),
          )
          f_state.generation = gen_str
          f_state.phase = DiscoveryPhase.stopped
          f_state.status = None
          state_updated = True
          continue

        f_state.status = None
        f_state.phase = DiscoveryPhase.pending
        f_state.generation = gen_str
        state_updated = True

    if self._scan_wake_event:
      self._scan_wake_event.set()

    if state_updated:
      self.trigger_state_update()

  def update_state(self, state: State) -> None:
    """Contributes discovery state to device state."""
    if self._discovery_state and self._discovery_state.families:
      for fam, provider in list(self._family_providers.items()):
        f_state = self._discovery_state.families.get(fam)
        if f_state is not None:
          passive_holdoff = getattr(provider, "passive_holdoff_count", 0)
          if passive_holdoff:
            f_state.passive_count = passive_holdoff
    if self._discovery_state and (
        self._discovery_state.generation is not None
        or self._discovery_state.families is not None
    ):
      state.discovery = self._discovery_state

  def _get_check_interval(self) -> float:
    """Calculates check interval from pending generations and intervals."""
    min_wait = 60.0
    now_dt = datetime.now(timezone.utc)
    if self._config and self._config.families:
      for family, fam_config in self._config.families.items():
        f_state = (
            self._discovery_state.families.get(family)
            if self._discovery_state.families
            else None
        )
        if f_state and f_state.phase == DiscoveryPhase.pending:
          if f_state.generation:
            target_dt = _parse_generation(f_state.generation)
            if target_dt:
              diff = (target_dt - now_dt).total_seconds()
              if diff > 0:
                min_wait = min(min_wait, diff)
              else:
                min_wait = min(min_wait, 1.0)
          else:
            min_wait = min(min_wait, 1.0)
        if (
            fam_config
            and fam_config.scan_interval_sec
            and fam_config.scan_interval_sec > 0
        ):
          min_wait = min(min_wait, float(fam_config.scan_interval_sec))
    return max(0.5, min_wait)

  def _update_family_state(self, family: str, active: bool) -> None:
    """Updates family UDMI discovery phase and schedules next periodic cycle."""
    if self._discovery_state.families is None:
      self._discovery_state.families = {}
    if family not in self._discovery_state.families:
      self._discovery_state.families[family] = FamilyDiscoveryState()

    f_state = self._discovery_state.families[family]

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
        # Schedule next recurring generation per UDMI specification (Ts = Tc + N*Ti)
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
    del device_id  # Events are published to the local discovery node's topic.
    LOGGER.info(
        "Discovery event received for device: %s",
        event.addr or event.network or "marker",
    )
    if not event.timestamp:
      event.timestamp = _format_generation(datetime.now(timezone.utc))
    if not event.version:
      event.version = UDMI_VERSION

    fam = event.family
    if fam and self._discovery_state.families:
      f_state = self._discovery_state.families.get(fam)
      if f_state and f_state.generation and event.generation != f_state.generation:
        event.generation = f_state.generation

      provider = self._family_providers.get(fam)
      if provider is not None and f_state is not None:
        passive_holdoff = getattr(provider, "passive_holdoff_count", 0)
        if passive_holdoff:
          f_state.passive_count = passive_holdoff

      if event.event_no is not None:
        if event.event_no > 0:
          self._active_event_counts[fam] = event.event_no
          if f_state:
            f_state.active_count = self._active_event_counts[fam]
            self.trigger_state_update()
      else:
        self._active_event_counts[fam] = (
            self._active_event_counts.get(fam, 0) + 1
        )
        if f_state:
          f_state.active_count = self._active_event_counts[fam]
          self.trigger_state_update()

    self.publish_event(event, "discovery")

  def _should_scan(self, family: str, config: FamilyDiscoveryConfig) -> bool:
    """Determines whether a discovery scan is due for the given family."""
    if not config:
      return False
    if self._discovery_state.families is None:
      self._discovery_state.families = {}
    f_state = self._discovery_state.families.get(family)
    if not f_state:
      f_state = FamilyDiscoveryState()
      self._discovery_state.families[family] = f_state

    if f_state.phase == DiscoveryPhase.active:
      return False

    if (
        f_state.status
        and f_state.status.level
        and f_state.status.level >= 500
        and f_state.phase == DiscoveryPhase.stopped
    ):
      return False

    gen_str = getattr(config, "generation", None)
    passive_sec = getattr(config, "passive_sec", None)
    now_dt = datetime.now(timezone.utc)
    gen_dt = _parse_generation(gen_str)
    state_gen = getattr(f_state, "generation", None) if f_state else None
    state_gen_dt = _parse_generation(state_gen)

    # Purely passive scan (no generation marker)
    if not gen_str and passive_sec and passive_sec > 0:
      return f_state.phase != DiscoveryPhase.stopped

    # Periodic / Recurring scan
    interval_sec = getattr(config, "scan_interval_sec", None)
    if interval_sec and interval_sec > 0:
      if f_state and f_state.phase == DiscoveryPhase.pending and state_gen_dt:
        return now_dt >= state_gen_dt
      elif not state_gen_dt:
        if gen_dt and gen_dt > now_dt:
          f_state.generation = gen_str
          f_state.phase = DiscoveryPhase.pending
          return False
        elif gen_dt:
          elapsed = (now_dt - gen_dt).total_seconds()
          cycles = int(elapsed // interval_sec)
          target_dt = gen_dt + timedelta(seconds=interval_sec * cycles)
          if (now_dt - target_dt).total_seconds() > GENERATION_PAST_TOLERANCE_SEC:
            target_dt += timedelta(seconds=interval_sec)
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
    if not gen_str or not gen_dt:
      return False
    if f_state.phase == DiscoveryPhase.stopped and gen_str == state_gen:
      return False

    # Reject stale past generations prior to device start window
    min_allowed_dt = self._start_time - timedelta(
        seconds=GENERATION_PAST_TOLERANCE_SEC
    )
    if gen_dt < min_allowed_dt and f_state.phase != DiscoveryPhase.pending:
      f_state.generation = gen_str
      f_state.phase = DiscoveryPhase.stopped
      return False

    if gen_dt > now_dt:
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
    if self._discovery_state.families is None:
      self._discovery_state.families = {}

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
      f_state.phase = DiscoveryPhase.stopped
      f_state.status = Entry(
          category="discovery.family.scan",
          level=400,
          message=(
              f"Scan throttled by safety circuit breaker (memory usage exceeds"
              f" {self.max_mem_pct}%)"
          ),
          timestamp=_format_generation(datetime.now(timezone.utc)),
      )
      if provider in self._active_providers:
        self._active_providers.remove(provider)
      self._family_providers.pop(family, None)
      self._update_family_state(family, False)
      self.trigger_state_update(immediate=True)
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
        self._family_providers.pop(family, None)
        self._update_family_state(family, False)
        self.trigger_state_update(immediate=True)
      return

    # Standard Protocol Scan
    f_state = self._discovery_state.families.get(family)
    if not f_state:
      f_state = FamilyDiscoveryState()
      self._discovery_state.families[family] = f_state

    if (
        not f_state.generation
        and fam_config
        and getattr(fam_config, "generation", None)
    ):
      f_state.generation = fam_config.generation

    f_state.phase = DiscoveryPhase.active
    f_state.active_count = 0
    self._active_event_counts[family] = 0
    self._family_providers[family] = provider
    self.trigger_state_update(immediate=True)

    # Build effective scan config carrying the current loop generation and duration
    effective_config = copy.copy(fam_config) if fam_config else None
    if effective_config is not None:
      if f_state.generation:
        effective_config.generation = f_state.generation
      if (
          getattr(effective_config, "scan_duration_sec", None) is None
          and getattr(effective_config, "scan_interval_sec", None)
      ):
        effective_config.scan_duration_sec = effective_config.scan_interval_sec

    LOGGER.info(
        "Scanning family '%s' (generation: %s)...", family, f_state.generation
    )
    try:
      provider.start_scan(effective_config, self._handle_scan_result)
      LOGGER.info("Scan finished for '%s'.", family)
      f_state.status = None
      passive_holdoff = getattr(provider, "passive_holdoff_count", 0)
      if passive_holdoff:
        f_state.passive_count = passive_holdoff
    except Exception as e:  # pylint: disable=broad-exception-caught
      LOGGER.error("Scan failed for '%s': %s", family, e, exc_info=True)
      err_msg = str(e).strip() or type(e).__name__
      f_state.status = Entry(
          category="discovery.family.scan",
          level=500,
          message=err_msg,
          timestamp=_format_generation(datetime.now(timezone.utc)),
      )
    finally:
      if provider in self._active_providers:
        self._active_providers.remove(provider)
      self._family_providers.pop(family, None)
      self._update_family_state(family, False)
      self.trigger_state_update(immediate=True)

  def _run_trace_capture(self, family: str, fam_config: Any) -> None:
    """Executes PCAP capture, runs raw-socket BACnet Who-Is rounds, and streams chunks over upload/discovery."""
    if self._discovery_state.families is None:
      self._discovery_state.families = {}
    f_state = self._discovery_state.families.get(family)
    if not f_state:
      f_state = FamilyDiscoveryState()
      self._discovery_state.families[family] = f_state

    if check_safety_circuit_breaker(self.max_mem_pct):
      LOGGER.warning(
          "Safety circuit breaker active: Throttling trace capture for '%s'.",
          family,
      )
      f_state.phase = DiscoveryPhase.stopped
      f_state.status = Entry(
          category="discovery.family.scan",
          level=400,
          message="Trace capture throttled by safety circuit breaker",
          timestamp=_format_generation(datetime.now(timezone.utc)),
      )
      return

    target_gen = None
    if fam_config:
      target_gen = getattr(fam_config, "generation", None) or (
          fam_config.get("generation") if isinstance(fam_config, dict) else None
      )
    if target_gen:
      f_state.generation = target_gen
    if not f_state.generation:
      f_state.generation = _format_generation(datetime.now(timezone.utc))
    generation = f_state.generation

    f_state.phase = DiscoveryPhase.active
    f_state.active_count = 0
    f_state.status = Entry(
        category="discovery.family.scan",
        level=200,
        message=f"Starting trace capture for family '{family}'...",
        timestamp=_format_generation(datetime.now(timezone.utc)),
    )
    self.trigger_state_update(immediate=True)

    whois_stop_event = threading.Event()
    self._trace_stop_events[family] = whois_stop_event
    whois_thread: Optional[threading.Thread] = None

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
        interface = "any"
        filter_str = ""
        max_bytes = 10 * 1024 * 1024

      if not filter_str and family == "bacnet":
        filter_str = "udp port 47808"

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

      # 1. Emit start marker (event_no: 0) over upload/discovery
      start_msg = DiscoveryUpload(
          timestamp=_format_generation(datetime.now(timezone.utc)),
          version=UDMI_VERSION,
          generation=generation,
          family=family,
          event_no=0,
      )
      self.publish_upload(start_msg, "discovery")

      discovered_routed_networks: Set[int] = set()
      discovered_routers: Set[str] = set()
      scan_round_timestamps: List[str] = []
      gateway_ip = resolve_gateway_ip(interface)

      # 2. For BACnet trace, launch paced raw-socket Who-Is discovery rounds
      if family == "bacnet":
        whois_thread = threading.Thread(
            target=run_bacnet_trace_whois_rounds,
            kwargs={
                "discovered_routed_networks": discovered_routed_networks,
                "scan_round_timestamps": scan_round_timestamps,
                "stop_event": whois_stop_event,
                "max_duration_sec": max_duration_sec,
                "interface": interface,
                "src_ip": gateway_ip,
                "discovered_routers": discovered_routers,
            },
            name="BACnet-Trace-WhoIs",
            daemon=True,
        )
        whois_thread.start()

      # 3. Stream compressed PCAP chunks incrementally over upload/discovery
      chunk_size = 128 * 1024  # 128KB raw chunk threshold before gzip
      pending_buf = bytearray()
      sha256_hasher = hashlib.sha256()
      chunk_count = 0
      total_bytes = 0
      circuit_breaker_tripped = False

      def _emit_compressed_chunk(raw_bytes: bytes) -> None:
        nonlocal chunk_count
        sha256_hasher.update(raw_bytes)
        compressed = gzip.compress(raw_bytes)
        b64_data = base64.b64encode(compressed).decode("ascii")
        chunk_count += 1
        chunk_msg = DiscoveryUpload(
            timestamp=_format_generation(datetime.now(timezone.utc)),
            version=UDMI_VERSION,
            generation=generation,
            family=family,
            event_no=chunk_count,
            data=b64_data,
        )
        self.publish_upload(chunk_msg, "discovery")
        f_state.active_count = chunk_count
        self.trigger_state_update()
        LOGGER.info(
            "Published upload/discovery chunk %d (event_no: %d, %d raw bytes ->"
            " %d gzip bytes)",
            chunk_count,
            chunk_count,
            len(raw_bytes),
            len(compressed),
        )

      data_generator = capture_packets(
          interface=interface,
          filter_str=filter_str,
          max_duration_sec=max_duration_sec,
          max_bytes=max_bytes,
          stop_event=whois_stop_event,
      )

      try:
        for chunk in data_generator:
          if check_safety_circuit_breaker(self.max_mem_pct):
            LOGGER.warning(
                "Safety circuit breaker triggered during trace capture. Halting"
                " capture loop."
            )
            circuit_breaker_tripped = True
            break
          if not chunk:
            continue
          total_bytes += len(chunk)
          if family == "bacnet":
            routed = extract_routed_networks_from_pcap_chunk(
                chunk, discovered_routers=discovered_routers
            )
            if routed:
              discovered_routed_networks.update(routed)
          pending_buf.extend(chunk)
          while len(pending_buf) >= chunk_size:
            slice_bytes = bytes(pending_buf[:chunk_size])
            del pending_buf[:chunk_size]
            _emit_compressed_chunk(slice_bytes)
      finally:
        close_fn = getattr(data_generator, "close", None)
        if callable(close_fn):
          close_fn()

      if pending_buf:
        _emit_compressed_chunk(bytes(pending_buf))
        pending_buf.clear()

      whois_stop_event.set()
      if whois_thread and whois_thread.is_alive():
        whois_thread.join(timeout=2.0)

      if circuit_breaker_tripped and chunk_count == 0:
        f_state.phase = DiscoveryPhase.stopped
        f_state.active_count = 0
        f_state.status = Entry(
            category="discovery.family.scan",
            level=400,
            message="Trace capture throttled by safety circuit breaker",
            timestamp=_format_generation(datetime.now(timezone.utc)),
        )
        return

      if total_bytes == 0:
        raise RuntimeError(
            f"Packet capture on '{interface}' (filter: '{filter_str}') produced"
            " 0 bytes"
        )

      # 4. Emit mandatory EOF marker (event_no: -(N + 1)) with sha256 and scan_manifest
      digest = sha256_hasher.hexdigest()
      manifest = ScanManifest(
          gateway_ip=gateway_ip,
          interface=interface,
          filter=filter_str,
          scan_round_timestamps=scan_round_timestamps,
          discovered_routed_networks=sorted(discovered_routed_networks),
      )
      eof_msg = DiscoveryUpload(
          timestamp=_format_generation(datetime.now(timezone.utc)),
          version=UDMI_VERSION,
          generation=generation,
          family=family,
          event_no=-(chunk_count + 1),
          sha256=digest,
          scan_manifest=manifest,
      )
      self.publish_upload(eof_msg, "discovery")
      LOGGER.info(
          "Capture complete (%d bytes, %d chunks, sha256=%s).",
          total_bytes,
          chunk_count,
          digest,
      )

      f_state.phase = DiscoveryPhase.stopped
      f_state.active_count = chunk_count
      if circuit_breaker_tripped:
        f_state.status = Entry(
            category="discovery.family.scan",
            level=400,
            message=(
                "Trace capture throttled by safety circuit breaker after"
                f" {chunk_count} upload/discovery chunks."
            ),
            timestamp=_format_generation(datetime.now(timezone.utc)),
        )
      else:
        f_state.status = Entry(
            category="discovery.family.scan",
            level=200,
            message=(
                f"Trace capture complete. {chunk_count} upload/discovery chunks"
                " emitted."
            ),
            timestamp=_format_generation(datetime.now(timezone.utc)),
        )

    except Exception as e:  # pylint: disable=broad-exception-caught
      whois_stop_event.set()
      if whois_thread and whois_thread.is_alive():
        whois_thread.join(timeout=1.0)
      LOGGER.error(
          "Trace capture failed for family '%s': %s", family, e, exc_info=True
      )
      f_state.phase = DiscoveryPhase.stopped
      f_state.status = Entry(
          category="discovery.family.scan",
          level=500,
          message=str(e),
          timestamp=_format_generation(datetime.now(timezone.utc)),
      )
    finally:
      self._trace_stop_events.pop(family, None)
      self.trigger_state_update()
