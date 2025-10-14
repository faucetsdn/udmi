import local_config
import pytest
import copy

def test_read_config():
  """ Tests read config merging from extra dir"""
  config = local_config.read_config("tests/data/override.json")
  assert config == {'configs_dir': 'tests/data/configs', 'b': {'x': 1}, "another": "one"}

  config = local_config.read_config("tests/data/no_override.json")
  assert config == {'a': 1}

  # TODO: Determine if ignoring a bad dir is the right behaviour
  config = local_config.read_config("tests/data/bad_dir.json")
  assert config == {'configs_dir': 'tests/where'}

def test_merge_dicts():
  # Simple merge, no overlap
  base = {'a': 1, 'b': 2}
  override = {'c': 3, 'd': 4}
  expected = {'a': 1, 'b': 2, 'c': 3, 'd': 4}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected

  # Override simple value
  base = {'a': 1, 'b': 2}
  override = {'b': 3, 'c': 4}
  expected = {'a': 1, 'b': 3, 'c': 4}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected

  # Nested merge
  base = {'a': 1, 'b': {'x': 10, 'y': 20}}
  override = {'b': {'y': 30, 'z': 40}, 'c': 3}
  expected = {'a': 1, 'b': {'x': 10, 'y': 30, 'z': 40}, 'c': 3}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected

  # Override dict with simple value
  base = {'a': 1, 'b': {'x': 10}}
  override = {'b': 2}
  expected = {'a': 1, 'b': 2}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected

  # Override simple value with dict
  base = {'a': 1, 'b': 2}
  override = {'b': {'x': 10}}
  expected = {'a': 1, 'b': {'x': 10}}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected

  # mpty override
  base = {'a': 1, 'b': 2}
  override = {}
  expected = {'a': 1, 'b': 2}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected

  # Deep nested merge
  base = {'a': {'b': {'c': 1, 'd': 2}}}
  override = {'a': {'b': {'d': 3, 'e': 4}, 'f': 5}}
  expected = {'a': {'b': {'c': 1, 'd': 3, 'e': 4}, 'f': 5}}
  assert local_config.merge_dicts(copy.deepcopy(base), override) == expected