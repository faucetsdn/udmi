"""Tests for mantis.preflight and the prerequisite gate in mantis.cli."""

import pytest

from mantis import cli, preflight

PROVIDER_VARS = ("MANTIS_OFFLINE", "GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_CLOUD_PROJECT", "GCP_PROJECT")


@pytest.fixture
def no_provider_env(monkeypatch):
    for name in PROVIDER_VARS:
        monkeypatch.delenv(name, raising=False)


def _no_adc(monkeypatch):
    import google.auth
    from google.auth.exceptions import DefaultCredentialsError

    def fail():
        raise DefaultCredentialsError("no credentials found")

    monkeypatch.setattr(google.auth, "default", fail)


@pytest.mark.parametrize(
    "name,value",
    [("MANTIS_OFFLINE", "true"), ("GEMINI_API_KEY", "k"), ("GOOGLE_API_KEY", "k"),
     ("GOOGLE_CLOUD_PROJECT", "p"), ("GCP_PROJECT", "p")],
)
def test_provider_is_configured_by_each_environment_variable(no_provider_env, monkeypatch, name, value):
    _no_adc(monkeypatch)
    monkeypatch.setenv(name, value)
    assert preflight.check_provider() is None


def test_provider_is_configured_by_an_adc_project(no_provider_env, monkeypatch):
    import google.auth

    monkeypatch.setattr(google.auth, "default", lambda: (object(), "adc-project"))
    assert preflight.check_provider() is None


def test_adc_without_a_project_is_not_a_provider(no_provider_env, monkeypatch):
    import google.auth

    monkeypatch.setattr(google.auth, "default", lambda: (object(), None))
    problem = preflight.check_provider()
    assert "name no project" in problem
    assert "export GEMINI_API_KEY" in problem


def test_missing_provider_names_every_fix(no_provider_env, monkeypatch):
    _no_adc(monkeypatch)
    problem = preflight.check_provider()
    assert "no application-default credentials" in problem
    for fix in ("GEMINI_API_KEY", "gcloud auth application-default login", "MANTIS_OFFLINE=true"):
        assert fix in problem


def test_missing_module_is_reported_with_the_setup_command():
    problem = preflight.check_modules(["yaml", "mantis_no_such_module"])
    assert "mantis_no_such_module" in problem
    assert "yaml" not in problem.split(":", 1)[1].split("(")[0]
    assert "bin/setup_base" in problem


def test_missing_command_is_reported_with_the_root_setup_command(monkeypatch):
    monkeypatch.setattr(preflight.shutil, "which", lambda command: None)
    problem = preflight.check_command("tmux", "sessions run in tmux")
    assert "'tmux' is not on PATH" in problem
    assert "sudo bin/setup_base" in problem


def test_udmi_common_package_is_importable_under_the_test_pythonpath():
    assert preflight.check_udmi_common() is None


def test_mcp_mode_does_not_require_a_provider():
    names = [getattr(check, "__name__", "") for check in preflight.mantis_checks("mcp")]
    assert "check_provider" not in names
    assert "check_provider" in [getattr(c, "__name__", "") for c in preflight.mantis_checks("cli")]


def test_unknown_mode_is_rejected():
    with pytest.raises(ValueError, match="Unknown Mantis preflight mode"):
        preflight.mantis_checks("workbench")


def test_cli_refuses_to_start_without_a_provider(no_provider_env, monkeypatch, capsys):
    _no_adc(monkeypatch)
    assert cli.main(["What is pointset?"]) == 1
    err = capsys.readouterr().err
    assert "Mantis cannot start until these prerequisites are fixed" in err
    assert "no model provider configured" in err


class _PastTheGate(Exception):
    """Raised by a stub agent: reaching it means the prerequisite gate passed."""


def _gate_on_provider_only(monkeypatch):
    import mantis.agent

    _no_adc(monkeypatch)
    monkeypatch.setattr(preflight, "run", lambda checks: [p for p in [preflight.check_provider()] if p])

    def agent(*args, **kwargs):
        raise _PastTheGate()

    monkeypatch.setattr(mantis.agent, "MantisAgent", agent)


@pytest.mark.parametrize("flag", ["--offline", "--vertex=my-project/us-central1"])
def test_cli_provider_flags_are_applied_before_the_check(no_provider_env, monkeypatch, flag):
    _gate_on_provider_only(monkeypatch)
    with pytest.raises(_PastTheGate):
        cli.main([flag, "What is pointset?"])


def test_cli_mcp_mode_refuses_when_tmux_is_missing(monkeypatch, capsys):
    monkeypatch.setattr(preflight.shutil, "which", lambda command: None)
    assert cli.main(["--mcp"]) == 1
    assert "'tmux' is not on PATH" in capsys.readouterr().err
