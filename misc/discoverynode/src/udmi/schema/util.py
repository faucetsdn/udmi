import dataclasses
import datetime
import json
import numpy
from typing import Any


def current_time_utc() -> datetime.datetime:
  """Returns the current timestamp in UTC."""
  return datetime.datetime.now(tz=datetime.timezone.utc)


def datetime_from_iso_timestamp(timestamp: str) -> datetime.datetime:
  """Converts a ISO6081 timestamp into a datetime object"""
  return  datetime.datetime.strptime(f"{timestamp}+0000", "%Y-%m-%dT%H:%M:%SZ%z")


def datetime_serializer(timestamp: datetime.datetime) -> str:
  """Serializes datetime object into a ISO6081 format UTC
    
  Args:
    timestamp: datetime object to serialize
  
  """
  return datetime.datetime.astimezone(
      timestamp, datetime.timezone.utc
  ).strftime("%Y-%m-%dT%H:%M:%SZ")


def json_serializer(obj: Any) -> str | float | int:
  """Function that is called for objects that can’t otherwise be serialized
   by the stdlinb json.dumps function.
    
   Primarly used for timestamps.

   Args:
    obj: object to convert into JSON serializabl object.
      """
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
      else:
        new_dict[k] = v
    return new_dict
