"""Unit tests for mantis.chat including multi-turn conversational interaction."""

import os
import pytest
from mantis.agent import MantisAgent
from mantis.chat import ChatConsole
from mantis.models import SessionContext


def test_chat_canonical_commands(tmp_path):
    agent = MantisAgent()
    console = ChatConsole(agent=agent, context=SessionContext())

    # Test /help
    assert console.handle_command("/help") is None

    # Test /status
    assert console.handle_command("/status") is None

    # Test /clear
    console.context_mgr.add_user_message("hi")
    assert len(console.history) == 1
    console.handle_command("/clear")
    assert len(console.history) == 0

    # Test /export
    export_file = tmp_path / "export.md"
    console.context_mgr.add_user_message("test question")
    console.handle_command(f"/export {export_file}")
    assert os.path.isfile(export_file)
    assert "test question" in export_file.read_text()

    # Test /exit
    assert console.handle_command("/exit") == "EXIT"


def test_chat_multi_turn_antecedent_resolution():
    from mantis.config import ProviderType
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    console = ChatConsole(agent=agent, context=SessionContext())

    # Turn 1: Run test for specific device AHU-99
    agent.run("Run pointset_publish for device AHU-99 on sites/udmi_site_model", context=console.context_mgr.context)
    assert console.context_mgr.context.active_device_id == "AHU-99"
    assert console.context_mgr.context.active_test_id == "pointset_publish"

    # Turn 2: Follow-up question without explicit device name
    # Verify the diagnosis was run for AHU-99 instead of default AHU-1. No run is
    # recorded for AHU-99, so the timeline lookup must fail naming AHU-99 rather
    # than silently harvesting the repository's out/ directory.
    import pytest
    with pytest.raises(FileNotFoundError, match="device_id='AHU-99'"):
        agent.run("Why did it fail?", context=console.context_mgr.context)
