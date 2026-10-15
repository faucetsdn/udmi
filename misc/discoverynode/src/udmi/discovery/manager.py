"""Discovery manager: single entry point for the `discovery` config block.

Reconciles the set of live scan controllers with the config. Both legacy
`discovery.families.<family>` entries and named `discovery.scans.<name>`
entries are normalised into a `ScanSpec` and go through the same creation
path; the only differences are where the family comes from and where state
is reported.
"""

import atexit
import dataclasses
import logging
import threading
from typing import Any, Callable

import udmi.discovery.bacnet
import udmi.discovery.discovery
import udmi.discovery.ether
import udmi.discovery.numbers
import udmi.discovery.passive
import udmi.schema.config
import udmi.schema.state

# ("families", "<family>") | ("scans", "<name>")
ScanKey = tuple[str, str]

SOURCE_FAMILIES = "families"
SOURCE_SCANS = "scans"

# Family registry. Maps family -> controller class.
FAMILY_CONTROLLERS: dict[str, type[udmi.discovery.discovery.DiscoveryController]] = {
    "vendor": udmi.discovery.numbers.NumberDiscovery,
    "bacnet": udmi.discovery.bacnet.GlobalBacnetDiscovery,
    "ipv4": udmi.discovery.passive.PassiveNetworkDiscovery,
    "ether": udmi.discovery.ether.EtherDiscovery,
}


class ScanConfigError(ValueError):
  """A scan entry in the config is invalid and will not be started."""


@dataclasses.dataclass(frozen=True)
class ScanSpec:
  key: ScanKey
  family: str
  # None == present-but-empty (legacy `{}`), i.e. stop but keep the entry
  config: udmi.schema.config.DiscoveryFamily | None


def scan_id_for(key: ScanKey) -> str:
  return f"{key[0]}/{key[1]}"


