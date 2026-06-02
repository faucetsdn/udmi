# UDMI-specific tool belt, delegating to the generic triage_harness
import os

from ..harness.tools import ToolBelt

MANTIS_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UDMI_ROOT = os.path.dirname(MANTIS_DIR)

# Initialize the generic ToolBelt with UDMI-specific parameters
_udmi_tool_belt = ToolBelt(
    workspace_root=UDMI_ROOT,
    search_dirs=["validator/src", "udmis/src", "pubber/src", "common/src",
                 "schema"],
    exclude_dirs=["bridgehead"],
    include_files=["*.java", "*.py", "*.yaml"]
)

# Export bound functions directly for backward compatibility with legacy code / model tool schemas
list_directory = _udmi_tool_belt.list_directory
grep_codebase = _udmi_tool_belt.grep_codebase
read_file_lines = _udmi_tool_belt.read_file_lines
git_read_operations = _udmi_tool_belt.git_read_operations
grep_file = _udmi_tool_belt.grep_file
expand_log_window = _udmi_tool_belt.expand_log_window
read_method_definition = _udmi_tool_belt.read_method_definition
lookup_symbol = _udmi_tool_belt.lookup_symbol

