from dataclasses import dataclass

#ESULT pass discovery.scan periodic_scan ALPHA 5 Sequence complete
@dataclass(eq=True, order=True)
class SchemaResult:
    schema: str = ''
    result: str = ''
   
