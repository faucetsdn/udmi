"""Read-only spec endpoint (/api/repo-doc) and the Mantis doc-link renderer.

Mantis answers cite `docs/...md` and `schema/*.json`. The panel turns those
citations into links to /api/repo-doc, which serves exactly those two trees from
the checkout under test as non-sniffable plain text.
"""

import json
import os
import shutil
import subprocess
import threading
import urllib.error
import urllib.parse
import urllib.request

import pytest

from workbench.server import repo_docs
from workbench.server.gateway import create_gateway
from workbench.server.repo_docs import RepoDocError
from workbench.server.routes import classify_exception

UDMI_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
MARKDOWN_MODULE = os.path.join(
    UDMI_ROOT, "workbench", "static", "js", "components", "mantis-markdown.js"
)


# ------------------------------------------------------------- resolution ---
@pytest.fixture
def fake_root(tmp_path):
    (tmp_path / "docs" / "specs").mkdir(parents=True)
    (tmp_path / "docs" / "specs" / "a.md").write_text("# A\n", encoding="utf-8")
    (tmp_path / "schema").mkdir()
    (tmp_path / "schema" / "state.json").write_text("{}", encoding="utf-8")
    (tmp_path / "secret.md").write_text("secret", encoding="utf-8")
    (tmp_path / "docs" / "leak.md").symlink_to(tmp_path / "secret.md")
    return str(tmp_path)


def test_allowed_docs_and_schemas_are_served(fake_root):
    assert repo_docs.read_doc(fake_root, "docs/specs/a.md") == b"# A\n"
    assert repo_docs.read_doc(fake_root, "schema/state.json") == b"{}"


@pytest.mark.parametrize(
    "path",
    [
        "docs/../secret.md",
        "docs/specs/../../secret.md",
        "/etc/passwd",
        "secret.md",
        "docs",
        "docs/specs/a.json",
        "schema/state.md",
        "validator/x.md",
        "docs\\specs\\a.md",
        "docs//specs/a.md",
        "docs/./specs/a.md",
        "docs/leak.md",
        "",
    ],
)
def test_refused_paths_are_explicit_403s(fake_root, path):
    with pytest.raises(RepoDocError) as caught:
        repo_docs.read_doc(fake_root, path)
    assert "Refusing" in str(caught.value)
    assert classify_exception(caught.value) == 403


def test_missing_doc_is_a_404(fake_root):
    with pytest.raises(RepoDocError) as caught:
        repo_docs.read_doc(fake_root, "docs/specs/missing.md")
    assert str(caught.value) == "Doc not found: docs/specs/missing.md"
    assert classify_exception(caught.value) == 404


# ------------------------------------------------------------- over HTTP ---
@pytest.fixture(scope="module")
def server_url(tmp_path_factory):
    config_path = str(tmp_path_factory.mktemp("repo-doc-config") / "workbench.json")
    server = create_gateway(host="127.0.0.1", port=0, config_path=config_path)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    yield f"http://127.0.0.1:{server.server_address[1]}"
    server.shutdown()
    server.server_close()


def _get(url):
    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return response.status, dict(response.headers), response.read()
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read()


def test_endpoint_serves_a_real_spec_as_inline_plain_text(server_url):
    status, headers, body = _get(f"{server_url}/api/repo-doc?path=docs/readme.md")
    assert status == 200
    assert headers["Content-Type"] == "text/plain; charset=utf-8"
    assert headers["X-Content-Type-Options"] == "nosniff"
    assert headers["Content-Disposition"] == "inline"
    with open(os.path.join(UDMI_ROOT, "docs", "readme.md"), "rb") as handle:
        assert body == handle.read()


def test_endpoint_serves_a_real_schema(server_url):
    status, _, body = _get(f"{server_url}/api/repo-doc?path=schema/state.json")
    assert status == 200
    assert json.loads(body)


def test_endpoint_refuses_traversal_and_reports_missing(server_url):
    traversal = urllib.parse.quote("docs/../etc/validator.out", safe="")
    status, _, body = _get(f"{server_url}/api/repo-doc?path={traversal}")
    assert status == 403
    assert "Refusing" in json.loads(body)["error"]

    status, _, body = _get(f"{server_url}/api/repo-doc?path=docs/nope/missing.md")
    assert status == 404
    assert json.loads(body)["error"].endswith("Doc not found: docs/nope/missing.md")

    status, _, body = _get(f"{server_url}/api/repo-doc")
    assert status == 400
    assert "path" in json.loads(body)["error"]


# ------------------------------------------------------ link rendering (JS) ---
def _render(cases):
    """Runs mantis-markdown.js under Node and returns formatInlineMarkdown output."""
    node = shutil.which("node")
    if node is None:
        pytest.fail("node is required to test workbench/static/js modules and is not on PATH.")
    script = (
        f"const m = await import({json.dumps('file://' + MARKDOWN_MODULE)});"
        f"const cases = {json.dumps(cases)};"
        "process.stdout.write(JSON.stringify(cases.map((c) => m.formatInlineMarkdown(c))));"
    )
    done = subprocess.run(
        [node, "--input-type=module", "-e", script],
        capture_output=True, text=True, timeout=60, check=False,
    )
    assert done.returncode == 0, done.stderr
    return json.loads(done.stdout)


LINK = (
    '<a class="mantis-doc-link" href="/api/repo-doc?path={q}" target="_blank" '
    'rel="noopener noreferrer">{inner}</a>'
)


def _link(path, inner):
    return LINK.format(q=urllib.parse.quote(path, safe=""), inner=inner)


def test_doc_paths_in_code_text_and_markdown_links_become_links():
    code, bare, md, schema, fragment = _render([
        "See `docs/specs/sequences/config.md` for details.",
        "Governed by docs/messages/state.md.",
        "Read [the state spec](docs/messages/state.md).",
        "Schema: `schema/state.json`",
        "See docs/specs/a.md#section-2 now",
    ])
    assert code == (
        "See " + _link("docs/specs/sequences/config.md",
                       "<code>docs/specs/sequences/config.md</code>") + " for details."
    )
    assert bare == "Governed by " + _link("docs/messages/state.md", "docs/messages/state.md") + "."
    assert md == "Read " + _link("docs/messages/state.md", "the state spec") + "."
    assert schema == "Schema: " + _link("schema/state.json", "<code>schema/state.json</code>")
    assert fragment == "See " + _link("docs/specs/a.md", "docs/specs/a.md#section-2") + " now"


def test_non_doc_targets_stay_escaped_text():
    outputs = _render([
        "[x](javascript:alert(1))",
        "[x](docs/../etc/passwd.md)",
        "/usr/src/udmi/docs/specs/a.md",
        "`validator/src/Foo.java`",
        "<img src=x onerror=alert(1)> docs/a.md",
        '[<b>"q"</b>](docs/a.md)',
        "\u0000" + "0" + "\u0000",
    ])
    assert outputs[0] == "[x](javascript:alert(1))"
    assert outputs[1] == "[x](docs/../etc/passwd.md)"
    assert "<a" not in outputs[2]
    assert outputs[3] == "<code>validator/src/Foo.java</code>"
    assert outputs[4].startswith("&lt;img src=x onerror=alert(1)&gt; <a ")
    assert outputs[5] == _link("docs/a.md", "&lt;b&gt;&quot;q&quot;&lt;/b&gt;")
    assert outputs[6] == "0"
