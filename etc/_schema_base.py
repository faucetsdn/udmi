"""gencode/python/udmi/schema/_base.py"""
from dataclasses import dataclass
from dataclasses_json import DataClassJsonMixin

@dataclass
class DataModel(DataClassJsonMixin):
  """Base class for all generated UDMI models."""
  pass
