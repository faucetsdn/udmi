"""Clean-room, pure-stdlib BACnet/IP PCAP/PCAPNG analyzer for UDMI."""

from dataclasses import dataclass
from typing import Any, Dict, List

from udmi.common.bacnet_analyzer.codec import (
    BacnetFrame,
    SegmentReassembler,
    decode_pcap,
)
from udmi.common.bacnet_analyzer.findings import Finding, generate_findings
from udmi.common.bacnet_analyzer.metrics import (
    AnalysisMetrics,
    CaptureContext,
    compute_metrics,
)


@dataclass
class AnalysisResult:
  """Combined BACnet/IP capture metrics and prioritized findings."""

  metrics: AnalysisMetrics
  findings: List[Finding]

  def to_dict(self) -> Dict[str, Any]:
    """Serializes analysis result to a JSON-compatible dictionary."""
    return {
        "schema_version": "1.0",
        "metrics": self.metrics.to_dict(),
        "findings": [f.to_dict() for f in self.findings],
    }


def analyze_pcap(pcap_path: str) -> AnalysisResult:
  """Analyzes a BACnet/IP PCAP or PCAPNG capture file."""
  metrics = compute_metrics(pcap_path)
  findings = generate_findings(metrics)
  return AnalysisResult(metrics=metrics, findings=findings)


__all__ = [
    "AnalysisMetrics",
    "AnalysisResult",
    "BacnetFrame",
    "CaptureContext",
    "Finding",
    "SegmentReassembler",
    "analyze_pcap",
    "compute_metrics",
    "decode_pcap",
    "generate_findings",
]
