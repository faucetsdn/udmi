from dataclasses import dataclass, field
from typing import Any, Dict, Optional

@dataclass(frozen=True)
class TriageFailure:
    """
    Represents a single detected test failure occurrence.
    
    Attributes:
        test_name: Name of the failed test case (e.g., 'valid_serial_no').
        category: Category of the failure (e.g., 'system', 'security').
        suite: Originating test suite ('sequencer', 'itemized', or 'both').
        device_id: ID of the device under test (e.g., 'AHU-1').
        occurrence_index: Index of this failure occurrence in the run (0-indexed).
        metadata: Arbitrary additional context payload mapping (e.g., run directory paths, logs mapping).
    """
    test_name: str
    category: str
    suite: str
    device_id: str = "AHU-1"
    occurrence_index: int = 0
    metadata: Dict[str, Any] = field(default_factory=dict)


@dataclass(frozen=True)
class TriageReportResult:
    """
    Represents the structured output outcome of a completed triage analysis.
    
    Attributes:
        test_id: ID of the analyzed test case.
        category: Outcome category of the test.
        suite: Originating test suite.
        breakpoint: The isolated mechanism/defect summary or warning message.
        insufficient: True if the analysis aborted due to insufficient log data.
        report_link: Relative path link to the generated diagnostic markdown report.
    """
    test_id: str
    category: str
    suite: str
    breakpoint: str
    insufficient: bool
    report_link: str
