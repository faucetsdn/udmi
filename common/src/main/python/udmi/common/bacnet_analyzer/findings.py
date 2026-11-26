"""Disjoint, cause-grouped BACnet/IP findings with frame-level evidence."""

from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Set

from udmi.common.bacnet_analyzer.metrics import (
    HIGH_BROADCAST_RATE_PER_SEC,
    HIGH_WHO_IS_RATE_PER_SEC,
    SLOW_RTT_MS,
    AnalysisMetrics,
)

SEVERITY_ORDER = {"CRITICAL": 0, "HIGH": 1, "MEDIUM": 2, "LOW": 3}
MAX_SAMPLE_FRAMES = 20


@dataclass
class Finding:
  """Single root-cause-grouped BACnet health finding with frame references."""

  id: str
  severity: str
  category: str
  cause_key: str
  title: str
  summary: str
  remediation: str
  affected_devices: List[str] = field(default_factory=list)
  affected_objects: List[str] = field(default_factory=list)
  frame_numbers: List[int] = field(default_factory=list)
  total_frames: int = 0
  evidence: Dict[str, Any] = field(default_factory=dict)

  def to_dict(self) -> Dict[str, Any]:
    """Serializes finding to a JSON-compatible dictionary."""
    return asdict(self)


def _cap_frames(frames: List[int]) -> tuple[List[int], int]:
  uniq = sorted(set(frames))
  return uniq[:MAX_SAMPLE_FRAMES], len(uniq)


