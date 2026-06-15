import json
import sys
from dataclasses import dataclass, field
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional
from google.genai import types
from pydantic import BaseModel, Field

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


class HypothesisEvaluation(BaseModel):
    title: str = Field(description="Title of the candidate hypothesis")
    status: str = Field(description="Final evaluation status: VERIFIED, DISPROVED, or UNVERIFIED")
    evidence: str = Field(description="Evidence or findings that support or disprove the hypothesis")


class RootCauseAnalysis(BaseModel):
    culprit_file: Optional[str] = Field(None, description="Path to the file containing the bug/defect, relative to workspace root")
    culprit_line_range: Optional[str] = Field(None, description="Culprit lines range (e.g., 'L120-L135')")
    explanation: str = Field(description="Detailed engineering explanation of the bug mechanism")


class TriageReportModel(BaseModel):
    """Structured Pydantic representation of a Mantis triage report."""
    target_id: str = Field(description="Unique identifier of the failing target")
    timestamp: str = Field(default_factory=lambda: datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"), description="ISO 8601 compilation timestamp")
    status: str = Field(description="Status of triage (e.g. SUCCESS, PARTIAL_FAIL_OPEN, FAILED)")
    verdict: str = Field(description="Final verdict: VERIFIED_DEFECT, FLAKY_ENVIRONMENT, or UNKNOWN")
    summary: str = Field(description="Dense root-cause diagnostic synthesis summary")
    hypotheses_evaluated: List[HypothesisEvaluation] = Field(default_factory=list, description="List of all hypotheses evaluated by the analyst")
    root_cause_analysis: Optional[RootCauseAnalysis] = Field(None, description="Root cause detail payload if a defect was verified")


async def extract_structured_report(
    client: Any,
    target_id: str,
    markdown_report: str,
    status: str = "SUCCESS",
    model_name: str = "gemini-3.5-flash"
) -> TriageReportModel:
    """
    Uses Gemini structured extraction to parse an unstructured markdown triage report
    into a structured Pydantic model.
    """
    if not markdown_report:
        return TriageReportModel(
            target_id=target_id,
            status="FAILED",
            verdict="UNKNOWN",
            summary="Empty report",
            hypotheses_evaluated=[],
            root_cause_analysis=None
        )

    # If it is a partial fail-open report, we can parse it directly
    if "Partial Triage Report (Fail-Open)" in markdown_report:
        status = "PARTIAL_FAIL_OPEN"

    prompt = (
        f"You are a Structured Triage Report Extractor. Read the markdown triage report below "
        f"and populate the required JSON schema fields.\n\n"
        f"Markdown Report:\n{markdown_report}"
    )

    try:
        response = await client.aio.models.generate_content(
            model=model_name,
            contents=prompt,
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=TriageReportModel,
                temperature=0.1
            )
        )
        if response.text:
            data = json.loads(response.text.strip())
            # Ensure target_id and status are set correctly from caller context
            data["target_id"] = target_id
            data["status"] = status
            return TriageReportModel(**data)
    except Exception as e:
        print(f"Warning: Failed to extract structured JSON report via Gemini: {e}", file=sys.stderr)
    
    # Fallback return in case of API failure
    return TriageReportModel(
        target_id=target_id,
        status=status,
        verdict="UNKNOWN",
        summary="Fallback extraction: " + markdown_report[:500],
        hypotheses_evaluated=[],
        root_cause_analysis=None
    )
