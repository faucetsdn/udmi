import importlib
import inspect
import pkgutil
from typing import get_origin, get_args, Union

import pytest
from dataclasses import is_dataclass

# Import the top-level package where your dataclasses are located
import udmi.schema

def find_all_dataclasses():
  """Dynamically discovers all dataclasses in the udmi.schema package."""
  package = udmi.schema
  all_dataclasses = []

  # Walk through all modules in the specified package
  for _, module_name, _ in pkgutil.walk_packages(package.__path__, package.__name__ + '.'):
    try:
      module = importlib.import_module(module_name)
      # Find all classes in the module that are dataclasses
      for name, obj in inspect.getmembers(module, inspect.isclass):
        if is_dataclass(obj) and obj.__module__ == module.__name__:
          all_dataclasses.append(obj)
    except ImportError:
      # Ignore modules that can't be imported
      continue

  if not all_dataclasses:
    raise RuntimeError("No dataclasses were found. Ensure the package is installed correctly.")

  return all_dataclasses

def generate_mock_data(dataclass_type):
  """Creates a basic dictionary with mock data based on dataclass field types."""
  mock = {}
  for field_name, field_type in dataclass_type.__annotations__.items():
    # Handle Optional[T] by unwrapping it
    origin = get_origin(field_type)
    if origin is Union:
      # Simplistic handling for Optional[T] which is Union[T, None]
      args = get_args(field_type)
      if len(args) == 2 and type(None) in args:
        field_type = next(arg for arg in args if arg is not type(None))
        origin = get_origin(field_type) # Re-evaluate origin

    # Assign default values based on type
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
      # Recursively generate mock data for nested dataclasses
      mock[field_name] = generate_mock_data(field_type)
    else:
      # For complex or unknown types, default to None
      mock[field_name] = None

  return mock

# Use pytest to run the test for every discovered dataclass
@pytest.mark.parametrize("dataclass_to_test", find_all_dataclasses())
def test_dataclass_serialization_roundtrip(dataclass_to_test):
  """
  Tests that a dataclass can be deserialized from a dict and serialized back
  without data loss.
  """
  # 1. Generate mock data based on the dataclass definition
  mock_data = generate_mock_data(dataclass_to_test)

  # 2. Test deserialization (from_dict)
  try:
    instance = dataclass_to_test.from_dict(mock_data)
  except Exception as e:
    pytest.fail(f"Failed to create {dataclass_to_test.__name__} from dict: {e}\nMock data: {mock_data}")

  # 3. Check if the instance was created
  assert instance is not None, f"{dataclass_to_test.__name__} instance should not be None"

  # 4. Test serialization (to_dict)
  try:
    output_dict = instance.to_dict()
  except Exception as e:
    pytest.fail(f"Failed to call to_dict() on {dataclass_to_test.__name__}: {e}")

  # 5. Verify that the round-trip data is consistent
  # Note: We compare keys because some fields might be None in the mock data
  # but excluded from the output dict if they are Optional.
  for key, value in mock_data.items():
    if value is not None:
      assert key in output_dict, f"Key '{key}' missing in the output dict for {dataclass_to_test.__name__}"
      assert output_dict[key] == value, f"Value mismatch for key '{key}' in {dataclass_to_test.__name__}"