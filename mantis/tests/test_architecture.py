"""Architectural boundary enforcement tests.

Parses the AST of all modules to ensure strict adherence to Clean Architecture
and unidirectional dependency flow.
"""

import ast
import os
import pytest


def get_imports(filepath: str) -> list[str]:
    """Extract all top-level and inner imported module names from a python file."""
    with open(filepath, "r", encoding="utf-8") as f:
        tree = ast.parse(f.read(), filename=filepath)

    imports = []
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                imports.append(alias.name)
        elif isinstance(node, ast.ImportFrom):
            if node.module:
                imports.append(node.module)
    return imports


def test_mcp_never_imports_mantis_agent(pytestconfig):
    """mcp/ is Layer 1 and must NEVER import mantis.agent, mantis.chat, mantis.cli, or mantis.context."""
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    mcp_dir = os.path.join(root_dir, "mcp")

    forbidden = ["mantis.agent", "mantis.chat", "mantis.cli", "mantis.context"]
    for entry in os.listdir(mcp_dir):
        if entry.endswith(".py"):
            fpath = os.path.join(mcp_dir, entry)
            imports = get_imports(fpath)
            for imp in imports:
                for fb in forbidden:
                    assert not imp.startswith(fb), (
                        f"Architectural Boundary Violation: '{entry}' in mcp/ imports '{imp}', "
                        f"violating unidirectional dependency rules."
                    )


def test_tools_never_import_agent():
    """mantis/tools/ is Layer 1 and must NEVER import mantis.agent, mantis.chat, mantis.cli, or mantis.context."""
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    tools_dir = os.path.join(root_dir, "mantis", "tools")

    forbidden = ["mantis.agent", "mantis.chat", "mantis.cli", "mantis.context"]
    for entry in os.listdir(tools_dir):
        if entry.endswith(".py"):
            fpath = os.path.join(tools_dir, entry)
            imports = get_imports(fpath)
            for imp in imports:
                for fb in forbidden:
                    assert not imp.startswith(fb), (
                        f"Architectural Boundary Violation: '{entry}' in mantis/tools/ imports '{imp}'."
                    )


def test_models_have_zero_internal_dependencies():
    """mantis/models.py is Layer 0 and must not import any mantis or mcp modules."""
    root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    models_file = os.path.join(root_dir, "mantis", "models.py")

    imports = get_imports(models_file)
    for imp in imports:
        assert not imp.startswith("mantis.") and not imp.startswith("mcp."), (
            f"Layer 0 Violation: mantis/models.py must be purely standalone but imports '{imp}'."
        )
