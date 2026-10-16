"""Tests for the Workbench launch prerequisites: preflight, bin/ensure_venv, bin/workbench."""

import os
import shutil
import subprocess
import sys
import textwrap

import pytest

from mantis import preflight as mantis_preflight
from workbench.server import preflight

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def test_workbench_checks_include_every_mantis_cli_check():
    names = [getattr(c, "__name__", "") for c in preflight.workbench_checks()]
    assert "check_provider" in names
    assert "check_udmi_common" in names
    assert "check_chromium" in names


def test_main_lists_every_problem_and_fails(monkeypatch, capsys):
    monkeypatch.setattr(preflight, "workbench_checks", lambda: [lambda: "first", lambda: None, lambda: "second"])
    assert preflight.main() == 1
    err = capsys.readouterr().err
    assert "The UDMI Workbench cannot start" in err
    assert "- first" in err and "- second" in err


def test_main_passes_when_every_check_passes(monkeypatch, capsys):
    monkeypatch.setattr(preflight, "workbench_checks", lambda: [lambda: None])
    assert preflight.main() == 0
    assert capsys.readouterr().err == ""


def test_java_is_required(monkeypatch):
    monkeypatch.setattr(mantis_preflight.shutil, "which", lambda command: None if command == "java" else "/x")
    problems = [c() for c in preflight.workbench_checks()[-1:]]
    assert "'java' is not on PATH" in problems[0]


# ------------------------------------------------------------ bin/ensure_venv ---
def _fake_repo(tmp_path, setup_writes_stamp=True):
    """A minimal checkout: bin/ensure_venv, a recording bin/setup_base, requirements."""
    (tmp_path / "bin").mkdir()
    (tmp_path / "etc").mkdir()
    shutil.copy(os.path.join(ROOT, "bin", "ensure_venv"), tmp_path / "bin" / "ensure_venv")
    (tmp_path / "etc" / "requirements.txt").write_text("pydantic==2\n")
    stamp_line = (
        "sha256sum etc/requirements.txt | cut -d' ' -f1 > venv/.requirements.sha256"
        if setup_writes_stamp else "true"
    )
    setup = tmp_path / "bin" / "setup_base"
    setup.write_text(textwrap.dedent(f"""\
        #!/bin/bash -e
        echo ran >> setup_base.calls
        mkdir -p venv/bin
        printf '#!/bin/sh\\n' > venv/bin/python3
        chmod +x venv/bin/python3
        {stamp_line}
        """))
    setup.chmod(0o755)
    return tmp_path


def _ensure(repo):
    return subprocess.run(["bin/ensure_venv"], cwd=repo, capture_output=True, text=True)


def _calls(repo):
    path = repo / "setup_base.calls"
    return path.read_text().count("ran") if path.exists() else 0


def test_ensure_venv_builds_a_missing_venv_once(tmp_path):
    repo = _fake_repo(tmp_path)
    result = _ensure(repo)
    assert result.returncode == 0, result.stderr
    assert "venv/ is missing" in result.stdout
    assert _calls(repo) == 1
    assert _ensure(repo).returncode == 0
    assert _calls(repo) == 1  # up to date: not rebuilt


def test_ensure_venv_rebuilds_when_requirements_change(tmp_path):
    repo = _fake_repo(tmp_path)
    _ensure(repo)
    (repo / "etc" / "requirements.txt").write_text("pydantic==2\nmarkdown2==2\n")
    result = _ensure(repo)
    assert result.returncode == 0, result.stderr
    assert "etc/requirements.txt changed" in result.stdout
    assert _calls(repo) == 2


def test_ensure_venv_rebuilds_a_venv_without_a_stamp(tmp_path):
    repo = _fake_repo(tmp_path)
    _ensure(repo)
    (repo / "venv" / ".requirements.sha256").unlink()
    result = _ensure(repo)
    assert "has no record of the requirements" in result.stdout
    assert _calls(repo) == 2


