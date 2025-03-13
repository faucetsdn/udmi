from dataclasses import dataclass


# ESULT pass discovery.scan periodic_scan ALPHA 5 Sequence complete
@dataclass
class RunMetadata:
  timestamp: int = 0
  udmi_commit_hash: str = ''
