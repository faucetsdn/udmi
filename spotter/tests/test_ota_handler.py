import json
import pytest
import subprocess
from unittest.mock import patch, MagicMock

from spotter.spotter.ota.handler import SpotterOTAHandler

@pytest.fixture
def ota_handler():
    handler = SpotterOTAHandler(
        hardware_make="PyUDMI",
        hardware_model="Spotter-v1",
        current_dependencies={"libA": "v1.0"}
    )
    handler.manifest_file_path = "spotter_manifest.json"
    return handler

def _mock_git_show(commit_hash, manifest_content):
    def side_effect(cmd, **kwargs):
        if "fetch" in cmd:
            return MagicMock()
        if "show" in cmd and commit_hash in cmd[2]:
            result = MagicMock()
            result.stdout = json.dumps(manifest_content)
            return result
        raise subprocess.CalledProcessError(1, cmd, stderr=b"Git error")
    return side_effect

@patch("subprocess.run")
def test_1_happy_path(mock_run, ota_handler):
    manifest = {
        "hardware_make": "PyUDMI",
        "hardware_model": "Spotter-v1",
        "dependencies": {"libA": "v1.0"}
    }
    mock_run.side_effect = _mock_git_show("abcd123", manifest)

    # Process
    commit = ota_handler.process("firmware", b"abcd123")
    assert commit == "abcd123"

    # Post Process
    mock_run.reset_mock()
    mock_run.side_effect = None # Remove side effect for post process
    with patch("sys.exit") as mock_exit:
        ota_handler.post_process("firmware", commit)
        mock_run.assert_called_once()
        assert "checkout" in mock_run.call_args[0][0]
        assert "abcd123" in mock_run.call_args[0][0]
        mock_exit.assert_called_once_with(0)

@patch("subprocess.run")
def test_2_hardware_mismatch(mock_run, ota_handler):
    manifest = {
        "hardware_make": "WRONG",
        "hardware_model": "Spotter-v1",
        "dependencies": {"libA": "v1.0"}
    }
    mock_run.side_effect = _mock_git_show("abcd123", manifest)

    with pytest.raises(ValueError, match="Hardware mismatch"):
        ota_handler.process("firmware", b"abcd123")

@patch("subprocess.run")
def test_3_dependency_mismatch(mock_run, ota_handler):
    manifest = {
        "hardware_make": "PyUDMI",
        "hardware_model": "Spotter-v1",
        "dependencies": {"libA": "v2.0"}  # requires v2.0 but we have v1.0
    }
    mock_run.side_effect = _mock_git_show("abcd123", manifest)

    with pytest.raises(ValueError, match="Dependency mismatch"):
        ota_handler.process("firmware", b"abcd123")

@patch("subprocess.run")
def test_4_corrupted_payload(mock_run, ota_handler):
    # Mock git show returning non-JSON
    def side_effect(cmd, **kwargs):
        if "show" in cmd:
            res = MagicMock()
            res.stdout = "NOT_JSON"
            return res
        return MagicMock()

    mock_run.side_effect = side_effect

    with pytest.raises(RuntimeError, match="Corrupted payload"):
        ota_handler.process("firmware", b"abcd123")

@patch("subprocess.run")
def test_5_git_failure_on_post_process(mock_run, ota_handler):
    # Post Process with git failure
    mock_run.side_effect = subprocess.CalledProcessError(1, ["git"], stderr=b"fatal: cannot parse object")

    with patch("sys.exit") as mock_exit:
        # Should catch and handle gracefully without exiting
        ota_handler.post_process("firmware", "abcd123")
        mock_exit.assert_not_called()
