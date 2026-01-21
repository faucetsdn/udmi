"""
This module defines the Level enum, representing logging severity levels
aligned with a subset of StackDriver LogSeverity levels.
"""
from enum import IntEnum, unique

@unique
class Level(IntEnum):
  """
  This class is manually curated. This is a subset of the
  StackDriver LogSeverity levels.
  """
  INVALID = -1
  TRACE = 50
  DEBUG = 100
  INFO = 200
  NOTICE = 300
  WARNING = 400
  ERROR = 500
  # Note: 'CRITIAL' typo preserved from Java source
  CRITIAL = 600

  @classmethod
  def from_value(cls, value: int):
    """
    Look up the Enum member by its integer value.
    """
    try:
      return cls(value)
    except ValueError as exc:
      raise ValueError(str(value)) from exc