class DiscoveryManager:
  """Owns the scan_key -> controller map and drives it from UDMI configs."""

  def __init__(
      self,
      state: udmi.schema.state.State,
      publisher: Callable,
      *,
      enabled_families: set[str],
      family_kwargs: dict[str, dict[str, Any]],
  ):
    """
    Args:
      state: top-level device state (manager owns state.discovery.families / .scans)
      publisher: callable accepting a DiscoveryEvent
      enabled_families: families switched on by the local config
        `udmi.discovery.{vendor,bacnet,ipv4,ether}`
      family_kwargs: per-family constructor kwargs derived from the local
        config, e.g. {"vendor": {"range": ...}, "bacnet": {"bacnet": <stack>}}

    Raises:
      ValueError: if an enabled family has no registered controller
    """
    unknown = set(enabled_families) - FAMILY_CONTROLLERS.keys()
    if unknown:
      raise ValueError(
          f"unknown discovery families enabled in local config: {sorted(unknown)}"
      )
    self.state = state
    self.publisher = publisher
    self.enabled_families = frozenset(enabled_families)
    self.family_kwargs = family_kwargs
    self.scans: dict[ScanKey, udmi.discovery.discovery.DiscoveryController] = {}
    self.lock = threading.Lock()
    self.log = logging.getLogger(__name__)
    atexit.register(self.shutdown)

  # ---------------------------------------------------------------- parsing

  def _parse(
      self, discovery_config: dict[str, Any]
  ) -> tuple[dict[ScanKey, ScanSpec], dict[ScanKey, ScanConfigError]]:
    """Normalises the `discovery` block into ScanSpecs.

    Returns:
      (specs, errors): valid entries, and entries which were rejected.
    """
    specs: dict[ScanKey, ScanSpec] = {}
    errors: dict[ScanKey, ScanConfigError] = {}

    for family, block in (discovery_config.get(SOURCE_FAMILIES) or {}).items():
      key = (SOURCE_FAMILIES, family)
      try:
        self._check_family_enabled(family)
        config = (
            udmi.schema.config.discovery_family_from_dict(scan_id_for(key), block)
            if block
            else None
        )
        specs[key] = ScanSpec(key=key, family=family, config=config)
      except (ScanConfigError, ValueError, TypeError) as err:
        errors[key] = ScanConfigError(str(err))

    for name, block in (discovery_config.get(SOURCE_SCANS) or {}).items():
      key = (SOURCE_SCANS, name)
      try:
        if not udmi.schema.config.SCAN_NAME_PATTERN.match(name):
          raise ScanConfigError(
              f"invalid scan name {name!r}; must match"
              f" {udmi.schema.config.SCAN_NAME_PATTERN.pattern}"
          )
        if not isinstance(block, dict) or not block:
          raise ScanConfigError("named scan must be a non-empty object")
        if "family" not in block:
          raise ScanConfigError("family is required")
        family = block["family"]
        self._check_family_enabled(family)
        body = {k: v for k, v in block.items() if k != "family"}
        config = udmi.schema.config.discovery_family_from_dict(scan_id_for(key), body)
        specs[key] = ScanSpec(key=key, family=family, config=config)
      except (ScanConfigError, ValueError, TypeError) as err:
        errors[key] = ScanConfigError(str(err))

    return specs, errors

  def _check_family_enabled(self, family: Any) -> None:
    if family not in self.enabled_families:
      raise ScanConfigError(
          f"family {family!r} is not enabled on this node"
          f" (enabled: {', '.join(sorted(self.enabled_families))})"
      )

  # ------------------------------------------------------------ reconciling

  def apply_config(self, config: dict[str, Any]) -> None:
    """Reconciles the live scan set with the `discovery` block of a UDMI config.

    Args:
      config: complete UDMI config as a dictionary
    """
    with self.lock:
      discovery_config = config.get("discovery") or {}
      specs, errors = self._parse(discovery_config)

      self.log.info(
          "discovery config: %d legacy family(s) (%s), %d named scan(s) (%s)%s",
          len(discovery_config.get(SOURCE_FAMILIES) or {}),
          ", ".join(discovery_config.get(SOURCE_FAMILIES) or {}),
          len(discovery_config.get(SOURCE_SCANS) or {}),
          ", ".join(discovery_config.get(SOURCE_SCANS) or {}),
          f", {len(errors)} rejected" if errors else "",
      )

      # Keys which disappeared from the config
      for key in [k for k in self.scans if k not in specs and k not in errors]:
        self._remove(key, reason="removed from config")

      # Rejected keys: stop anything previously running, surface the error in state
      for key, err in errors.items():
        if key in self.scans:
          self._remove(key, reason="config rejected")
        entry = self._state_entry(key)
        entry.status = udmi.schema.state.Status(
            category="discovery.config", level=500, message=str(err)
        )
        entry.phase = udmi.schema.state.Phase.stopped
        self.log.error("[%s] rejected: %s", scan_id_for(key), err)

      # Valid keys: create lazily (same path for legacy and named), then apply
      for key, spec in specs.items():
        if key in self.scans and self.scans[key].family != spec.family:
          self._remove(key, reason="family changed")
        if spec.config is None:
          if key in self.scans:
            self.scans[key].apply_config(None)
          else:
            entry = self._state_entry(key)
            for field in dataclasses.fields(entry):
              setattr(entry, field.name, None)
          continue
        if key not in self.scans:
          self.scans[key] = self._create(spec)
        self.scans[key].apply_config(spec.config)

  def _create(self, spec: ScanSpec) -> udmi.discovery.discovery.DiscoveryController:
    cls = FAMILY_CONTROLLERS[spec.family]
    scan_id = scan_id_for(spec.key)
    entry = self._state_entry(spec.key)
    for field in dataclasses.fields(entry):
      setattr(entry, field.name, None)
    self.log.info("[%s] created (family=%s)", scan_id, spec.family)
    return cls(
        entry,
        self.publisher,
        scan_id=scan_id,
        **self.family_kwargs.get(spec.family, {}),
    )

  def _state_entry(self, key: ScanKey) -> udmi.schema.state.Discovery:
    source, name = key
    container = (
        self.state.discovery.families
        if source == SOURCE_FAMILIES
        else self.state.discovery.scans
    )
    if name not in container:
      container[name] = udmi.schema.state.Discovery()
    return container[name]

  def _remove(self, key: ScanKey, *, reason: str) -> None:
    controller = self.scans.pop(key)
    controller.shutdown()
    source, name = key
    if source == SOURCE_SCANS:
      # Named scan entries disappear from published state entirely
      self.state.discovery.scans.pop(name, None)
      self.log.info("[%s] %s, stopped and state cleared", scan_id_for(key), reason)
    else:
      # Legacy family entries are nulled (purged on serialisation), as before
      controller._reset_udmi_state()
      self.log.info("[%s] %s, stopped and state reset", scan_id_for(key), reason)

  # ---------------------------------------------------------------- hooks

  def on_state_update_hook(self) -> None:
    """Executed before state is published; updates per-scan counters."""
    for controller in list(self.scans.values()):
      controller.on_state_update_hook()

  def shutdown(self) -> None:
    """Stops every live scan. Safe to call repeatedly."""
    with self.lock:
      if self.scans:
        self.log.info("discovery shutdown: stopping %d scan(s)", len(self.scans))
        for key in list(self.scans):
          self._remove(key, reason="shutdown")
