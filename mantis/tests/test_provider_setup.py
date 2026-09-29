"""Tests for the one-time provider setup (`bin/mantis setup`)."""

import json
import os

import pytest

from mantis import cli, preflight, provider_setup

PROVIDER_VARS = (
    "MANTIS_OFFLINE", "GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_CLOUD_PROJECT",
    "GCP_PROJECT", "GOOGLE_CLOUD_REGION", "GCP_REGION",
)


@pytest.fixture(autouse=True)
def clean_env(monkeypatch):
    for name in PROVIDER_VARS:
        monkeypatch.delenv(name, raising=False)


@pytest.fixture
def adc_ok(monkeypatch):
    monkeypatch.setattr(preflight, "adc_problem", lambda require_project=False: None)


def _document():
    with open(provider_setup.config_path()) as fh:
        return json.load(fh)


def test_config_path_follows_xdg(tmp_path, monkeypatch):
    monkeypatch.setenv("XDG_CONFIG_HOME", str(tmp_path))
    assert provider_setup.config_path() == str(tmp_path / "udmi" / "workbench.json")


@pytest.mark.parametrize("spec,project,region", [
    ("my-project", "my-project", "global"),
    ("my-project/us-central1", "my-project", "us-central1"),
])
def test_parse_vertex_spec(spec, project, region):
    assert provider_setup.parse_vertex_spec(spec) == {
        "provider": "vertex_ai", "project": project, "region": region}


@pytest.mark.parametrize("spec", ["", "/us-central1", "p/r/x", "p/"])
def test_parse_vertex_spec_rejects_malformed(spec):
    with pytest.raises(provider_setup.ProviderSetupError, match="Expected --vertex"):
        provider_setup.parse_vertex_spec(spec)


def test_setup_saves_and_preserves_other_keys(adc_ok, capsys):
    path = provider_setup.config_path()
    os.makedirs(os.path.dirname(path))
    with open(path, "w") as fh:
        json.dump({"site_roots": ["/labs"]}, fh)
    assert cli.main(["setup", "--vertex=my-project/europe-west1"]) == 0
    assert "Saved: Mantis uses Vertex AI project 'my-project', region 'europe-west1'" in capsys.readouterr().out
    assert _document() == {
        "site_roots": ["/labs"],
        "mantis": {"provider": "vertex_ai", "project": "my-project", "region": "europe-west1"},
    }


def test_setup_refuses_without_adc(monkeypatch, capsys):
    monkeypatch.setattr(preflight, "adc_problem", lambda require_project=False: "no application-default credentials: x")
    assert cli.main(["setup", "--vertex=my-project"]) == 1
    assert "gcloud auth application-default login" in capsys.readouterr().err
    assert not os.path.exists(provider_setup.config_path())


def test_setup_warns_when_the_environment_already_conflicts(adc_ok, monkeypatch, capsys):
    monkeypatch.setenv("GEMINI_API_KEY", "k")
    assert cli.main(["setup", "--vertex=my-project"]) == 0
    assert "will refuse to start" in capsys.readouterr().err


def test_setup_show_and_clear(adc_ok, capsys):
    assert cli.main(["setup"]) == 0
    assert "No saved Mantis provider" in capsys.readouterr().out
    cli.main(["setup", "--vertex=my-project"])
    capsys.readouterr()
    assert cli.main(["setup"]) == 0
    assert "Vertex AI project 'my-project', region 'global'" in capsys.readouterr().out
    assert cli.main(["setup", "--clear"]) == 0
    assert "Removed the saved Mantis provider" in capsys.readouterr().out
    assert "mantis" not in _document()
    assert cli.main(["setup", "--clear"]) == 0
    assert "nothing to remove" in capsys.readouterr().out


def test_setup_rejects_unknown_arguments(capsys):
    assert cli.main(["setup", "local", "stack"]) == 2
    assert "unrecognised setup arguments: local stack" in capsys.readouterr().err


def test_apply_exports_the_saved_setup():
    provider_setup.save({"provider": "vertex_ai", "project": "my-project", "region": "us-central1"})
    assert provider_setup.apply_to_environment() is None
    assert os.environ["GOOGLE_CLOUD_PROJECT"] == "my-project"
    assert os.environ["GOOGLE_CLOUD_REGION"] == "us-central1"


def test_apply_without_a_saved_setup_changes_nothing():
    assert provider_setup.apply_to_environment() is None
    assert "GOOGLE_CLOUD_PROJECT" not in os.environ


def test_apply_accepts_an_environment_that_agrees(monkeypatch):
    provider_setup.save({"provider": "vertex_ai", "project": "my-project", "region": "global"})
    monkeypatch.setenv("GOOGLE_CLOUD_PROJECT", "my-project")
    assert provider_setup.apply_to_environment() is None


@pytest.mark.parametrize("name,value,expected", [
    ("GEMINI_API_KEY", "k", "GEMINI_API_KEY is set"),
    ("GOOGLE_API_KEY", "k", "GOOGLE_API_KEY is set"),
    ("MANTIS_OFFLINE", "true", "MANTIS_OFFLINE is set"),
    ("GOOGLE_CLOUD_PROJECT", "other", "GOOGLE_CLOUD_PROJECT is set to 'other'"),
    ("GCP_PROJECT", "other", "GCP_PROJECT is set to 'other'"),
    ("GOOGLE_CLOUD_REGION", "asia-east1", "GOOGLE_CLOUD_REGION is set to 'asia-east1'"),
])
def test_apply_refuses_a_conflicting_environment(monkeypatch, name, value, expected):
    provider_setup.save({"provider": "vertex_ai", "project": "my-project", "region": "global"})
    monkeypatch.setenv(name, value)
    problem = provider_setup.apply_to_environment()
    assert expected in problem
    assert "Vertex AI project 'my-project'" in problem
    assert "bin/mantis setup --clear" in problem


def test_an_invalid_saved_entry_is_reported():
    path = provider_setup.config_path()
    os.makedirs(os.path.dirname(path))
    with open(path, "w") as fh:
        json.dump({"mantis": {"provider": "vertex_ai"}}, fh)
    assert "is invalid" in provider_setup.apply_to_environment()


def test_cli_refuses_to_start_when_the_saved_setup_conflicts(monkeypatch, capsys):
    provider_setup.save({"provider": "vertex_ai", "project": "my-project", "region": "global"})
    assert cli.main(["--offline", "What is pointset?"]) == 1
    assert "MANTIS_OFFLINE is set" in capsys.readouterr().err


def test_cli_vertex_flag_for_a_different_project_conflicts(monkeypatch, capsys):
    provider_setup.save({"provider": "vertex_ai", "project": "my-project", "region": "global"})
    assert cli.main(["--vertex=other/global", "What is pointset?"]) == 1
    assert "GOOGLE_CLOUD_PROJECT is set to 'other'" in capsys.readouterr().err


def test_saved_setup_satisfies_the_provider_check(monkeypatch):
    monkeypatch.setattr(preflight, "adc_problem", lambda require_project=False: "no application-default credentials")
    provider_setup.save({"provider": "vertex_ai", "project": "my-project", "region": "global"})
    checks = preflight.mantis_checks("cli")
    problems = preflight.run(checks[-2:])  # apply_to_environment, check_provider
    assert problems == []
