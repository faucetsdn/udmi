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
import re
import threading
import time
from typing import Any, Callable, Dict, FrozenSet, Iterable, List, Optional, Set, Tuple

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
      build_trace_bpf_filter,
      capture_packets,
      check_raw_socket_permission,
      extract_bacnet_trace_observations,
      resolve_gateway_ip,
      run_bacnet_trace_whois_rounds,
      validate_trace_interface,
  )
except ImportError:
  from edge.spotter.src.host_telemetry import check_safety_circuit_breaker
  from edge.spotter.src.pcap import (
      build_trace_bpf_filter,
      capture_packets,
      check_raw_socket_permission,
      extract_bacnet_trace_observations,
      resolve_gateway_ip,
      run_bacnet_trace_whois_rounds,
      validate_trace_interface,
  )

LOGGER = logging.getLogger("spotter_agent")

# Clock skew tolerance (in seconds) for newly issued 'now' scan generations
GENERATION_PAST_TOLERANCE_SEC = 15.0
VALID_DISCOVERY_MODES = frozenset(("full", "capture_only"))
DEFAULT_TRACE_CHUNK_FLUSH_SEC = 30.0
SCAN_NAME_RE = re.compile(r"^[a-z][a-z0-9]*(_[a-z0-9]+)*$")
DEFAULT_EXCLUSIVE_FAMILIES: FrozenSet[str] = frozenset(("bacnet",))
ScanKey = Tuple[str, str]  # ("families", family) or ("scans", scan_name)


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

  def __init__(
      self,
      max_mem_pct: float = 85.0,
      discovery_mode: str = "full",
      trace_flush_interval_sec: float = DEFAULT_TRACE_CHUNK_FLUSH_SEC,
      provider_factories: Optional[Dict[str, Callable[[], Any]]] = None,
      exclusive_families: Optional[Iterable[str]] = None,
  ) -> None:
    super().__init__()
    mode_norm = str(discovery_mode or "full").strip().lower()
    if mode_norm not in VALID_DISCOVERY_MODES:
      raise ValueError(
          f"Invalid udmi.discovery.mode {discovery_mode!r}: expected one of"
          f" {sorted(VALID_DISCOVERY_MODES)}"
      )
    self.max_mem_pct = max_mem_pct
    self.discovery_mode = mode_norm
    self.trace_flush_interval_sec = float(trace_flush_interval_sec)
    self._provider_factories: Dict[str, Callable[[], Any]] = dict(
        provider_factories or {}
    )
    self._exclusive_families: FrozenSet[str] = (
        frozenset(exclusive_families)
        if exclusive_families is not None
        else DEFAULT_EXCLUSIVE_FAMILIES
    )
    self._family_locks: Dict[str, threading.Lock] = {}
    self._family_locks_guard = threading.Lock()
    self._start_time = datetime.now(timezone.utc)
    self._active_event_counts: Dict[str, int] = {}
    self._applied_family_configs: Dict[str, Any] = {}
    self._family_providers: Dict[str, Any] = {}
    self._trace_stop_events: Dict[str, threading.Event] = {}
    self._scan_event_counts: Dict[ScanKey, int] = {}
    self._applied_scan_configs: Dict[ScanKey, Any] = {}
    self._scan_providers: Dict[ScanKey, Any] = {}
    self._scan_trace_stop_events: Dict[ScanKey, threading.Event] = {}
    self._scan_last_times: Dict[ScanKey, float] = {}

  @staticmethod
  def _log_prefix(scan_key: ScanKey) -> str:
    """Formats a per-scan log prefix ([families/<fam>] or [scans/<name>])."""
    return f"[{scan_key[0]}/{scan_key[1]}]"

  def _get_family_lock(self, family: str) -> threading.Lock:
    """Returns the process-wide exclusivity lock for the specified family."""
    with self._family_locks_guard:
      lock = self._family_locks.get(family)
      if lock is None:
        lock = threading.Lock()
        self._family_locks[family] = lock
      return lock

  def _get_state_map(
      self, section: str, create: bool = False
  ) -> Optional[Dict[str, FamilyDiscoveryState]]:
    """Returns the discovery state dictionary for 'families' or 'scans'."""
    if section == "scans":
      if self._discovery_state.scans is None and create:
        self._discovery_state.scans = {}
      return self._discovery_state.scans
    if self._discovery_state.families is None and create:
      self._discovery_state.families = {}
    return self._discovery_state.families

  def _get_config_map(
      self, section: str
  ) -> Optional[Dict[str, FamilyDiscoveryConfig]]:
    """Returns the discovery config dictionary for 'families' or 'scans'."""
    if not self._config:
      return None
    if section == "scans":
      return getattr(self._config, "scans", None)
    return getattr(self._config, "families", None)

  def _resolve_provider_for_scan(self, family: str) -> Optional[Any]:
    """Creates an isolated provider instance via factory if available, else LocalnetManager."""
    factory = self._provider_factories.get(family)
    if callable(factory):
      return factory()
    if not self._device or not hasattr(self._device, "get_manager"):
      return None
    # pylint: disable=import-outside-toplevel
    from udmi.core.managers.localnet_manager import LocalnetManager

    localnet = self._device.get_manager(LocalnetManager)
    if not localnet or not hasattr(localnet, "get_provider"):
      return None
    return localnet.get_provider(family)

  def _stop_scan_key(self, scan_key: ScanKey) -> None:
    """Stops an active scan (standard or trace) for the specified ScanKey."""
    section, name = scan_key
    trace_stop = self._scan_trace_stop_events.pop(scan_key, None)
    if section == "families":
      fam_trace = self._trace_stop_events.pop(name, None)
      if fam_trace is not None:
        fam_trace.set()
    if trace_stop is not None:
      trace_stop.set()
    provider = self._scan_providers.pop(scan_key, None)
    if section == "families":
      fam_prov = self._family_providers.pop(name, None)
      if provider is None:
        provider = fam_prov
    if provider is not None:
      try:
        provider.stop_scan()
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug(
            "%s Error stopping provider: %s", self._log_prefix(scan_key), err
        )
      if provider in self._active_providers:
        self._active_providers.remove(provider)

  def _stop_family_scan(self, family: str) -> None:
    """Stops an active provider scan for the specified family, if running."""
    self._stop_scan_key(("families", family))

  def _stop_all_family_scans(self) -> None:
    """Stops all currently running family and named scans."""
    for trace_stop in list(self._scan_trace_stop_events.values()):
      trace_stop.set()
    self._scan_trace_stop_events.clear()
    for trace_stop in list(self._trace_stop_events.values()):
      trace_stop.set()
    self._trace_stop_events.clear()
    for scan_key in list(self._scan_providers.keys()):
      self._stop_scan_key(scan_key)
    for family in list(self._family_providers.keys()):
      self._stop_family_scan(family)
    for provider in list(self._active_providers):
      try:
        provider.stop_scan()
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Error stopping active provider: %s", err)
    self._active_providers.clear()

  def _has_registered_provider(self, family: str) -> bool:
    """Checks if a provider factory or LocalnetManager provider is registered."""
    if family in self._provider_factories:
      return True
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

  def _reconcile_scan_section(
      self,
      section: str,
      configs: Optional[Dict[str, Any]],
      now_dt: datetime,
  ) -> bool:
    """Reconciles either 'families' or 'scans' config entries into discovery state."""
    state_updated = False
    state_map = self._get_state_map(section, create=False)

    if configs is None:
      if state_map:
        for existing_name in list(state_map.keys()):
          scan_key = (section, existing_name)
          self._stop_scan_key(scan_key)
          self._applied_scan_configs.pop(scan_key, None)
          if section == "families":
            self._applied_family_configs.pop(existing_name, None)
        if section == "scans":
          self._discovery_state.scans = None
        else:
          self._discovery_state.families = {}
        state_updated = True
      return state_updated

    state_map = self._get_state_map(section, create=True)
    assert state_map is not None

    configured_keys = set(configs.keys())
    for existing_name in list(state_map.keys()):
      if existing_name not in configured_keys:
        scan_key = (section, existing_name)
        self._stop_scan_key(scan_key)
        self._applied_scan_configs.pop(scan_key, None)
        if section == "families":
          self._applied_family_configs.pop(existing_name, None)
        state_map.pop(existing_name, None)
        state_updated = True

    for entry_name, fam_config in configs.items():
      scan_key: ScanKey = (section, entry_name)
      prefix = self._log_prefix(scan_key)
      prev_cfg = self._applied_scan_configs.get(scan_key)
      f_state = state_map.get(entry_name)

      # Empty or cleared config stops active/pending scan
      if not fam_config or (
          not getattr(fam_config, "generation", None)
          and not getattr(fam_config, "scan_interval_sec", None)
          and not getattr(fam_config, "passive_sec", None)
      ):
        if f_state and f_state.phase in (
            DiscoveryPhase.active,
            DiscoveryPhase.pending,
        ):
          self._stop_scan_key(scan_key)
          f_state.phase = DiscoveryPhase.stopped
          state_updated = True
        self._applied_scan_configs[scan_key] = copy.deepcopy(fam_config)
        if section == "families":
          self._applied_family_configs[entry_name] = copy.deepcopy(fam_config)
        continue

      # No-op if config is unchanged and already tracked in state
      if prev_cfg == fam_config and f_state is not None:
        continue

      # Config changed: stop any currently running scan first
      if f_state and f_state.phase == DiscoveryPhase.active:
        self._stop_scan_key(scan_key)

      prev_state_gen = f_state.generation if f_state else None
      if not f_state:
        f_state = FamilyDiscoveryState()
        state_map[entry_name] = f_state

      self._applied_scan_configs[scan_key] = copy.deepcopy(fam_config)
      if section == "families":
        self._applied_family_configs[entry_name] = copy.deepcopy(fam_config)

      # Validate scan name and required family for named scans
      if section == "scans":
        if not SCAN_NAME_RE.match(str(entry_name)):
          f_state.phase = DiscoveryPhase.stopped
          f_state.generation = getattr(fam_config, "generation", None)
          f_state.status = Entry(
              category="discovery.family.scan",
              level=500,
              message=f"{prefix} Invalid scan name '{entry_name}'",
              timestamp=_format_generation(now_dt),
          )
          state_updated = True
          continue
        family = getattr(fam_config, "family", None)
        if not family or not SCAN_NAME_RE.match(str(family)):
          f_state.phase = DiscoveryPhase.stopped
          f_state.generation = getattr(fam_config, "generation", None)
          f_state.status = Entry(
              category="discovery.family.scan",
              level=500,
              message=(
                  f"{prefix} Named scan '{entry_name}' requires a valid"
                  " 'family'"
              ),
              timestamp=_format_generation(now_dt),
          )
          state_updated = True
          continue
      else:
        family = entry_name

      raw_depth = getattr(fam_config, "depth", None)
      depth_val = str(getattr(raw_depth, "value", raw_depth) or "").lower()
      is_trace = depth_val == "trace"

      # Enforce capture_only mode for BACnet coexistence (e.g. alongside Mango)
      if self.discovery_mode == "capture_only" and family == "bacnet" and not is_trace:
        f_state.phase = DiscoveryPhase.stopped
        f_state.generation = getattr(fam_config, "generation", None)
        f_state.status = Entry(
            category="discovery.family.scan",
            level=500,
            message=(
                f"{prefix} Active BACnet discovery is disabled in capture_only"
                " mode; only depth='trace' is permitted"
            ),
            timestamp=_format_generation(now_dt),
        )
        state_updated = True
        continue

      # Validate provider availability if device LocalnetManager is wired
      if not (is_trace and family == "bacnet" and self.discovery_mode == "capture_only"):
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
              "%s Skipping stale past generation %s (start_time: %s)",
              prefix,
              gen_str,
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

    return state_updated

  def handle_config(self, config: Config) -> None:
    """Reconciles discovery configuration, self-enumeration, families, and named scans."""
    if not config or config.discovery is None:
      self._stop_all_family_scans()
      self._config = None
      self._applied_family_configs.clear()
      self._applied_scan_configs.clear()
      self._discovery_state.generation = None
      self._discovery_state.families = None
      self._discovery_state.scans = None
      self.trigger_state_update()
      return

    self._config = config.discovery
    if self._discovery_state.families is None and config.discovery.families is not None:
      self._discovery_state.families = {}

    state_updated = self._handle_self_enumeration(config.discovery)
    now_dt = datetime.now(timezone.utc)

    if self._reconcile_scan_section("families", config.discovery.families, now_dt):
      state_updated = True
    if self._reconcile_scan_section(
        "scans", getattr(config.discovery, "scans", None), now_dt
    ):
      state_updated = True

    if self._scan_wake_event:
      self._scan_wake_event.set()

    if state_updated:
      self.trigger_state_update()

  def update_state(self, state: State) -> None:
    """Contributes discovery state to device state."""
    for scan_key, provider in list(self._scan_providers.items()):
      section, name = scan_key
      state_map = self._get_state_map(section, create=False)
      f_state = state_map.get(name) if state_map else None
      if f_state is not None:
        passive_holdoff = getattr(provider, "passive_holdoff_count", 0)
        if passive_holdoff:
          f_state.passive_count = passive_holdoff
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
        or self._discovery_state.scans is not None
    ):
      state.discovery = self._discovery_state

  def _get_check_interval(self) -> float:
    """Calculates check interval from pending generations and intervals."""
    min_wait = 60.0
    now_dt = datetime.now(timezone.utc)
    for section in ("families", "scans"):
      cfg_map = self._get_config_map(section)
      state_map = self._get_state_map(section, create=False)
      if not cfg_map:
        continue
      for name, fam_config in cfg_map.items():
        f_state = state_map.get(name) if state_map else None
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

  def _update_scan_state(self, scan_key: ScanKey, active: bool) -> None:
    """Updates UDMI discovery phase for a ScanKey and schedules next periodic cycle."""
    section, name = scan_key
    state_map = self._get_state_map(section, create=True)
    assert state_map is not None
    if name not in state_map:
      state_map[name] = FamilyDiscoveryState()

    f_state = state_map[name]

    if active:
      f_state.phase = DiscoveryPhase.active
    else:
      cfg_map = self._get_config_map(section)
      fam_config = cfg_map.get(name) if cfg_map else None
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

  def _update_family_state(self, family: str, active: bool) -> None:
    """Updates family UDMI discovery phase and schedules next periodic cycle."""
    self._update_scan_state(("families", family), active)

  def _handle_scan_result_for_key(
      self,
      scan_key: Optional[ScanKey],
      device_id: str,
      event: DiscoveryEvents,
  ) -> None:
    """Handles discovered device event for a ScanKey, tags scan name, and publishes."""
    del device_id
    prefix = self._log_prefix(scan_key) if scan_key else ""
    LOGGER.info(
        "%s Discovery event received for device: %s",
        prefix,
        event.addr or event.network or "marker",
    )
    if not event.timestamp:
      event.timestamp = _format_generation(datetime.now(timezone.utc))
    if not event.version:
      event.version = UDMI_VERSION

    if scan_key is None and event.family:
      scan_key = ("families", event.family)

    if scan_key is not None:
      section, name = scan_key
      if section == "scans":
        event.scan = name
      state_map = self._get_state_map(section, create=False)
      f_state = state_map.get(name) if state_map else None
      if f_state and f_state.generation and event.generation != f_state.generation:
        event.generation = f_state.generation

      provider = self._scan_providers.get(scan_key)
      if provider is None and section == "families":
        provider = self._family_providers.get(name)
      if provider is not None and f_state is not None:
        passive_holdoff = getattr(provider, "passive_holdoff_count", 0)
        if passive_holdoff:
          f_state.passive_count = passive_holdoff

      if event.event_no is not None:
        if event.event_no > 0:
          self._scan_event_counts[scan_key] = event.event_no
          if section == "families":
            self._active_event_counts[name] = event.event_no
          if f_state:
            f_state.active_count = event.event_no
            self.trigger_state_update()
      else:
        new_count = self._scan_event_counts.get(scan_key, 0) + 1
        self._scan_event_counts[scan_key] = new_count
        if section == "families":
          self._active_event_counts[name] = new_count
        if f_state:
          f_state.active_count = new_count
          self.trigger_state_update()

    self.publish_event(event, "discovery")

  def _handle_scan_result(self, device_id: str, event: DiscoveryEvents) -> None:
    """Handles discovered device event for a family scan (legacy entrypoint)."""
    scan_key = ("families", event.family) if event.family else None
    self._handle_scan_result_for_key(scan_key, device_id, event)

  def _should_scan_key(
      self, scan_key: ScanKey, config: FamilyDiscoveryConfig
  ) -> bool:
    """Determines whether a discovery scan is due for the given ScanKey."""
    if not config:
      return False
    section, name = scan_key
    state_map = self._get_state_map(section, create=True)
    assert state_map is not None
    f_state = state_map.get(name)
    if not f_state:
      f_state = FamilyDiscoveryState()
      state_map[name] = f_state

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
          last_run = self._scan_last_times.get(
              scan_key,
              self._last_scan_times.get(name, 0) if section == "families" else 0,
          )
          return (time.time() - last_run) >= interval_sec
      else:
        last_run = self._scan_last_times.get(
            scan_key,
            self._last_scan_times.get(name, 0) if section == "families" else 0,
        )
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

  def _should_scan(self, family: str, config: FamilyDiscoveryConfig) -> bool:
    """Determines whether a discovery scan is due for the given family."""
    return self._should_scan_key(("families", family), config)

  def _check_scheduled_scans(self) -> None:
    """Checks both config.discovery.families and config.discovery.scans for due runs."""
    if not self._config:
      return
    for section in ("families", "scans"):
      cfg_map = self._get_config_map(section)
      if not cfg_map:
        continue
      for name, fam_config in list(cfg_map.items()):
        scan_key: ScanKey = (section, name)
        if self._should_scan_key(scan_key, fam_config):
          family = (
              getattr(fam_config, "family", None)
              if section == "scans"
              else name
          )
          if not family:
            continue
          LOGGER.info(
              "%s Scheduled scan triggered (family: %s)",
              self._log_prefix(scan_key),
              family,
          )
          self._trigger_scan_key(scan_key, family, fam_config)

  def _trigger_scan_key(
      self,
      scan_key: ScanKey,
      family: str,
      fam_config: Optional[FamilyDiscoveryConfig],
  ) -> None:
    """Launches a background scan thread for the given ScanKey."""
    section, name = scan_key
    depth_val = getattr(fam_config, "depth", None) if fam_config else None
    is_trace = depth_val in (Depth.trace, Depth.trace.value)

    provider = self._resolve_provider_for_scan(family)
    if provider is None and not (
        is_trace and family == "bacnet" and self.discovery_mode == "capture_only"
    ):
      LOGGER.warning(
          "%s No provider registered for family '%s'.",
          self._log_prefix(scan_key),
          family,
      )
      return

    if provider is not None:
      self._active_providers.append(provider)
    self._update_scan_state(scan_key, True)
    self.trigger_state_update()

    now_ts = time.time()
    self._scan_last_times[scan_key] = now_ts
    if section == "families":
      self._last_scan_times[name] = now_ts

    thread = threading.Thread(
        target=self._run_scan_key,
        args=(scan_key, family, provider, fam_config),
        name=f"Scan-{section}-{name}",
        daemon=True,
    )
    thread.start()

  def _run_scan_key(
      self,
      scan_key: ScanKey,
      family: str,
      provider: Any,
      fam_config: Optional[FamilyDiscoveryConfig] = None,
  ) -> None:
    """Executes a family or named scan with circuit breaker and per-family exclusivity."""
    section, name = scan_key
    prefix = self._log_prefix(scan_key)
    state_map = self._get_state_map(section, create=True)
    assert state_map is not None

    if fam_config is None:
      cfg_map = self._get_config_map(section)
      fam_config = cfg_map.get(name) if cfg_map else None

    if check_safety_circuit_breaker(self.max_mem_pct):
      LOGGER.warning(
          "%s Safety circuit breaker active: Host memory exceeds %.1f%%"
          " threshold. Throttling scan.",
          prefix,
          self.max_mem_pct,
      )
      f_state = state_map.get(name)
      if not f_state:
        f_state = FamilyDiscoveryState()
        state_map[name] = f_state
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
      if provider is not None and provider in self._active_providers:
        self._active_providers.remove(provider)
      self._scan_providers.pop(scan_key, None)
      if section == "families":
        self._family_providers.pop(name, None)
      self._update_scan_state(scan_key, False)
      self.trigger_state_update(immediate=True)
      return

    # Acquire non-blocking per-family exclusivity lock if family is exclusive
    fam_lock: Optional[threading.Lock] = None
    acquired_lock = False
    if family in self._exclusive_families:
      fam_lock = self._get_family_lock(family)
      acquired_lock = fam_lock.acquire(blocking=False)
      if not acquired_lock:
        LOGGER.warning(
            "%s Skipping scan: family '%s' is busy with another active scan.",
            prefix,
            family,
        )
        f_state = state_map.get(name)
        if not f_state:
          f_state = FamilyDiscoveryState()
          state_map[name] = f_state
        f_state.status = Entry(
            category="discovery.family.scan",
            level=500,
            message=f"{prefix} Family '{family}' is busy with another active scan",
            timestamp=_format_generation(datetime.now(timezone.utc)),
        )
        interval_sec = (
            getattr(fam_config, "scan_interval_sec", None)
            if fam_config
            else None
        )
        if interval_sec and interval_sec > 0:
          current_dt = _parse_generation(f_state.generation) or datetime.now(
              timezone.utc
          )
          f_state.generation = _format_generation(
              current_dt + timedelta(seconds=interval_sec)
          )
          f_state.phase = DiscoveryPhase.pending
        else:
          f_state.phase = DiscoveryPhase.stopped
        if provider is not None and provider in self._active_providers:
          self._active_providers.remove(provider)
        self._scan_providers.pop(scan_key, None)
        if section == "families":
          self._family_providers.pop(name, None)
        self.trigger_state_update(immediate=True)
        return

    try:
      depth_val = getattr(fam_config, "depth", None) if fam_config else None
      if depth_val in (Depth.trace, Depth.trace.value):
        f_state = state_map.get(name)
        run_generation = getattr(f_state, "generation", None) or (
            getattr(fam_config, "generation", None) if fam_config else None
        )
        try:
          self._run_trace_capture(family, fam_config, scan_key=scan_key)
        finally:
          if provider is not None and provider in self._active_providers:
            self._active_providers.remove(provider)
          if self._scan_providers.get(scan_key) is provider:
            self._scan_providers.pop(scan_key, None)
          if section == "families" and self._family_providers.get(name) is provider:
            self._family_providers.pop(name, None)
          cur_state = state_map.get(name)
          if cur_state is not None and cur_state.generation == run_generation:
            self._update_scan_state(scan_key, False)
            self.trigger_state_update(immediate=True)
        return

      # Standard Protocol Scan
      f_state = state_map.get(name)
      if not f_state:
        f_state = FamilyDiscoveryState()
        state_map[name] = f_state

      if (
          not f_state.generation
          and fam_config
          and getattr(fam_config, "generation", None)
      ):
        f_state.generation = fam_config.generation
      run_generation = f_state.generation

      f_state.phase = DiscoveryPhase.active
      f_state.active_count = 0
      self._scan_event_counts[scan_key] = 0
      self._scan_providers[scan_key] = provider
      if section == "families":
        self._active_event_counts[name] = 0
        self._family_providers[name] = provider
      self.trigger_state_update(immediate=True)

      effective_config = copy.copy(fam_config) if fam_config else None
      if effective_config is not None:
        if f_state.generation:
          effective_config.generation = f_state.generation
        if (
            getattr(effective_config, "scan_duration_sec", None) is None
            and getattr(effective_config, "scan_interval_sec", None)
        ):
          effective_config.scan_duration_sec = effective_config.scan_interval_sec

      def _publish_for_scan(dev_id: str, ev: DiscoveryEvents) -> None:
        self._handle_scan_result_for_key(scan_key, dev_id, ev)

      LOGGER.info(
          "%s Scanning family '%s' (generation: %s)...",
          prefix,
          family,
          f_state.generation,
      )
      try:
        provider.start_scan(effective_config, _publish_for_scan)
        LOGGER.info("%s Scan finished.", prefix)
        if f_state.generation == run_generation:
          f_state.status = None
          passive_holdoff = getattr(provider, "passive_holdoff_count", 0)
          if passive_holdoff:
            f_state.passive_count = passive_holdoff
      except Exception as e:  # pylint: disable=broad-exception-caught
        LOGGER.error("%s Scan failed: %s", prefix, e, exc_info=True)
        if f_state.generation == run_generation:
          err_msg = str(e).strip() or type(e).__name__
          f_state.status = Entry(
              category="discovery.family.scan",
              level=500,
              message=err_msg,
              timestamp=_format_generation(datetime.now(timezone.utc)),
          )
      finally:
        if provider is not None and provider in self._active_providers:
          self._active_providers.remove(provider)
        if self._scan_providers.get(scan_key) is provider:
          self._scan_providers.pop(scan_key, None)
        if section == "families" and self._family_providers.get(name) is provider:
          self._family_providers.pop(name, None)
        if f_state.generation == run_generation:
          self._update_scan_state(scan_key, False)
          self.trigger_state_update(immediate=True)
    finally:
      if acquired_lock and fam_lock is not None:
        fam_lock.release()

  def _run_scan(self, family: str, provider: Any) -> None:
    """Executes a protocol family scan (legacy entrypoint)."""
    self._run_scan_key(("families", family), family, provider)

  def _run_trace_capture(
      self,
      family: str,
      fam_config: Any,
      scan_key: Optional[ScanKey] = None,
  ) -> None:
    """Executes PCAP capture, runs raw-socket BACnet Who-Is rounds, and streams chunks over upload/discovery."""
    effective_key: ScanKey = scan_key or ("families", family)
    section, entry_name = effective_key
    scan_name = entry_name if section == "scans" else None
    prefix = self._log_prefix(effective_key)

    state_map = self._get_state_map(section, create=True)
    assert state_map is not None
    f_state = state_map.get(entry_name)
    if not f_state:
      f_state = FamilyDiscoveryState()
      state_map[entry_name] = f_state

    if check_safety_circuit_breaker(self.max_mem_pct):
      LOGGER.warning(
          "%s Safety circuit breaker active: Throttling trace capture.",
          prefix,
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
        message=f"{prefix} Starting trace capture for family '{family}'...",
        timestamp=_format_generation(datetime.now(timezone.utc)),
    )
    self.trigger_state_update(immediate=True)

    whois_stop_event = threading.Event()
    self._scan_trace_stop_events[effective_key] = whois_stop_event
    if section == "families":
      self._trace_stop_events[family] = whois_stop_event
    whois_thread: Optional[threading.Thread] = None
    whois_errors: List[BaseException] = []

    try:
      trace_cfg = getattr(fam_config, "trace", None)
      if isinstance(fam_config, dict):
        trace_cfg = fam_config.get("trace")

      if isinstance(trace_cfg, dict):
        raw_interface = trace_cfg.get("interface")
        filter_str = trace_cfg.get("filter") or ""
        raw_max_bytes = trace_cfg.get("max_bytes")
        exclude_capture_host = bool(trace_cfg.get("exclude_capture_host", False))
      elif trace_cfg:
        raw_interface = getattr(trace_cfg, "interface", None)
        filter_str = getattr(trace_cfg, "filter", None) or ""
        raw_max_bytes = getattr(trace_cfg, "max_bytes", None)
        exclude_capture_host = bool(
            getattr(trace_cfg, "exclude_capture_host", False)
        )
      else:
        raw_interface = None
        filter_str = ""
        raw_max_bytes = None
        exclude_capture_host = False

      # Fail fast on missing/invalid explicit interface, scan_duration_sec, or max_bytes
      interface = validate_trace_interface(raw_interface)

      raw_duration = getattr(fam_config, "scan_duration_sec", None)
      if isinstance(fam_config, dict):
        raw_duration = fam_config.get("scan_duration_sec")
      if raw_duration is None or int(raw_duration) <= 0:
        raise ValueError(
            "scan_duration_sec must be explicitly set and > 0 for trace capture"
        )
      max_duration_sec = int(raw_duration)

      if raw_max_bytes is None or int(raw_max_bytes) <= 0:
        raise ValueError(
            "trace.max_bytes must be explicitly set and > 0 for trace capture"
        )
      max_bytes = int(raw_max_bytes)

      # Release any cached BAC0 client before BACnet trace so UDP 47808 stays unbound
      if family == "bacnet":
        localnet = getattr(self._device, "get_manager", lambda *_: None)(
            "localnet"
        )
        if localnet is not None:
          bacnet_prov = getattr(localnet, "get_provider", lambda *_: None)(
              "bacnet"
          )
          if bacnet_prov is not None and callable(
              getattr(bacnet_prov, "close", None)
          ):
            bacnet_prov.close()
        # CAP_NET_RAW pre-flight check before starting capture
        check_raw_socket_permission()

      gateway_ip = resolve_gateway_ip(interface)
      if not filter_str and family == "bacnet":
        filter_str = "udp port 47808"
      effective_filter = build_trace_bpf_filter(
          filter_str,
          exclude_capture_host=exclude_capture_host,
          gateway_ip=gateway_ip,
      )

      LOGGER.info(
          "Spawning TRACE capture worker on '%s' (filter: '%s', max_duration:"
          " %ds, max_bytes: %d)",
          interface,
          effective_filter,
          max_duration_sec,
          max_bytes,
      )

      # 1. Emit start marker (event_no: 0) over upload/discovery
      start_msg = DiscoveryUpload(
          timestamp=_format_generation(datetime.now(timezone.utc)),
          version=UDMI_VERSION,
          generation=generation,
          family=family,
          scan=scan_name,
          event_no=0,
      )
      self.publish_upload(start_msg, "discovery")

      discovered_routed_networks: Set[int] = set()
      scan_round_timestamps: List[str] = []
      bacnet_ip_devices: Set[int] = set()
      routed_mstp_devices: Set[int] = set()
      device_addrs: Dict[int, Set[str]] = {}
      bvll_tail = b""

      # 2. Launch Who-Is rounds ONLY once tcpdump signals ready (G1)
      def _start_whois_rounds() -> None:
        nonlocal whois_thread
        if family != "bacnet" or whois_stop_event.is_set():
          return
        whois_thread = threading.Thread(
            target=run_bacnet_trace_whois_rounds,
            kwargs={
                "discovered_routed_networks": discovered_routed_networks,
                "scan_round_timestamps": scan_round_timestamps,
                "stop_event": whois_stop_event,
                "max_duration_sec": max_duration_sec,
                "interface": interface,
                "src_ip": gateway_ip,
                "error_out": whois_errors,
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
      last_flush_mono = time.monotonic()

      def _emit_compressed_chunk(raw_bytes: bytes) -> None:
        nonlocal chunk_count, last_flush_mono
        sha256_hasher.update(raw_bytes)
        compressed = gzip.compress(raw_bytes)
        b64_data = base64.b64encode(compressed).decode("ascii")
        chunk_count += 1
        chunk_msg = DiscoveryUpload(
            timestamp=_format_generation(datetime.now(timezone.utc)),
            version=UDMI_VERSION,
            generation=generation,
            family=family,
            scan=scan_name,
            event_no=chunk_count,
            data=b64_data,
        )
        self.publish_upload(chunk_msg, "discovery")
        last_flush_mono = time.monotonic()
        self.trigger_state_update()
        LOGGER.info(
            "Published upload/discovery chunk %d (event_no: %d, %d raw bytes ->"
            " %d gzip bytes)",
            chunk_count,
            chunk_count,
            len(raw_bytes),
            len(compressed),
        )

      heartbeat = (
          min(1.0, self.trace_flush_interval_sec)
          if self.trace_flush_interval_sec > 0
          else None
      )
      data_generator = capture_packets(
          interface=interface,
          filter_str=effective_filter,
          max_duration_sec=max_duration_sec,
          max_bytes=max_bytes,
          stop_event=whois_stop_event,
          on_ready=_start_whois_rounds if family == "bacnet" else None,
          heartbeat_sec=heartbeat,
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
          if chunk:
            total_bytes += len(chunk)
            if family == "bacnet":
              routed, iam_obs, bvll_tail = extract_bacnet_trace_observations(
                  chunk, tail=bvll_tail
              )
              if routed:
                discovered_routed_networks.update(routed)
              if iam_obs:
                for dev_id, addr_key, is_routed in iam_obs:
                  if is_routed:
                    routed_mstp_devices.add(dev_id)
                  else:
                    bacnet_ip_devices.add(dev_id)
                  device_addrs.setdefault(dev_id, set()).add(addr_key)
                f_state.active_count = len(
                    bacnet_ip_devices | routed_mstp_devices
                )
            pending_buf.extend(chunk)
            while len(pending_buf) >= chunk_size:
              slice_bytes = bytes(pending_buf[:chunk_size])
              del pending_buf[:chunk_size]
              _emit_compressed_chunk(slice_bytes)
          if (
              pending_buf
              and self.trace_flush_interval_sec > 0
              and (time.monotonic() - last_flush_mono)
              >= self.trace_flush_interval_sec
          ):
            _emit_compressed_chunk(bytes(pending_buf))
            pending_buf.clear()
      finally:
        close_fn = getattr(data_generator, "close", None)
        if callable(close_fn):
          close_fn()

      cancelled_externally = whois_stop_event.is_set() and not whois_errors
      whois_stop_event.set()
      if whois_thread and whois_thread.is_alive():
        whois_thread.join(timeout=2.0)

      if whois_errors:
        raise whois_errors[0]

      if pending_buf:
        _emit_compressed_chunk(bytes(pending_buf))
        pending_buf.clear()

      if f_state.generation != generation:
        return

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

      if cancelled_externally and total_bytes == 0:
        f_state.phase = DiscoveryPhase.stopped
        return

      if total_bytes == 0:
        raise RuntimeError(
            f"Packet capture on '{interface}' (filter: '{effective_filter}')"
            " produced 0 bytes"
        )

      # 4. Emit mandatory EOF marker (event_no: -(N + 1)) with sha256 and scan_manifest
      digest = sha256_hasher.hexdigest()
      duplicate_device_ids = sorted(
          dev_id for dev_id, addrs in device_addrs.items() if len(addrs) > 1
      )
      manifest = ScanManifest(
          gateway_ip=gateway_ip,
          interface=interface,
          filter=effective_filter,
          scan_round_timestamps=scan_round_timestamps,
          discovered_routed_networks=sorted(discovered_routed_networks),
          bacnet_ip_devices=len(bacnet_ip_devices),
          routed_mstp_devices=len(routed_mstp_devices),
          duplicate_device_ids=duplicate_device_ids,
      )
      eof_msg = DiscoveryUpload(
          timestamp=_format_generation(datetime.now(timezone.utc)),
          version=UDMI_VERSION,
          generation=generation,
          family=family,
          scan=scan_name,
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

      if f_state.generation == generation:
        f_state.phase = DiscoveryPhase.stopped
        f_state.active_count = (
            len(bacnet_ip_devices | routed_mstp_devices)
            if family == "bacnet"
            else 0
        )
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
                  f"Trace capture complete. {chunk_count} upload/discovery"
                  " chunks emitted."
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
      if f_state.generation == generation:
        f_state.phase = DiscoveryPhase.stopped
        f_state.status = Entry(
            category="discovery.family.scan",
            level=500,
            message=str(e),
            timestamp=_format_generation(datetime.now(timezone.utc)),
        )
    finally:
      if self._trace_stop_events.get(family) is whois_stop_event:
        self._trace_stop_events.pop(family, None)
      self.trigger_state_update()
