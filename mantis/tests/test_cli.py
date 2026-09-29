"""Unit tests for mantis.cli."""

import pytest
from mantis.cli import main


def test_cli_help(capsys):
    res = main(["--help"])
    assert res == 0
    captured = capsys.readouterr()
    assert "Mantis: Autonomous UDMI Agent" in captured.out


def test_cli_version(capsys):
    res = main(["--version"])
    assert res == 0
    captured = capsys.readouterr()
    assert "Mantis v2" in captured.out


def test_cli_headless_query(capsys):
    res = main(["--offline", "What are the required fields in pointset schema?"])
    assert res == 0
    captured = capsys.readouterr()
    assert "Schema: `pointset`" in captured.out or "pointset" in captured.out
