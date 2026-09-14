"""Spotter custom SystemManager.

Integrates host telemetry and safety circuit breaker.
"""

from datetime import datetime, timezone
import logging
from typing import Optional

from udmi.constants import UDMI_VERSION
from udmi.core.managers import SystemManager
from udmi.schema import Metrics, StateSystemHardware, SystemEvents, SystemState

try:
  import psutil
except ImportError:
  psutil = None

try:
  from host_telemetry import (
      check_safety_circuit_breaker,
      get_cpu_and_memory_metrics,
  )
except ImportError:
  from edge.spotter.src.host_telemetry import (
      check_safety_circuit_breaker,
      get_cpu_and_memory_metrics,
  )

LOGGER = logging.getLogger("spotter_agent")


class SpotterSystemManager(SystemManager):
  """Spotter SystemManager incorporating host telemetry.

  Also evaluates host memory usage against safety circuit breaker thresholds.
  """

  def __init__(
      self,
      system_state: Optional[SystemState] = None,
      max_mem_pct: float = 85.0,
      metrics_rate_sec: int = 300,
  ) -> None:
    super().__init__(system_state=system_state)
    self.max_mem_pct = max_mem_pct
    self._metrics_rate_sec = metrics_rate_sec

  @property
  def metrics_rate_sec(self) -> int:
    """Returns the current metrics reporting interval in seconds."""
    return self._metrics_rate_sec

  @metrics_rate_sec.setter
  def metrics_rate_sec(self, val: int) -> None:
    """Sets the metrics reporting interval in seconds."""
    self._metrics_rate_sec = val

  @property
  def system_state(self) -> SystemState:
    """Returns the managed system state."""
    return self._system_state

  @system_state.setter
  def system_state(self, val: SystemState) -> None:
    """Updates the managed system state."""
    self._system_state = val

  def set_software_info(
      self, os_name: Optional[str] = None, os_version: Optional[str] = None
  ) -> None:
    """Sets host operating system software details in system state."""
    if self._system_state.software is None:
      self._system_state.software = {}
    if os_name:
      self._system_state.software["os"] = os_name
    if os_version:
      self._system_state.software["os_version"] = os_version

  def set_hardware_info(
      self, make: Optional[str] = None, model: Optional[str] = None
  ) -> None:
    """Sets host hardware details in system state."""
    if self._system_state.hardware is None:
      self._system_state.hardware = StateSystemHardware(
          make=make or "UDMI", model=model or "Spotter"
      )
    else:
      if make:
        self._system_state.hardware.make = make
      if model:
        self._system_state.hardware.model = model

  def publish_metrics(self) -> None:
    """Publishes system metrics using host telemetry with circuit breaker."""
    try:
      # Evaluate memory usage against safety threshold
      if check_safety_circuit_breaker(self.max_mem_pct):
        LOGGER.warning(
            "Safety circuit breaker: Memory usage exceeds %.1f%% threshold"
            " during metrics collection.",
            self.max_mem_pct,
        )

      m = get_cpu_and_memory_metrics()
      mem_total = m.get("mem_total_mb")
      mem_free = m.get("mem_free_mb")
      system_load = m.get("load_1m")

      # Fallback to psutil if /proc metrics are not available
      if mem_total is None:
        if psutil is not None:
          super().publish_metrics()
        return

      metrics = Metrics(
          mem_total_mb=mem_total,
          mem_free_mb=mem_free,
          system_load=system_load,
      )

      system_event = SystemEvents(
          timestamp=datetime.now(timezone.utc).isoformat(),
          version=UDMI_VERSION,
          metrics=metrics,
      )
      self.publish_event(system_event, "system")
    except Exception as e:  # pylint: disable=broad-exception-caught
      LOGGER.error("Failed to publish host metrics: %s", e)

