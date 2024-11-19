import dataclasses
import datetime
import json
import numpy
from typing import Any


def current_time_utc():
  """Returns the current timestamp in UTC."""
  return datetime.datetime.now(tz=datetime.timezone.utc)


def datetime_serializer(timestamp: datetime.datetime):
  return datetime.datetime.astimezone(
      timestamp, datetime.timezone.utc
  ).strftime("%Y-%m-%dT%H:%M:%SZ")


def json_serializer(obj: Any):
  if isinstance(obj, datetime.datetime):
    return datetime_serializer(obj)
  
  if isinstance(obj, numpy.integer):
    return int(obj)
  
  if isinstance(obj, numpy.floating):
    return float(obj)
  
  raise TypeError(f"Type {type(obj)} is not serializable")


def deep_remove(
    target: dict,
    keys: list[Any] = None,
    values: list[Any] = None,
) -> dict:
  """Removes a list of keys and values from a nested dictionary."""

  if keys is None:
    keys = []

  if values is None:
    values = []

  if isinstance(target, dict):
    new_dict = {}
    for k, v in target.items():
      if v in values or k in keys:
        continue
      elif isinstance(v, dict):
        new_dict[k] = deep_remove(v, keys, values)
      elif isinstance(v, list):
        new_dict[k] = deep_remove(v, keys, values)
      else:
        new_dict[k] = v
    return new_dict