def generate_findings(metrics: AnalysisMetrics) -> List[Finding]:
  """Synthesizes disjoint, prioritized findings from AnalysisMetrics."""
  raw: List[Finding] = []
  seen_causes: Set[str] = set()

  def _add(
      severity: str,
      category: str,
      cause_key: str,
      title: str,
      summary: str,
      remediation: str,
      devices: List[str],
      objects: List[str],
      frames: List[int],
      evidence: Dict[str, Any],
  ) -> None:
    if cause_key in seen_causes:
      return
    seen_causes.add(cause_key)
    sample_frames, total = _cap_frames(frames)
    raw.append(
        Finding(
            id="",
            severity=severity,
            category=category,
            cause_key=cause_key,
            title=title,
            summary=summary,
            remediation=remediation,
            affected_devices=sorted(set(devices)),
            affected_objects=sorted(set(objects)),
            frame_numbers=sample_frames,
            total_frames=total,
            evidence=evidence,
        )
    )

  # 1. Duplicate BACnet Device Instance IDs (CRITICAL)
  for inst, info in sorted(metrics.duplicate_device_instances.items()):
    eps = info["endpoints"]
    _add(
        severity="CRITICAL",
        category="addressing",
        cause_key=f"duplicate_device_instance:{inst}",
        title=f"Duplicate BACnet device instance {inst}",
        summary=(
            f"Multiple endpoints ({', '.join(eps)}) announced the same "
            f"BACnet device instance {inst} via I-Am."
        ),
        remediation=(
            f"Assign a globally unique BACnet Device Instance ID to one of "
            f"the conflicting controllers ({', '.join(eps)})."
        ),
        devices=eps + [f"device:{inst}"],
        objects=[f"device-{inst}"],
        frames=info["frames"],
        evidence={"device_instance": inst, "endpoints": eps},
    )

  # 2. Router Reject / Busy / Network-Number Disagreement (HIGH)
  for rtr, frames in sorted(metrics.router_reject_frames.items()):
    _add(
        severity="HIGH",
        category="routing",
        cause_key=f"router_reject:{rtr}",
        title=f"Router {rtr} rejecting BACnet messages",
        summary=(
            f"Router {rtr} emitted {len(frames)} Reject-Message-To-Network "
            "frame(s), indicating unreachable networks or stale routing tables."
        ),
        remediation=(
            f"Verify routing table entries and downstream network numbers "
            f"configured on router {rtr}."
        ),
        devices=[rtr],
        objects=[],
        frames=frames,
        evidence={"reject_count": len(frames)},
    )

  for rtr, frames in sorted(metrics.router_busy_frames.items()):
    _add(
        severity="HIGH",
        category="routing",
        cause_key=f"router_busy:{rtr}",
        title=f"Router {rtr} signaling Router-Busy-To-Network",
        summary=(
            f"Router {rtr} emitted {len(frames)} Router-Busy-To-Network "
            "frame(s), indicating downstream trunk congestion or buffer exhaustion."
        ),
        remediation=(
            f"Reduce concurrent polling rates and inspect MS/TP trunk load "
            f"behind router {rtr}."
        ),
        devices=[rtr],
        objects=[],
        frames=frames,
        evidence={"busy_count": len(frames)},
    )

  if len(metrics.network_number_announcements) > 1:
    all_rtrs = sorted({
        r for rtrs in metrics.network_number_announcements.values() for r in rtrs
    })
    _add(
        severity="HIGH",
        category="routing",
        cause_key="router_network_number_disagreement",
        title="Local routers disagree on BACnet network number",
        summary=(
            "Routers announced conflicting local network numbers: "
            + ", ".join(
                f"net {n} ({'/'.join(rtrs)})"
                for n, rtrs in sorted(metrics.network_number_announcements.items())
            )
            + "."
        ),
        remediation=(
            "Standardize the local BACnet/IP network number across all routers "
            "on this broadcast domain."
        ),
        devices=all_rtrs,
        objects=[],
        frames=metrics.network_number_frames,
        evidence={"announcements": metrics.network_number_announcements},
    )

  # 3. Slow / Congested Routed Trunks (grouped by router to keep causes disjoint)
  covered_routed_devices: Set[str] = set()
  for rtr, stats in sorted(metrics.router_latency.items()):
    if stats["slow_count"] > 0 and stats["p95_ms"] >= SLOW_RTT_MS:
      slow_devs = stats["slow_devices"]
      covered_routed_devices.update(slow_devs)
      _add(
          severity="HIGH",
          category="latency",
          cause_key=f"slow_routed_trunk:{rtr}",
          title=f"Slow or congested routed trunk behind router {rtr}",
          summary=(
              f"Routed traffic through {rtr} exhibited p95 RTT of "
              f"{stats['p95_ms']} ms (max {stats['max_ms']} ms, "
              f"{stats['slow_count']} slow replies across {len(slow_devs)} device(s))."
          ),
          remediation=(
              f"Inspect MS/TP trunk wiring, baud rate, token-passing load, "
              f"and polling intervals behind router {rtr}."
          ),
          devices=[rtr] + slow_devs,
          objects=[],
          frames=stats["slow_frames"],
          evidence={
              "p50_ms": stats["p50_ms"],
              "p95_ms": stats["p95_ms"],
              "max_ms": stats["max_ms"],
              "slow_count": stats["slow_count"],
          },
      )

  # 4. Slow Direct BACnet/IP Devices (disjoint from routed trunks)
  for dev, stats in sorted(metrics.device_latency.items()):
    if dev in covered_routed_devices or stats.get("router") is not None:
      continue
    if stats["slow_count"] > 0 and stats["p95_ms"] >= SLOW_RTT_MS:
      _add(
          severity="MEDIUM",
          category="latency",
          cause_key=f"slow_direct_device:{dev}",
          title=f"Slow BACnet/IP responses from {dev}",
          summary=(
              f"Device {dev} had p95 RTT of {stats['p95_ms']} ms "
              f"(max {stats['max_ms']} ms, {stats['slow_count']} slow replies)."
          ),
          remediation=(
              f"Check controller CPU/network load and APDU timeout alignment "
              f"for BACnet/IP device {dev}."
          ),
          devices=[dev],
          objects=[],
          frames=stats["slow_frames"],
          evidence={
              "p50_ms": stats["p50_ms"],
              "p95_ms": stats["p95_ms"],
              "max_ms": stats["max_ms"],
              "slow_count": stats["slow_count"],
          },
      )

  # 5. Retries and Unanswered Confirmed Requests
  for dev, info in sorted(metrics.unanswered_details.items()):
    if info["count"] >= 2:
      _add(
          severity="HIGH" if info["count"] >= 5 else "MEDIUM",
          category="reliability",
          cause_key=f"unanswered_requests:{dev}",
          title=f"Unanswered confirmed requests to {dev}",
          summary=(
              f"{info['count']} confirmed request(s) to {dev} received no reply "
              "within the capture window."
          ),
          remediation=(
              f"Verify reachability, power state, and firewall/UDP-47808 rules "
              f"for {dev}; remove offline devices from active polling lists."
          ),
          devices=[dev],
          objects=[],
          frames=info["frames"],
          evidence={"unanswered_count": info["count"]},
      )

  for dev, info in sorted(metrics.retry_details.items()):
    if info["count"] >= 2:
      _add(
          severity="MEDIUM",
          category="reliability",
          cause_key=f"confirmed_retries:{dev}",
          title=f"Confirmed-request retries toward {dev}",
          summary=(
              f"Observed {info['count']} APDU retry frame(s) targeting {dev} "
              "before receiving an acknowledgement."
          ),
          remediation=(
              f"Increase APDU timeout or reduce polling concurrency toward {dev} "
              "to prevent retry amplification."
          ),
          devices=[dev],
          objects=[],
          frames=info["frames"],
          evidence={"retry_count": info["count"]},
      )

  # 6. Broadcast Storm / Broadcast COV
  if (
      metrics.broadcast_rate_per_sec >= HIGH_BROADCAST_RATE_PER_SEC
      or metrics.broadcast_cov_count >= 5
  ):
    top_srcs = list(metrics.broadcast_sources.keys())[:5]
    frames = list(metrics.broadcast_cov_frames)
    for src in top_srcs:
      frames.extend(metrics.broadcast_sources[src]["frames"])
    _add(
        severity="HIGH" if metrics.broadcast_cov_count >= 10 else "MEDIUM",
        category="broadcast",
        cause_key="excessive_broadcast_or_cov",
        title="Excessive broadcast rate or broadcast COV notifications",
        summary=(
            f"Observed {metrics.broadcast_count} broadcasts "
            f"({metrics.broadcast_rate_per_sec}/s) including "
            f"{metrics.broadcast_cov_count} broadcast COV notification(s)."
        ),
        remediation=(
            "Convert broadcast COV notifications to unicast SubscribeCOV "
            "and throttle top broadcast generators."
        ),
        devices=top_srcs,
        objects=[],
        frames=frames,
        evidence={
            "broadcast_count": metrics.broadcast_count,
            "broadcast_rate_per_sec": metrics.broadcast_rate_per_sec,
            "broadcast_cov_count": metrics.broadcast_cov_count,
        },
    )

  # 7. Excessive / Unbounded Who-Is or Unreachable Target Queries
  eff_dur = max(metrics.capture.duration_sec, 1.0)
  who_is_rate = round(metrics.who_is_count / eff_dur, 3)
  if (
      metrics.unbounded_who_is_count >= 5
      or who_is_rate >= HIGH_WHO_IS_RATE_PER_SEC
      or len(metrics.unanswered_who_is_targets) >= 3
  ):
    frames = list(metrics.unbounded_who_is_frames)
    unans_devs = []
    for inst, flist in metrics.unanswered_who_is_targets.items():
      unans_devs.append(f"device:{inst}")
      frames.extend(flist)
    _add(
        severity="MEDIUM",
        category="discovery",
        cause_key="excessive_discovery_who_is",
        title="Excessive BACnet discovery traffic or unreachable device queries",
        summary=(
            f"Observed {metrics.who_is_count} Who-Is requests "
            f"({metrics.unbounded_who_is_count} unbounded, "
            f"{len(metrics.unanswered_who_is_targets)} unanswered target IDs)."
        ),
        remediation=(
            "Remove offline or decommissioned device IDs from discovery lists "
            "and replace periodic unbounded Who-Is with targeted range queries."
        ),
        devices=unans_devs,
        objects=[],
        frames=frames,
        evidence={
            "who_is_count": metrics.who_is_count,
            "unbounded_who_is_count": metrics.unbounded_who_is_count,
            "unanswered_target_count": len(metrics.unanswered_who_is_targets),
        },
    )

  # 8. Recurring APDU Errors / Rejects / Aborts
  for key, info in sorted(metrics.apdu_errors.items()):
    if info["count"] >= 2:
      _add(
          severity="MEDIUM" if info["count"] >= 5 else "LOW",
          category="apdu_errors",
          cause_key=f"apdu_error:{key}",
          title=(
              f"APDU Error {info['error']} on {info['service']} "
              f"from {info['device']}"
          ),
          summary=(
              f"Device {info['device']} returned {info['count']} "
              f"{info['error']} error(s) for {info['service']}."
          ),
          remediation=(
              f"Audit polling templates targeting {info['device']} and remove "
              "unsupported objects/properties."
          ),
          devices=[info["device"]],
          objects=info["objects"],
          frames=info["frames"],
          evidence={
              "service": info["service"],
              "error": info["error"],
              "count": info["count"],
          },
      )

  for key, info in sorted(metrics.apdu_rejects_aborts.items()):
    _add(
        severity="MEDIUM",
        category="apdu_errors",
        cause_key=f"apdu_{key}",
        title=(
            f"APDU {info['pdu_type'].upper()} (reason={info['reason']}) "
            f"from {info['device']}"
        ),
        summary=(
            f"Device {info['device']} emitted {info['count']} APDU "
            f"{info['pdu_type']}(s) with reason code {info['reason']}."
        ),
        remediation=(
            f"Check max-APDU / segmentation settings and supported services "
            f"for {info['device']}."
        ),
        devices=[info["device"]],
        objects=[],
        frames=info["frames"],
        evidence=info,
    )

  # 9. BBMD / Foreign Device NAKs
  if metrics.bbmd_nak_count > 0:
    _add(
        severity="HIGH",
        category="bbmd",
        cause_key="bbmd_bvlc_nak",
        title="BBMD / BVLC-Result NAK responses detected",
        summary=(
            f"Observed {metrics.bbmd_nak_count} non-zero BVLC-Result NAK "
            "frame(s) indicating BDT/FDT registration or distribution failures."
        ),
        remediation=(
            "Verify BBMD Broadcast Distribution Table (BDT) entries, foreign "
            "device TTL settings, and UDP 47808 reachability."
        ),
        devices=metrics.routers,
        objects=[],
        frames=metrics.bbmd_nak_frames,
        evidence={"nak_count": metrics.bbmd_nak_count},
    )

  # 10. Multiple Time-Synchronization Masters
  if len(metrics.time_sync_masters) > 1:
    masters = sorted(metrics.time_sync_masters.keys())
    frames = [f for fl in metrics.time_sync_masters.values() for f in fl]
    _add(
        severity="MEDIUM",
        category="time_sync",
        cause_key="multiple_time_sync_masters",
        title="Multiple BACnet time-synchronization masters detected",
        summary=(
            f"{len(masters)} distinct sources ({', '.join(masters)}) are "
            "broadcasting BACnet time synchronization."
        ),
        remediation=(
            "Disable BACnet time-sync on all but one authoritative master "
            "controller or BMS server to prevent clock oscillation."
        ),
        devices=masters,
        objects=[],
        frames=frames,
        evidence={"masters": masters},
    )

  # 11. Incomplete Segmented APDU Reassembly
  if metrics.segmented_incomplete > 0:
    _add(
        severity="MEDIUM",
        category="segmentation",
        cause_key="incomplete_segmented_apdu",
        title="Incomplete segmented BACnet APDU transfers",
        summary=(
            f"{metrics.segmented_incomplete} segmented APDU transfer(s) "
            "failed to complete reassembly (missing segments or timeout)."
        ),
        remediation=(
            "Check MTU/fragmentation, APDU segment timeout, and max-segments "
            "capabilities between clients and controllers."
        ),
        devices=[],
        objects=[],
        frames=metrics.segmented_incomplete_frames,
        evidence={
            "reassembled": metrics.segmented_reassembled,
            "incomplete": metrics.segmented_incomplete,
        },
    )

  raw.sort(key=lambda f: (SEVERITY_ORDER.get(f.severity, 9), f.cause_key))
  for idx, finding in enumerate(raw, start=1):
    finding.id = f"F{idx:02d}"
  return raw
