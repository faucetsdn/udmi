import test_local


def test_deep_merge():
    dict1 = {"a": {"b": 1, "c": {"d": 2}}}
    dict2 = {"a": {"c": 3, "c": {"d": 3}}}
    result = {"a": {"b": 1, "c": 3, "c": {"d": 3}}}
    assert test_local.deep_merge(dict1, dict2) == result
