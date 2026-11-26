"""Single-pass BACnet/IP traffic metrics and deterministic health checks."""

from collections import Counter, defaultdict
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional, Set, Tuple

from udmi.common.bacnet_analyzer.codec import (
    BVLC_READ_BDT,
    BVLC_REGISTER_FOREIGN_DEVICE,
    BVLC_RESULT,
    BVLC_WRITE_BDT,
    LINKTYPE_NAMES,
    NET_I_AM_ROUTER,
    NET_NETWORK_NUMBER_IS,
    NET_REJECT_MESSAGE,
    NET_ROUTER_BUSY,
    PDU_ABORT,
    PDU_COMPLEX_ACK,
    PDU_CONFIRMED_REQ,
    PDU_ERROR,
    PDU_REJECT,
    PDU_SIMPLE_ACK,
    PDU_UNCONFIRMED_REQ,
    UNCONF_COV,
    UNCONF_I_AM,
    UNCONF_TIME_SYNC,
    UNCONF_UTC_TIME_SYNC,
    UNCONF_WHO_IS,
    BacnetFrame,
    SegmentReassembler,
    decode_bacnet_packet,
    iter_raw_packets,
)

# Default health thresholds (conservative BACnet/IP field baselines)
SLOW_RTT_MS = 1000.0
DUPLICATE_CAPTURE_WINDOW_SEC = 0.005
TRANSACTION_TIMEOUT_SEC = 10.0
SHORT_CAPTURE_SEC = 60.0
HIGH_BROADCAST_RATE_PER_SEC = 5.0
HIGH_WHO_IS_RATE_PER_SEC = 0.5


def _utc_iso(ts: Optional[float]) -> Optional[str]:
  if ts is None:
    return None
  return (
      datetime.fromtimestamp(ts, tz=timezone.utc)
      .isoformat(timespec="seconds")
      .replace("+00:00", "Z")
  )


def _percentile(sorted_vals: List[float], pct: float) -> float:
  if not sorted_vals:
    return 0.0
  idx = min(len(sorted_vals) - 1, int(round((pct / 100.0) * (len(sorted_vals) - 1))))
  return round(sorted_vals[idx], 2)


@dataclass
class CaptureContext:
  """Capture-level metadata, vantage point, and defect indicators."""

  pcap_path: str
  link_type: str = "UNKNOWN"
  duration_sec: float = 0.0
  start_utc: Optional[str] = None
  end_utc: Optional[str] = None
  total_packets: int = 0
  bacnet_frames: int = 0
  deduplicated_frames: int = 0
  double_captured_frames: int = 0
  truncated_packets: int = 0
  short_capture: bool = False
  vantage_point: str = "unknown"
  capture_host: Optional[str] = None
  capture_host_share_pct: float = 0.0
  defects: List[str] = field(default_factory=list)


@dataclass
class AnalysisMetrics:
  """Structured metrics extracted from a single pass over a BACnet/IP PCAP."""

  capture: CaptureContext
  direct_devices: List[str] = field(default_factory=list)
  routed_devices: List[str] = field(default_factory=list)
  routers: List[str] = field(default_factory=list)
  router_networks: Dict[str, List[int]] = field(default_factory=dict)
  device_instance_map: Dict[int, List[str]] = field(default_factory=dict)
  duplicate_device_instances: Dict[int, Dict[str, Any]] = field(
      default_factory=dict
  )
  confirmed_requests: int = 0
  completed_transactions: int = 0
  retries: int = 0
  unanswered_requests: int = 0
  overall_rtt_ms: Dict[str, float] = field(default_factory=dict)
  device_latency: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  router_latency: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  retry_details: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  unanswered_details: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  broadcast_count: int = 0
  broadcast_rate_per_sec: float = 0.0
  broadcast_sources: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  broadcast_cov_count: int = 0
  broadcast_cov_frames: List[int] = field(default_factory=list)
  who_is_count: int = 0
  unbounded_who_is_count: int = 0
  unbounded_who_is_frames: List[int] = field(default_factory=list)
  unanswered_who_is_targets: Dict[int, List[int]] = field(default_factory=dict)
  apdu_errors: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  apdu_rejects_aborts: Dict[str, Dict[str, Any]] = field(default_factory=dict)
  router_reject_frames: Dict[str, List[int]] = field(default_factory=dict)
  router_busy_frames: Dict[str, List[int]] = field(default_factory=dict)
  network_number_announcements: Dict[int, List[str]] = field(
      default_factory=dict
  )
  network_number_frames: List[int] = field(default_factory=list)
  bbmd_nak_count: int = 0
  bbmd_nak_frames: List[int] = field(default_factory=list)
  bbmd_mgmt_frames: int = 0
  time_sync_masters: Dict[str, List[int]] = field(default_factory=dict)
  segmented_reassembled: int = 0
  segmented_incomplete: int = 0
  segmented_incomplete_frames: List[int] = field(default_factory=list)
  not_assessed: List[Dict[str, str]] = field(default_factory=list)
  health_score: float = 100.0
  health_band: str = "healthy"

  def to_dict(self) -> Dict[str, Any]:
    """Serializes metrics to a JSON-compatible dictionary."""
    return asdict(self)


