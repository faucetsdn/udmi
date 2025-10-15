"""Tests for "correctness" of the generated python classes.

1. Verify that a dataclass can be deserialized from a dict and serialized
back without data loss.
"""
import importlib
import inspect
import pkgutil
from typing import get_origin, get_args, Union

import pytest
from dataclasses import is_dataclass

import udmi.schema

def find_all_dataclasses():
  """Dynamically discovers all dataclasses in the udmi.schema package."""
  package = udmi.schema
  all_dataclasses = []

  for _, module_name, _ in pkgutil.walk_packages(package.__path__,
                                                 package.__name__ + "."):
    try:
      module = importlib.import_module(module_name)
      for _, obj in inspect.getmembers(module, inspect.isclass):
        if is_dataclass(obj) and obj.__module__ == module.__name__:
          all_dataclasses.append(obj)
    except ImportError:
      continue

  if not all_dataclasses:
    raise RuntimeError("No dataclasses were found. Ensure the package "
                       "is installed correctly.")

  return all_dataclasses

def generate_mock_data(dataclass_type):
  """Creates a basic dict with mock data based on dataclass field types."""
  mock = {}
  for field_name, field_type in dataclass_type.__annotations__.items():
    origin = get_origin(field_type)
    if origin is Union:
      args = get_args(field_type)
      if len(args) == 2 and type(None) in args:
        field_type = next(arg for arg in args if arg is not type(None))
        origin = get_origin(field_type)

    if origin in (list, dict):
      mock[field_name] = origin()
    elif field_type is str:
      mock[field_name] = f"test_{field_name}"
    elif field_type is int:
      mock[field_name] = 123
    elif field_type is float:
      mock[field_name] = 123.45
    elif field_type is bool:
      mock[field_name] = True
    elif is_dataclass(field_type):
      mock[field_name] = generate_mock_data(field_type)
    else:
      mock[field_name] = None

  return mock

@pytest.mark.parametrize("dataclass_to_test", find_all_dataclasses())
def test_dataclass_serialization_roundtrip(dataclass_to_test):
  """
  Tests that a dataclass can be deserialized from a dict and serialized back
  without data loss.
  """
  mock_data = generate_mock_data(dataclass_to_test)

  try:
    instance = dataclass_to_test.from_dict(mock_data)
  except Exception as e:
    pytest.fail(f"Failed to create {dataclass_to_test.__name__} "
                f"from dict: {e}\nMock data: {mock_data}")

  assert instance is not None, f"{dataclass_to_test.__name__} " \
                               f"instance should not be None"

  try:
    output_dict = instance.to_dict()
  except Exception as e:
    pytest.fail(f"Failed to call to_dict() on "
                f"{dataclass_to_test.__name__}: {e}")

  for key, value in mock_data.items():
    if value is not None:
      assert key in output_dict, f"Key '{key}' missing in the output " \
                                 f"dict for {dataclass_to_test.__name__}"
      assert output_dict[
               key] == value, f"Value mismatch for key '{key}' " \
                              f"in {dataclass_to_test.__name__}"
