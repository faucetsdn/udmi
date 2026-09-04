"""Host infrastructure telemetry and OS probing for UDMI Spotter."""

import logging
import os
from typing import Any, Dict

LOGGER = logging.getLogger(__name__)


def get_host_os_info() -> Dict[str, str]:
  """Probes host OS distribution details from /etc/os-release."""
  os_info = {}
  candidate_paths = [
      "/host/etc/os-release",
      "/etc/os-release",
      "/usr/lib/os-release",
  ]
  for path in candidate_paths:
    if os.path.exists(path):
      try:
        with open(path, "r", encoding="utf-8") as f:
          for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
              k, v = line.split("=", 1)
              os_info[k.strip()] = v.strip().strip('"\'')
        if os_info:
          break
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Could not parse %s: %s", path, err)
  return os_info


def get_host_hardware_info() -> Dict[str, str]:
  """Probes host hardware vendor and model from DMI or device-tree."""
  hardware: Dict[str, str] = {}

  vendor_paths = [
      "/sys/class/dmi/id/sys_vendor",
      "/host/sys/class/dmi/id/sys_vendor",
  ]
  for path in vendor_paths:
    if os.path.exists(path):
      try:
        with open(path, "r", encoding="utf-8") as f:
          val = f.read().strip()
          if val:
            hardware["make"] = val
            break
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Could not read %s: %s", path, err)

  model_paths = [
      "/sys/class/dmi/id/product_name",
      "/host/sys/class/dmi/id/product_name",
  ]
  for path in model_paths:
    if os.path.exists(path):
      try:
        with open(path, "r", encoding="utf-8") as f:
          val = f.read().strip()
          if val:
            hardware["model"] = val
            break
      except Exception as err:  # pylint: disable=broad-exception-caught
        LOGGER.debug("Could not read %s: %s", path, err)

  if "model" not in hardware:
    dt_paths = ["/proc/device-tree/model", "/host/proc/device-tree/model"]
    for path in dt_paths:
      if os.path.exists(path):
        try:
          with open(path, "r", encoding="utf-8") as f:
            val = f.read().strip().replace("\x00", "")
            if val:
              hardware["model"] = val
              break
        except Exception as err:  # pylint: disable=broad-exception-caught
          LOGGER.debug("Could not read %s: %s", path, err)

  return hardware


def get_cpu_and_memory_metrics() -> Dict[str, Any]:
  """Reads system load averages and memory usage from /proc filesystem."""
  metrics: Dict[str, Any] = {}

  # 1. Memory metrics from /proc/meminfo
  if os.path.exists("/proc/meminfo"):
    try:
      meminfo: Dict[str, int] = {}
      with open("/proc/meminfo", "r", encoding="utf-8") as f:
        for line in f:
          parts = line.split(":")
          if len(parts) == 2:
            key = parts[0].strip()
            val_str = parts[1].strip().split()[0]
            if val_str.isdigit():
              meminfo[key] = int(val_str)

      total_kb = meminfo.get("MemTotal", 0)
      avail_kb = meminfo.get("MemAvailable", meminfo.get("MemFree", 0))
      if total_kb > 0:
        metrics["mem_total_mb"] = round(total_kb / 1024.0, 2)
        metrics["mem_free_mb"] = round(avail_kb / 1024.0, 2)
        metrics["mem_used_pct"] = round(
            100.0 * (total_kb - avail_kb) / total_kb, 2
        )
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.debug("Could not parse /proc/meminfo: %s", err)

  # 2. CPU load averages from /proc/loadavg
  if os.path.exists("/proc/loadavg"):
    try:
      with open("/proc/loadavg", "r", encoding="utf-8") as f:
        parts = f.read().strip().split()
        if len(parts) >= 3:
          metrics["load_1m"] = float(parts[0])
          metrics["load_5m"] = float(parts[1])
          metrics["load_15m"] = float(parts[2])
    except Exception as err:  # pylint: disable=broad-exception-caught
      LOGGER.debug("Could not parse /proc/loadavg: %s", err)

  return metrics


def check_safety_circuit_breaker(max_mem_pct: float = 85.0) -> bool:
  """Evaluates host memory usage.

  Returns True if memory exceeds safety threshold.
  """
  metrics = get_cpu_and_memory_metrics()
  used_pct = metrics.get("mem_used_pct", 0.0)
  if used_pct >= max_mem_pct:
    LOGGER.warning(
        "Safety Circuit Breaker: Memory usage %.2f%% exceeds %.2f%% threshold!",
        used_pct,
        max_mem_pct,
    )
    return True
  return False