def test_ensure_venv_fails_when_setup_base_leaves_no_stamp(tmp_path):
    repo = _fake_repo(tmp_path, setup_writes_stamp=False)
    result = _ensure(repo)
    assert result.returncode == 1
    assert "does not match etc/requirements.txt" in result.stderr


def test_setup_base_writes_the_stamp_ensure_venv_reads():
    with open(os.path.join(ROOT, "bin", "setup_base")) as f:
        setup = f.read()
    assert "sha256sum etc/requirements.txt | cut -d' ' -f1 > venv/.requirements.sha256" in setup
    assert " lsof" in setup  # bin/workbench needs it; installed with the system packages


# ------------------------------------------------------------- bin/workbench ---
def _path_without(tmp_path, excluded):
    """A PATH directory holding the tools bin/workbench needs, minus `excluded`."""
    tools = tmp_path / "tools"
    tools.mkdir()
    for name in ("bash", "dirname", "tr", "head", "sleep", "mkdir", "tail", "lsof", "setsid"):
        if name in excluded:
            continue
        found = shutil.which(name)
        if found:
            os.symlink(found, tools / name)
    return str(tools)


def test_workbench_refuses_to_start_without_lsof(tmp_path):
    env = {"PATH": _path_without(tmp_path, {"lsof"}), "HOME": str(tmp_path)}
    result = subprocess.run(
        ["bash", os.path.join(ROOT, "bin", "workbench"), "--port=18999"],
        env=env, capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 1
    assert "lsof is not on PATH" in result.stdout
    assert "Starting the UDMI Workbench gateway" not in result.stdout


def test_chromium_check_names_the_missing_browser_directories(tmp_path, monkeypatch):
    monkeypatch.setenv("PLAYWRIGHT_BROWSERS_PATH", str(tmp_path / "browsers"))
    problem = preflight.check_chromium()
    assert "Playwright's Chromium is not installed" in problem
    assert str(tmp_path / "browsers") in problem
    assert "venv/bin/playwright install chromium" in problem


# ------------------------------------------------ saved provider (bin/mantis setup) ---
def _gateway_env(tmp_path, **extra):
    env = {k: v for k, v in os.environ.items() if k not in (
        "GEMINI_API_KEY", "GOOGLE_API_KEY", "GOOGLE_CLOUD_PROJECT", "GCP_PROJECT",
        "GOOGLE_CLOUD_REGION", "GCP_REGION", "MANTIS_OFFLINE")}
    env["XDG_CONFIG_HOME"] = str(tmp_path / "xdg")
    env["PYTHONPATH"] = os.pathsep.join([os.path.join(ROOT, "common", "src", "main", "python"), ROOT])
    env.update(extra)
    return env


def test_gateway_refuses_to_start_when_the_saved_setup_conflicts(tmp_path):
    from mantis import provider_setup

    env = _gateway_env(tmp_path, GEMINI_API_KEY="k")
    config = tmp_path / "xdg" / "udmi" / "workbench.json"
    config.parent.mkdir(parents=True)
    config.write_text('{"mantis": {"provider": "vertex_ai", "project": "my-project", "region": "global"}}')
    result = subprocess.run(
        [sys.executable, "-m", "workbench.server.gateway", "--port=0"],
        cwd=ROOT, env=env, capture_output=True, text=True, timeout=60,
    )
    assert result.returncode == 1
    assert "GEMINI_API_KEY is set" in result.stderr
    assert "UDMI Workbench serving" not in result.stdout
    assert provider_setup.CONFIG_KEY == "mantis"


def test_workbench_no_longer_accepts_internal(tmp_path):
    result = subprocess.run(
        ["bash", os.path.join(ROOT, "bin", "workbench"), "--internal"],
        capture_output=True, text=True, timeout=30,
    )
    assert result.returncode == 1
    assert "Unknown argument '--internal'" in result.stdout
