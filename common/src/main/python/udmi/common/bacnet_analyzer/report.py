"""Canonical JSON and action-first Markdown report rendering for BACnet PCAPs."""

from collections import Counter
import json
from pathlib import Path
from typing import Any, Dict, List, Optional

from udmi.common.bacnet_analyzer import AnalysisResult


def _format_duration(seconds: float) -> str:
  total = int(round(seconds))
  mins, secs = divmod(total, 60)
  hours, mins = divmod(mins, 60)
  if hours:
    return f"{seconds:.1f} s ({hours}h {mins}m {secs}s)"
  if mins:
    return f"{seconds:.1f} s ({mins}m {secs}s)"
  return f"{seconds:.1f} s"


def _format_frames(frame_numbers: List[int], total_frames: int) -> str:
  if not frame_numbers:
    return "none"
  listed = ", ".join(str(f) for f in frame_numbers[:12])
  extra = total_frames - min(len(frame_numbers), 12)
  return f"{listed} (+{extra} more)" if extra > 0 else listed


def _build_capture_recommendations(result: AnalysisResult) -> List[str]:
  """Generates actionable recommendations to improve capture quality/coverage."""
  ctx = result.metrics.capture
  recs: List[str] = []
  if ctx.short_capture:
    recs.append(
        f"Capture duration is short ({ctx.duration_sec}s); capture at least "
        "10-15 minutes to observe periodic polling, COV bursts, and time-sync."
    )
  if ctx.truncated_packets > 0:
    recs.append(
        f"{ctx.truncated_packets} packet(s) were truncated (snaplen too small); "
        "recapture with `-s 0` to preserve full APDU payloads."
    )
  if ctx.double_captured_frames > 0:
    recs.append(
        f"{ctx.double_captured_frames} double-captured frame(s) detected; "
        "filter SPAN/mirror sessions to a single VLAN or direction to avoid "
        "duplicate frame noise."
    )
  if ctx.vantage_point == "host" and ctx.capture_host:
    recs.append(
        f"Capture reflects a single host vantage point ({ctx.capture_host}, "
        f"{ctx.capture_host_share_pct}% unicast share); peer-to-peer traffic "
        "between other BACnet/IP devices is not visible. Use a switch SPAN "
        "port for full broadcast-domain visibility."
    )
  return recs


def build_analysis_json(
    result: AnalysisResult, capture_id: Optional[str] = None
) -> Dict[str, Any]:
  """Builds the canonical structured `analysis.json` payload."""
  ctx = result.metrics.capture
  resolved_id = capture_id or Path(ctx.pcap_path).stem
  sev_counts = Counter(f.severity for f in result.findings)

  if not result.findings:
    verdict_summary = (
        f"Healthy BACnet/IP capture (score {result.metrics.health_score}/100) "
        f"with no actionable defects across {ctx.deduplicated_frames} frames."
    )
  else:
    top = result.findings[0]
    verdict_summary = (
        f"{result.metrics.health_band.upper()} (score "
        f"{result.metrics.health_score}/100): {len(result.findings)} finding(s); "
        f"top priority [{top.severity}] {top.id} — {top.title}."
    )

  prioritized_actions = [
      {
          "finding_id": f.id,
          "severity": f.severity,
          "category": f.category,
          "title": f.title,
          "remediation": f.remediation,
          "affected_devices": f.affected_devices,
          "affected_objects": f.affected_objects,
          "frame_numbers": f.frame_numbers,
          "total_frames": f.total_frames,
      }
      for f in result.findings
  ]

  return {
      "schema_version": "1.0",
      "capture_id": resolved_id,
      "verdict": {
          "health_score": result.metrics.health_score,
          "health_band": result.metrics.health_band,
          "summary": verdict_summary,
          "finding_counts": {
              "CRITICAL": sev_counts.get("CRITICAL", 0),
              "HIGH": sev_counts.get("HIGH", 0),
              "MEDIUM": sev_counts.get("MEDIUM", 0),
              "LOW": sev_counts.get("LOW", 0),
          },
      },
      "prioritized_actions": prioritized_actions,
      "coverage_and_vantage": {
          "vantage_point": ctx.vantage_point,
          "capture_host": ctx.capture_host,
          "capture_host_share_pct": ctx.capture_host_share_pct,
          "link_type": ctx.link_type,
          "duration_sec": ctx.duration_sec,
          "start_utc": ctx.start_utc,
          "end_utc": ctx.end_utc,
          "total_packets": ctx.total_packets,
          "bacnet_frames": ctx.bacnet_frames,
          "deduplicated_frames": ctx.deduplicated_frames,
          "double_captured_frames": ctx.double_captured_frames,
          "truncated_packets": ctx.truncated_packets,
          "short_capture": ctx.short_capture,
          "defects": ctx.defects,
          "not_assessed": result.metrics.not_assessed,
      },
      "capture_recommendations": _build_capture_recommendations(result),
      "findings": [f.to_dict() for f in result.findings],
      "metrics": result.metrics.to_dict(),
  }