def _infer_vantage_point(
    ip_participation: Counter, unicast_count: int
) -> Tuple[str, Optional[str], float]:
  """Infers whether capture was taken on an active host or SPAN/router."""
  if unicast_count == 0 or not ip_participation:
    return "unknown", None, 0.0
  top_host, top_count = ip_participation.most_common(1)[0]
  share = (top_count / unicast_count) * 100.0
  if share >= 80.0:
    return "host", top_host, round(share, 1)
  return "span_or_router", top_host, round(share, 1)


def compute_metrics(pcap_path: str) -> AnalysisMetrics:
  """Runs a single streaming pass over a PCAP/PCAPNG and computes metrics."""
  ctx = CaptureContext(pcap_path=pcap_path)
  reassembler = SegmentReassembler()

  first_ts: Optional[float] = None
  last_ts: Optional[float] = None
  linktype_counts: Counter = Counter()

  # Deduplication cache: (src_ip, dst_ip, src_port, dst_port, ip_id, payload)
  recent_sig: Dict[Tuple[Any, ...], float] = {}

  direct_devices: Set[str] = set()
  routed_devices: Set[str] = set()
  routed_to_router: Dict[str, str] = {}
  routers: Set[str] = set()
  router_networks: Dict[str, Set[int]] = defaultdict(set)

  # Device instance announcements: instance -> {endpoint: [frames]}
  dev_inst_endpoints: Dict[int, Dict[str, List[int]]] = defaultdict(
      lambda: defaultdict(list)
  )
  seen_i_am_instances: Set[int] = set()

  # Confirmed transaction tracking: (client, server, invoke_id) -> state dict
  inflight: Dict[Tuple[str, str, int], Dict[str, Any]] = {}
  all_rtts: List[float] = []
  dev_rtts: Dict[str, List[float]] = defaultdict(list)
  dev_slow_frames: Dict[str, List[int]] = defaultdict(list)
  router_rtts: Dict[str, List[float]] = defaultdict(list)
  router_slow_frames: Dict[str, List[int]] = defaultdict(list)
  router_slow_devices: Dict[str, Set[str]] = defaultdict(set)

  retry_frames: Dict[str, List[int]] = defaultdict(list)
  unanswered_frames: Dict[str, List[int]] = defaultdict(list)
  confirmed_requests = 0
  completed_transactions = 0
  retries = 0

  broadcast_count = 0
  broadcast_src_frames: Dict[str, List[int]] = defaultdict(list)
  broadcast_cov_frames: List[int] = []
  who_is_count = 0
  unbounded_who_is_frames: List[int] = []
  targeted_who_is: Dict[int, List[int]] = defaultdict(list)

  apdu_errors: Dict[str, Dict[str, Any]] = {}
  apdu_rejects_aborts: Dict[str, Dict[str, Any]] = {}
  router_reject_frames: Dict[str, List[int]] = defaultdict(list)
  router_busy_frames: Dict[str, List[int]] = defaultdict(list)
  net_num_announcers: Dict[int, Set[str]] = defaultdict(set)
  net_num_frames: List[int] = []

  bbmd_nak_frames: List[int] = []
  bbmd_mgmt_frames = 0
  time_sync_masters: Dict[str, List[int]] = defaultdict(list)

  unicast_count = 0
  ip_participation: Counter = Counter()

  for pkt in iter_raw_packets(pcap_path):
    ctx.total_packets += 1
    if pkt.caplen < pkt.origlen:
      ctx.truncated_packets += 1
    linktype_counts[pkt.linktype] += 1
    if first_ts is None or pkt.timestamp < first_ts:
      first_ts = pkt.timestamp
    if last_ts is None or pkt.timestamp > last_ts:
      last_ts = pkt.timestamp

    frame = decode_bacnet_packet(pkt)
    if frame is None:
      continue
    ctx.bacnet_frames += 1

    # Duplicate-capture suppression (e.g. mirrored VLAN / loopback capture)
    sig = (
        frame.src_ip,
        frame.dst_ip,
        frame.src_port,
        frame.dst_port,
        frame.ip_id,
        frame.raw_udp_payload,
    )
    prev_ts = recent_sig.get(sig)
    if (
        prev_ts is not None
        and (frame.timestamp - prev_ts) <= DUPLICATE_CAPTURE_WINDOW_SEC
    ):
      ctx.double_captured_frames += 1
      continue
    recent_sig[sig] = frame.timestamp
    ctx.deduplicated_frames += 1

    # Vantage-point participation (unicast frames only)
    if not frame.is_broadcast:
      unicast_count += 1
      ip_participation[frame.ip_src] += 1
      ip_participation[frame.ip_dst] += 1

    # Device & router topology tracking
    if frame.snet is not None and frame.sadr:
      routed_devices.add(frame.src_endpoint)
      routers.add(frame.ip_src)
      routed_to_router[frame.src_endpoint] = frame.ip_src
      router_networks[frame.ip_src].add(frame.snet)
    else:
      direct_devices.add(frame.src_endpoint)

    if frame.dnet is not None and frame.dadr:
      routed_devices.add(frame.dst_endpoint)
      if not frame.is_broadcast:
        routers.add(frame.ip_dst)
        routed_to_router[frame.dst_endpoint] = frame.ip_dst
        router_networks[frame.ip_dst].add(frame.dnet)

    # BVLC / BBMD management
    if frame.bvlc_function in (
        BVLC_REGISTER_FOREIGN_DEVICE,
        BVLC_WRITE_BDT,
        BVLC_READ_BDT,
        BVLC_RESULT,
    ):
      bbmd_mgmt_frames += 1
      if (
          frame.bvlc_function == BVLC_RESULT
          and frame.bvlc_result_code is not None
          and frame.bvlc_result_code != 0
      ):
        bbmd_nak_frames.append(frame.frame_no)

    # Broadcast tracking
    if frame.is_broadcast:
      broadcast_count += 1
      broadcast_src_frames[frame.src_endpoint].append(frame.frame_no)

    # Network-layer messages
    if frame.is_network_msg:
      routers.add(frame.ip_src)
      if frame.network_msg_type == NET_I_AM_ROUTER:
        for net in frame.network_numbers:
          router_networks[frame.ip_src].add(net)
      elif frame.network_msg_type == NET_REJECT_MESSAGE:
        router_reject_frames[frame.ip_src].append(frame.frame_no)
      elif frame.network_msg_type == NET_ROUTER_BUSY:
        router_busy_frames[frame.ip_src].append(frame.frame_no)
      elif frame.network_msg_type == NET_NETWORK_NUMBER_IS:
        for net in frame.network_numbers:
          net_num_announcers[net].add(frame.ip_src)
        net_num_frames.append(frame.frame_no)
      continue

    # Segmented APDU reassembly
    if frame.segmented:
      reassembler.feed(frame)

    # Unconfirmed services
    if frame.pdu_type == PDU_UNCONFIRMED_REQ:
      if frame.service_choice == UNCONF_I_AM and frame.device_instance is not None:
        seen_i_am_instances.add(frame.device_instance)
        dev_inst_endpoints[frame.device_instance][frame.src_endpoint].append(
            frame.frame_no
        )
      elif frame.service_choice == UNCONF_WHO_IS:
        who_is_count += 1
        if frame.who_is_low is None and frame.who_is_high is None:
          unbounded_who_is_frames.append(frame.frame_no)
        elif (
            frame.who_is_low is not None
            and frame.who_is_high is not None
            and frame.who_is_low == frame.who_is_high
        ):
          targeted_who_is[frame.who_is_low].append(frame.frame_no)
      elif frame.service_choice == UNCONF_COV and frame.is_broadcast:
        broadcast_cov_frames.append(frame.frame_no)
      elif frame.service_choice in (UNCONF_TIME_SYNC, UNCONF_UTC_TIME_SYNC):
        time_sync_masters[frame.src_endpoint].append(frame.frame_no)
      continue

    # Confirmed request / reply correlation
    if frame.pdu_type == PDU_CONFIRMED_REQ and frame.invoke_id is not None:
      if frame.segmented and frame.seq_num not in (None, 0):
        continue
      confirmed_requests += 1
      key = (frame.src_endpoint, frame.dst_endpoint, frame.invoke_id)
      prev = inflight.get(key)
      if prev is not None:
        age = frame.timestamp - float(prev["ts"])
        if age <= TRANSACTION_TIMEOUT_SEC and prev["service"] == frame.service_choice:
          retries += 1
          retry_frames[frame.dst_endpoint].append(frame.frame_no)
          prev["ts"] = frame.timestamp
          continue
        unanswered_frames[prev["dst"]].append(int(prev["frame_no"]))
      inflight[key] = {
          "ts": frame.timestamp,
          "frame_no": frame.frame_no,
          "service": frame.service_choice,
          "service_name": frame.service_name,
          "object_id": frame.object_id,
          "dst": frame.dst_endpoint,
          "router": routed_to_router.get(frame.dst_endpoint),
      }
      continue

    if (
        frame.pdu_type
        in (PDU_SIMPLE_ACK, PDU_COMPLEX_ACK, PDU_ERROR, PDU_REJECT, PDU_ABORT)
        and frame.invoke_id is not None
    ):
      if frame.segmented and frame.more_follows:
        continue
      reply_key = (frame.dst_endpoint, frame.src_endpoint, frame.invoke_id)
      req = inflight.pop(reply_key, None)
      if req is not None:
        completed_transactions += 1
        rtt_ms = max(0.0, (frame.timestamp - float(req["ts"])) * 1000.0)
        all_rtts.append(rtt_ms)
        server = frame.src_endpoint
        dev_rtts[server].append(rtt_ms)
        router_ip = routed_to_router.get(server) or req.get("router")
        if router_ip:
          router_rtts[router_ip].append(rtt_ms)
        if rtt_ms >= SLOW_RTT_MS:
          dev_slow_frames[server].append(frame.frame_no)
          if router_ip:
            router_slow_frames[router_ip].append(frame.frame_no)
            router_slow_devices[router_ip].add(server)

      if frame.pdu_type == PDU_ERROR:
        err_key = f"{frame.src_endpoint}|{frame.service_name}|{frame.error_label}"
        bucket = apdu_errors.setdefault(
            err_key,
            {
                "device": frame.src_endpoint,
                "service": frame.service_name,
                "error": frame.error_label,
                "count": 0,
                "objects": set(),
                "frames": [],
            },
        )
        bucket["count"] += 1
        bucket["frames"].append(frame.frame_no)
        if req and req.get("object_id"):
          bucket["objects"].add(req["object_id"])
      elif frame.pdu_type in (PDU_REJECT, PDU_ABORT):
        kind = "reject" if frame.pdu_type == PDU_REJECT else "abort"
        ra_key = f"{frame.src_endpoint}|{kind}|{frame.abort_or_reject_reason}"
        bucket = apdu_rejects_aborts.setdefault(
            ra_key,
            {
                "device": frame.src_endpoint,
                "pdu_type": kind,
                "reason": frame.abort_or_reject_reason,
                "count": 0,
                "frames": [],
            },
        )
        bucket["count"] += 1
        bucket["frames"].append(frame.frame_no)

  # Flush remaining unanswered requests & incomplete segments
  reassembler.flush_incomplete()
  for req in inflight.values():
    if last_ts is None or (last_ts - float(req["ts"])) >= 1.0:
      unanswered_frames[req["dst"]].append(int(req["frame_no"]))

  # Finalize capture context
  if first_ts is not None and last_ts is not None:
    ctx.duration_sec = round(max(0.0, last_ts - first_ts), 2)
    ctx.start_utc = _utc_iso(first_ts)
    ctx.end_utc = _utc_iso(last_ts)
  ctx.short_capture = ctx.duration_sec < SHORT_CAPTURE_SEC
  if linktype_counts:
    top_lt = linktype_counts.most_common(1)[0][0]
    ctx.link_type = f"{LINKTYPE_NAMES.get(top_lt, 'LINKTYPE') }({top_lt})"
  vp, cap_host, cap_share = _infer_vantage_point(ip_participation, unicast_count)
  ctx.vantage_point = vp
  ctx.capture_host = cap_host
  ctx.capture_host_share_pct = cap_share

  if ctx.truncated_packets > 0:
    ctx.defects.append(f"truncated_packets:{ctx.truncated_packets}")
  if ctx.double_captured_frames > 0:
    ctx.defects.append(f"double_captured_frames:{ctx.double_captured_frames}")
  if ctx.short_capture:
    ctx.defects.append(f"short_capture:{ctx.duration_sec}s")

  # Duplicate device instances
  duplicate_instances: Dict[int, Dict[str, Any]] = {}
  device_instance_map: Dict[int, List[str]] = {}
  for inst, ep_map in sorted(dev_inst_endpoints.items()):
    eps = sorted(ep_map.keys())
    device_instance_map[inst] = eps
    if len(eps) > 1:
      frames_list: List[int] = []
      for ep in eps:
        frames_list.extend(ep_map[ep])
      duplicate_instances[inst] = {
          "endpoints": eps,
          "frames": sorted(frames_list),
      }

  # Latency summaries
  all_rtts.sort()
  overall_rtt_ms = (
      {
          "count": len(all_rtts),
          "p50": _percentile(all_rtts, 50),
          "p95": _percentile(all_rtts, 95),
          "max": round(all_rtts[-1], 2),
      }
      if all_rtts
      else {}
  )

  device_latency: Dict[str, Dict[str, Any]] = {}
  for dev, rtts in sorted(dev_rtts.items()):
    rtts.sort()
    device_latency[dev] = {
        "count": len(rtts),
        "p50_ms": _percentile(rtts, 50),
        "p95_ms": _percentile(rtts, 95),
        "max_ms": round(rtts[-1], 2),
        "slow_count": len(dev_slow_frames.get(dev, [])),
        "slow_frames": dev_slow_frames.get(dev, []),
        "router": routed_to_router.get(dev),
    }

  router_latency: Dict[str, Dict[str, Any]] = {}
  for rtr, rtts in sorted(router_rtts.items()):
    rtts.sort()
    router_latency[rtr] = {
        "count": len(rtts),
        "p50_ms": _percentile(rtts, 50),
        "p95_ms": _percentile(rtts, 95),
        "max_ms": round(rtts[-1], 2),
        "slow_count": len(router_slow_frames.get(rtr, [])),
        "slow_frames": router_slow_frames.get(rtr, []),
        "slow_devices": sorted(router_slow_devices.get(rtr, set())),
    }

  # Convert error sets to sorted lists for JSON serialization
  serialized_errors: Dict[str, Dict[str, Any]] = {}
  for k, v in sorted(apdu_errors.items()):
    serialized_errors[k] = {
        "device": v["device"],
        "service": v["service"],
        "error": v["error"],
        "count": v["count"],
        "objects": sorted(v["objects"]),
        "frames": v["frames"],
    }

  eff_duration = max(ctx.duration_sec, 1.0)
  bcast_rate = round(broadcast_count / eff_duration, 3)

  unanswered_who_is = {
      inst: frames
      for inst, frames in sorted(targeted_who_is.items())
      if inst not in seen_i_am_instances
  }

  # Not-assessed checks
  not_assessed: List[Dict[str, str]] = []
  if not time_sync_masters:
    not_assessed.append({
        "check": "multiple_time_sync_masters",
        "reason": "no TimeSynchronization or UTCTimeSynchronization frames observed",
    })
  if len(net_num_announcers) < 1 or sum(
      len(v) for v in net_num_announcers.values()
  ) < 2:
    not_assessed.append({
        "check": "router_network_number_disagreement",
        "reason": "fewer than 2 routers announced Network-Number-Is",
    })
  if bbmd_mgmt_frames == 0:
    not_assessed.append({
        "check": "bbmd_foreign_device_health",
        "reason": "no BVLC management frames (Register-Foreign-Device/BDT/Result) observed",
    })

  # Compute transparent health score (0..100)
  penalty = 0.0
  penalty += min(30.0, 25.0 * len(duplicate_instances))
  if confirmed_requests > 0:
    retry_pct = (retries / confirmed_requests) * 100.0
    unans_pct = (
        sum(len(f) for f in unanswered_frames.values()) / confirmed_requests
    ) * 100.0
    penalty += min(20.0, retry_pct * 1.5)
    penalty += min(20.0, unans_pct * 1.5)
  if all_rtts and _percentile(all_rtts, 95) >= SLOW_RTT_MS:
    penalty += 10.0
  if bcast_rate >= HIGH_BROADCAST_RATE_PER_SEC or len(broadcast_cov_frames) >= 10:
    penalty += 10.0
  if router_reject_frames or router_busy_frames:
    penalty += 10.0
  if bbmd_nak_frames:
    penalty += 10.0
  if len(time_sync_masters) > 1:
    penalty += 5.0
  if serialized_errors:
    penalty += min(10.0, 2.0 * len(serialized_errors))
  if reassembler.incomplete_count > 0:
    penalty += 5.0

  score = round(max(0.0, 100.0 - penalty), 2)
  band = "healthy" if score >= 85.0 else ("warning" if score >= 60.0 else "critical")

  return AnalysisMetrics(
      capture=ctx,
      direct_devices=sorted(direct_devices),
      routed_devices=sorted(routed_devices),
      routers=sorted(routers),
      router_networks={r: sorted(nets) for r, nets in sorted(router_networks.items())},
      device_instance_map=device_instance_map,
      duplicate_device_instances=duplicate_instances,
      confirmed_requests=confirmed_requests,
      completed_transactions=completed_transactions,
      retries=retries,
      unanswered_requests=sum(len(f) for f in unanswered_frames.values()),
      overall_rtt_ms=overall_rtt_ms,
      device_latency=device_latency,
      router_latency=router_latency,
      retry_details={
          dev: {"count": len(f), "frames": f}
          for dev, f in sorted(retry_frames.items())
      },
      unanswered_details={
          dev: {"count": len(f), "frames": f}
          for dev, f in sorted(unanswered_frames.items())
      },
      broadcast_count=broadcast_count,
      broadcast_rate_per_sec=bcast_rate,
      broadcast_sources={
          ep: {"count": len(f), "frames": f[:20]}
          for ep, f in sorted(
              broadcast_src_frames.items(), key=lambda kv: -len(kv[1])
          )[:10]
      },
      broadcast_cov_count=len(broadcast_cov_frames),
      broadcast_cov_frames=broadcast_cov_frames,
      who_is_count=who_is_count,
      unbounded_who_is_count=len(unbounded_who_is_frames),
      unbounded_who_is_frames=unbounded_who_is_frames,
      unanswered_who_is_targets=unanswered_who_is,
      apdu_errors=serialized_errors,
      apdu_rejects_aborts=apdu_rejects_aborts,
      router_reject_frames=dict(router_reject_frames),
      router_busy_frames=dict(router_busy_frames),
      network_number_announcements={
          net: sorted(rtrs) for net, rtrs in sorted(net_num_announcers.items())
      },
      network_number_frames=net_num_frames,
      bbmd_nak_count=len(bbmd_nak_frames),
      bbmd_nak_frames=bbmd_nak_frames,
      bbmd_mgmt_frames=bbmd_mgmt_frames,
      time_sync_masters=dict(time_sync_masters),
      segmented_reassembled=reassembler.reassembled_count,
      segmented_incomplete=reassembler.incomplete_count,
      segmented_incomplete_frames=reassembler.incomplete_frames,
      not_assessed=not_assessed,
      health_score=score,
      health_band=band,
  )
