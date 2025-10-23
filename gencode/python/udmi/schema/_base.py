"""gencode/python/udmi/schema/_base.py"""
from dataclasses import dataclass
from dataclasses_json import dataclass_json

@dataclass_json
@dataclass
class DataModel:
  """Base class for all generated UDMI models."""
  pass