def render_markdown_report(
    result: AnalysisResult, capture_id: Optional[str] = None
) -> str:
  """Renders an action-first Markdown report optimized for human and LLM use."""
  doc = build_analysis_json(result, capture_id=capture_id)
  ctx = result.metrics.capture
  verdict = doc["verdict"]
  m = result.metrics

  lines: List[str] = [
      "# BACnet/IP Capture Analysis Report",
      "",
      f"- **Capture ID**: `{doc['capture_id']}`",
      f"- **Health Verdict**: **{verdict['health_band'].upper()}** "
      f"(Score: **{verdict['health_score']}/100**)",
      f"- **Vantage Point**: `{ctx.vantage_point}`"
      + (
          f" (capture host `{ctx.capture_host}`, "
          f"{ctx.capture_host_share_pct}% unicast share)"
          if ctx.capture_host
          else ""
      ),
      f"- **Duration**: {_format_duration(ctx.duration_sec)} | "
      f"**Total Packets**: {ctx.total_packets} | "
      f"**BACnet/IP Frames**: {ctx.bacnet_frames} "
      f"(deduplicated: {ctx.deduplicated_frames})",
      "",
      "## Prioritized Actions to Fix",
      "",
  ]

  if not result.findings:
    lines.append("No actionable BACnet/IP health defects detected.")
  else:
    for idx, f in enumerate(result.findings, start=1):
      meta_parts: List[str] = []
      if f.affected_devices:
        meta_parts.append(f"Devices: {', '.join(f.affected_devices[:8])}")
      if f.affected_objects:
        meta_parts.append(f"Objects: {', '.join(f.affected_objects[:8])}")
      if f.frame_numbers:
        meta_parts.append(
            f"Frames: {_format_frames(f.frame_numbers, f.total_frames)}"
        )
      meta_suffix = f" ({' | '.join(meta_parts)})" if meta_parts else ""
      lines.append(
          f"{idx}. **[{f.severity}] {f.id} — {f.title}**: "
          f"{f.remediation}{meta_suffix}"
      )

  lines.extend([
      "",
      "## Evidence & Coverage Summary",
      "",
      "| Metric | Value |",
      "| :--- | :--- |",
      f"| Vantage Point | `{ctx.vantage_point}` |",
      f"| Link Type | `{ctx.link_type}` |",
      f"| Capture Window (UTC) | {ctx.start_utc or 'N/A'} to {ctx.end_utc or 'N/A'} |",
      f"| Active Devices / Routers | {len(m.direct_devices) + len(m.routed_devices)} / "
      f"{len(m.routers)} (direct IP: {len(m.direct_devices)}, routed: {len(m.routed_devices)}) |",
      f"| Confirmed Requests / Completed | {m.confirmed_requests} / {m.completed_transactions} "
      f"(retries: {m.retries}, unanswered: {m.unanswered_requests}) |",
      f"| Broadcasts | {m.broadcast_count} ({m.broadcast_rate_per_sec}/s, "
      f"broadcast COV: {m.broadcast_cov_count}) |",
      f"| Segmented APDUs | reassembled: {m.segmented_reassembled}, "
      f"incomplete: {m.segmented_incomplete} |",
      f"| Capture Defects | {', '.join(ctx.defects) if ctx.defects else 'none'} |",
  ])

  if m.not_assessed:
    lines.extend(["", "### Not-Assessed Checks", ""])
    for item in m.not_assessed:
      lines.append(f"- **{item['check']}**: {item['reason']}")

  recs = doc["capture_recommendations"]
  if recs:
    lines.extend(["", "### Capture Recommendations", ""])
    for rec in recs:
      lines.append(f"- {rec}")

  if result.findings:
    lines.extend(["", "## Detailed Findings", ""])
    for f in result.findings:
      lines.extend([
          f"### {f.id}: {f.title}",
          "",
          f"- **Severity / Category**: {f.severity} / `{f.category}` (`{f.cause_key}`)",
          f"- **Affected Devices**: {', '.join(f.affected_devices) if f.affected_devices else 'none'}",
          f"- **Affected Objects**: {', '.join(f.affected_objects) if f.affected_objects else 'none'}",
          f"- **1-Based Wireshark Frames**: {_format_frames(f.frame_numbers, f.total_frames)}",
          f"- **Summary**: {f.summary}",
          f"- **Recommended Action**: {f.remediation}",
          "",
      ])

  return "\n".join(lines).rstrip() + "\n"


def write_report_bundle(
    result: AnalysisResult,
    output_dir: str,
    capture_id: Optional[str] = None,
) -> Dict[str, str]:
  """Writes canonical `analysis.json` and `report.md` into `output_dir`."""
  out = Path(output_dir)
  out.mkdir(parents=True, exist_ok=True)
  json_path = out / "analysis.json"
  md_path = out / "report.md"

  doc = build_analysis_json(result, capture_id=capture_id)
  md = render_markdown_report(result, capture_id=capture_id)

  json_path.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
  md_path.write_text(md, encoding="utf-8")
  return {"json_path": str(json_path), "markdown_path": str(md_path)}
