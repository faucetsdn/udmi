"""Unit tests for mantis.context ContextManager."""

import pytest
from mantis.context import ContextManager
from mantis.models import MessageRole, SessionContext


def test_context_manager_entity_extraction():
    ctx_mgr = ContextManager()

    # Turn 1: User mentions device and test sequence
    ctx_mgr.add_user_message("Run pointset_publish for device AHU-2 on sites/udmi_site_model")

    assert ctx_mgr.context.active_device_id == "AHU-2"
    assert ctx_mgr.context.active_test_id == "pointset_publish"
    assert ctx_mgr.context.active_site_model == "sites/udmi_site_model"

    # Turn 2: Follow-up pronoun/elliptical question
    ctx_mgr.add_user_message("Why did it fail?")
    # Pointers remain preserved
    assert ctx_mgr.context.active_device_id == "AHU-2"
    assert ctx_mgr.context.active_test_id == "pointset_publish"


def test_context_manager_session_extraction():
    ctx_mgr = ContextManager()
    ctx_mgr.add_user_message("Start an isolated environment dev_run_42 with DUT AHU-1")

    assert ctx_mgr.context.active_session_id == "dev_run_42"
    assert ctx_mgr.context.active_device_id == "AHU-1"


def test_context_manager_compaction():
    ctx_mgr = ContextManager(max_history_turns=2, max_token_chars_per_turn=100)

    # Add large output
    large_payload = "A" * 500
    ctx_mgr.add_assistant_message(large_payload)

    # Check compaction
    assert len(ctx_mgr.context.history[0].content) <= 180
    assert "...[Log output truncated for context compaction]..." in ctx_mgr.context.history[0].content


def test_context_manager_llm_contents():
    ctx_mgr = ContextManager()
    ctx_mgr.add_user_message("Hello Mantis")
    ctx_mgr.add_assistant_message("Hello! How can I help you?")

    contents = ctx_mgr.get_llm_contents("Why did pointset_publish fail?")
    assert len(contents) == 3
    assert contents[0].role == "user"
    assert contents[1].role == "model"
    assert contents[2].role == "user"


def test_context_clear_preserves_pointers():
    ctx = SessionContext(
        active_site_model="sites/custom_model",
        active_session_id="session_99",
        active_device_id="AHU-3",
        active_test_id="system_last_update",
    )
    ctx_mgr = ContextManager(context=ctx)
    ctx_mgr.add_user_message("Test message")

    assert len(ctx_mgr.context.history) == 1

    ctx_mgr.clear_history()

    assert len(ctx_mgr.context.history) == 0
    assert ctx_mgr.context.active_device_id == "AHU-3"
    assert ctx_mgr.context.active_test_id == "system_last_update"
    assert ctx_mgr.context.active_session_id == "session_99"
    assert ctx_mgr.context.active_site_model == "sites/custom_model"


def test_context_disk_persistence(tmp_path):
    cache_file = tmp_path / "active_context.json"
    
    # Instance 1: write context
    mgr1 = ContextManager(cache_file=str(cache_file))
    mgr1.add_user_message("Run pointset_publish on device AHU-5 in sites/custom_site")
    mgr1.add_assistant_message("Executing test...")

    assert cache_file.is_file()
    assert mgr1.context.active_device_id == "AHU-5"
    assert mgr1.context.active_test_id == "pointset_publish"

    # Instance 2: reload from disk
    mgr2 = ContextManager(cache_file=str(cache_file))
    assert mgr2.context.active_device_id == "AHU-5"
    assert mgr2.context.active_test_id == "pointset_publish"
    assert mgr2.context.active_site_model == "sites/custom_site"
    assert len(mgr2.context.history) == 2


def test_context_reset(tmp_path):
    cache_file = tmp_path / "active_context.json"
    mgr = ContextManager(cache_file=str(cache_file))
    mgr.add_user_message("Run test on AHU-1")
    assert cache_file.is_file()

    mgr.reset()
    assert mgr.context.active_device_id is None
    assert len(mgr.context.history) == 0


def test_context_entity_extraction_rejects_common_words_and_days():
    ctx_mgr = ContextManager()

    # Queries with temporal or colloquial phrases should not extract device
    ctx_mgr.add_user_message("Why did the test fail on Tuesday?")
    assert ctx_mgr.context.active_device_id is None

    ctx_mgr.add_user_message("Can you debug this for me?")
    assert ctx_mgr.context.active_device_id is None

    ctx_mgr.add_user_message("Why did it fail on localhost?")
    assert ctx_mgr.context.active_device_id is None

    # Valid device name with hyphen should be extracted
    ctx_mgr.add_user_message("Why did it fail on AHU-1?")
    assert ctx_mgr.context.active_device_id == "AHU-1"

