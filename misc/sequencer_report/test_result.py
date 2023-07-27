from dataclasses import dataclass
from schema_result import SchemaResult
#ESULT pass discovery.scan periodic_scan ALPHA 5 Sequence complete
@dataclass(eq=True, order=True)
class TestResult:
    bucket: str = ''
    name: str = ''
    result: str = ''
    stage: str = ''
    message: str = ''