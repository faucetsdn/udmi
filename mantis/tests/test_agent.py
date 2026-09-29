"""Unit tests for mantis.agent including ReAct multi-step tool-calling loop."""

import os
from unittest.mock import MagicMock
import pytest
from mantis.agent import MantisAgent, _neutralize_schema_refs
from mantis.config import ModelTier, ProviderType


def test_agent_diagnose_stale_cutoff(tmp_path):
    run_dir = tmp_path / "run_stale"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:02Z Dispatched config RC:9a6ddf.00000134
2026-08-26T12:45:08Z Cutoff set: 2026-08-26T12:45:08Z
2026-08-26T12:45:09Z ignoring stale state update 2026-08-26T12:45:06Z
2026-08-26T12:47:08Z Stage timeout after 120s
2026-08-26T12:47:09Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    agent = MantisAgent()
    res = agent.diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        site_model="sites/udmi_site_model",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert "Sequencer timed out" in res["root_cause"]
    assert res["competing_hypotheses"]["Stale State Cutoff Rejection"]["status"] == "CONFIRMED"
    assert res["competing_hypotheses"]["Jackson Deserialization Failure"]["status"] == "REFUTED"
    assert "sequenceDiagram" in res["report"]


def test_agent_diagnose_jackson_error(tmp_path):
    run_dir = tmp_path / "run_jackson"
    run_dir.mkdir()
    seq_log = run_dir / "sequence.log"
    seq_log.write_text("""
2026-08-26T12:45:00Z Starting test pointset_publish for AHU-1
2026-08-26T12:45:02Z UnrecognizedPropertyException: Unrecognized field "invalid_key" (class udmi.schema.Metadata)
2026-08-26T12:45:03Z RESULT fail pointset pointset_publish STABLE 0/8 Sequence failed
""")

    agent = MantisAgent()
    res = agent.diagnose_test_failure(
        test_id="pointset_publish",
        device_id="AHU-1",
        site_model="sites/udmi_site_model",
        run_dir=str(run_dir),
    )

    assert res["status"] == "SUCCESS"
    assert res["competing_hypotheses"]["Jackson Deserialization Failure"]["status"] == "CONFIRMED"
    # Nothing was received from the device in this run, so no state update could
    # have been rejected as stale -- and nothing observed rules it out either.
    assert res["competing_hypotheses"]["Stale State Cutoff Rejection"]["status"] == "NOT ASSESSED"


def test_agent_run_deterministic_schema_query():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    out = agent.run("What are the required fields in pointset schema?")
    assert "Schema: `pointset`" in out or "pointset" in out


def test_agent_run_deterministic_site_model_query():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    out = agent.run("Validate site model sites/udmi_site_model")
    assert "Site Model: `sites/udmi_site_model`" in out
    assert "AHU-1" in out


def test_agent_run_deterministic_test_execution_no_session():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    agent.session_mgr.list_test_setups = MagicMock(return_value=[])
    agent.session_mgr.is_session_active = MagicMock(return_value=False)
    out = agent.run("Run pointset_publish for device AHU-1")
    assert "not running" in out or "Start an isolated environment" in out


def test_agent_run_deterministic_test_execution_with_active_session():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    agent.session_mgr.is_session_active = MagicMock(return_value=True)
    agent.session_mgr.get_session_info = MagicMock(return_value={
        "session_name": "udmi_dev_1",
        "ports": {"mqtt": 28430},
        "project_spec": "//mqtt/localhost:28430",
    })
    agent.session_mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    out = agent.run("Run pointset_publish for device AHU-1 in session dev_1")
    assert "Launched sequencer test" in out
    assert "AHU-1" in out
    assert "pointset_publish" in out
    agent.session_mgr.start_session_process.assert_called_once()


def test_agent_run_deterministic_test_execution_cloud_endpoint():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    agent.session_mgr.is_session_active = MagicMock(return_value=True)
    agent.session_mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    out = agent.run("Run test pointset_publish for AHU-1 against //gbos/bos-platform-dev/faucetsdn")
    assert "Launched sequencer test" in out
    assert "AHU-1" in out
    assert "pointset_publish" in out
    assert "//gbos/bos-platform-dev/faucetsdn" in out
    agent.session_mgr.start_session_process.assert_called_once()


def test_agent_run_deterministic_test_execution_gref_with_plus_suffix():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    agent.session_mgr.is_session_active = MagicMock(return_value=True)
    agent.session_mgr.start_session_process = MagicMock(return_value={"status": "STARTED"})

    out = agent.run("Run test pointset_publish for AHU-1 against //gref/bos-platform-staging+dev_user")
    assert "Launched sequencer test" in out
    assert "AHU-1" in out
    assert "pointset_publish" in out
    assert "//gref/bos-platform-staging+dev_user" in out
    agent.session_mgr.start_session_process.assert_called_once()


# ------------------------------------------------------------------------------
# Mock ReAct Cognitive Loop Tests
# ------------------------------------------------------------------------------

class MockFunctionCall:
    def __init__(self, name, args):
        self.name = name
        self.args = args


class MockCandidate:
    def __init__(self, content):
        self.content = content


class MockContent:
    def __init__(self, role, parts):
        self.role = role
        self.parts = parts


class MockResponse:
    def __init__(self, text=None, function_calls=None):
        self.text = text
        self.function_calls = function_calls or []
        self.candidates = [MockCandidate(MockContent("model", []))]


class MockModels:
    def __init__(self, responses):
        self.responses = list(responses)
        self.call_count = 0
        self.calls = []

    def generate_content(self, model, contents, config):
        self.call_count += 1
        self.calls.append((model, contents, config))
        if self.responses:
            return self.responses.pop(0)
        return MockResponse(text="Default fallback")


class MockGenAIClient:
    def __init__(self, responses):
        self.models = MockModels(responses)


def test_agent_react_tool_calling_loop():
    # Step 1: Model calls inspect_udmi_schema
    resp_step1 = MockResponse(
        text=None,
        function_calls=[MockFunctionCall(name="inspect_udmi_schema", args={"schema_name": "pointset"})],
    )
    # Step 2: Model receives tool output and emits final answer
    resp_step2 = MockResponse(
        text="The pointset schema defines point dictionaries and sample telemetry structures.",
        function_calls=[],
    )

    mock_client = MockGenAIClient([resp_step1, resp_step2])
    agent = MantisAgent(client=mock_client)

    chunks = []
    output = agent._run_llm(
        prompt="Tell me about the pointset schema",
        stream_callback=chunks.append,
    )

    assert "The pointset schema defines point dictionaries" in output
    assert mock_client.models.call_count == 2
    # Verify tools were provided in config
    _, _, config = mock_client.models.calls[0]
    assert config.tools is not None


def test_agent_react_tool_error_resilience():
    # Step 1: Model calls a tool with invalid name
    resp_step1 = MockResponse(
        text=None,
        function_calls=[MockFunctionCall(name="non_existent_tool", args={})],
    )
    # Step 2: Model handles error and synthesizes
    resp_step2 = MockResponse(
        text="Handled tool error gracefully.",
        function_calls=[],
    )

    mock_client = MockGenAIClient([resp_step1, resp_step2])
    agent = MantisAgent(client=mock_client)

    output = agent._run_llm(
        prompt="Execute invalid action",
        enable_scoping=False,
    )

    assert output == "Handled tool error gracefully."
    assert mock_client.models.call_count == 2


class MockStreamChunk:
    def __init__(self, text="", function_calls=None, candidates=None):
        self.text = text
        self.function_calls = function_calls or []
        self.candidates = candidates or []


class MockStreamingModels:
    def __init__(self, stream_chunks):
        self.stream_chunks = stream_chunks
        self.call_count = 0

    def generate_content_stream(self, model, contents, config):
        self.call_count += 1
        for chunk in self.stream_chunks:
            yield chunk


def test_agent_react_streaming_token_chunks():
    chunks_data = [
        MockStreamChunk(text="The "),
        MockStreamChunk(text="pointset "),
        MockStreamChunk(text="schema "),
        MockStreamChunk(text="is "),
        MockStreamChunk(text="valid."),
    ]
    client = MagicMock()
    client.models = MockStreamingModels(chunks_data)
    agent = MantisAgent(client=client)

    received_tokens = []
    output = agent._run_llm(
        prompt="Describe pointset",
        stream_callback=received_tokens.append,
        enable_scoping=False,
    )

    assert output == "The pointset schema is valid."
    assert received_tokens == ["The ", "pointset ", "schema ", "is ", "valid."]
    assert client.models.call_count == 1


@pytest.mark.anyio
async def test_agent_run_async(monkeypatch):
    monkeypatch.setenv("MANTIS_OFFLINE", "true")
    agent = MantisAgent()
    out = await agent.run_async("What are the required fields in pointset schema?")
    assert "Schema: `pointset`" in out or "pointset" in out


def test_agent_metrics_tracking():
    resp = MockResponse(text="Analysis complete", function_calls=[])
    mock_client = MockGenAIClient([resp])
    agent = MantisAgent(client=mock_client)

    from mantis.context import ContextManager
    ctx_mgr = ContextManager()
    out = agent._run_llm("Analyze test run", context_mgr=ctx_mgr, enable_scoping=False)

    assert out == "Analysis complete"
    assert ctx_mgr.context.metrics is not None
    assert ctx_mgr.context.metrics.total_steps == 1
    assert ctx_mgr.context.metrics.api_calls_count == 1
    assert ctx_mgr.context.metrics.total_duration_sec >= 0.0


def test_agent_api_retry_resilience():
    agent = MantisAgent()
    mock_models = MagicMock()
    # Fail once with 429, then succeed
    mock_models.generate_content.side_effect = [
        RuntimeError("429 ResourceExhausted: Quota exceeded"),
        MockResponse(text="Success after retry", function_calls=[]),
    ]

    res = agent._call_api_with_retry(
        client_models=mock_models,
        method_name="generate_content",
        model="gemini-2.0-pro",
        contents=[],
        config={},
        max_retries=2,
        base_delay=0.01,
    )

    assert res.text == "Success after retry"
    assert mock_models.generate_content.call_count == 2


def test_agent_classify_intent_tier():
    agent = MantisAgent()

    # Flash tier queries (schema lookups, log slicing, entity extraction, metadata)
    assert agent.classify_intent_tier("Show the pointset schema") == ModelTier.FLASH
    assert agent.classify_intent_tier("List schemas in UDMI") == ModelTier.FLASH
    assert agent.classify_intent_tier("Slice logs for AHU-1 from sequence.log") == ModelTier.FLASH
    assert agent.classify_intent_tier("Extract entities from support bundle") == ModelTier.FLASH
    assert agent.classify_intent_tier("Inspect metadata for AHU-1") == ModelTier.FLASH
    assert agent.classify_intent_tier("Show devices in sites/udmi_site_model") == ModelTier.FLASH

    # Pro tier queries (failure diagnosis, differential analysis, code/model patching, ReAct planning)
    assert agent.classify_intent_tier("Why did pointset_publish fail for AHU-1?") == ModelTier.PRO
    assert agent.classify_intent_tier("Diagnose root cause of test timeout") == ModelTier.PRO
    assert agent.classify_intent_tier("Compare test runs and show divergence") == ModelTier.PRO
    assert agent.classify_intent_tier("Apply patch to metadata.json to set sample_rate_sec") == ModelTier.PRO
    assert agent.classify_intent_tier("Verify golden baseline validator with anti-cheating") == ModelTier.PRO


def test_agent_classify_intent_tier_ignores_indicators_inside_words():
    """'log' inside 'catalog' or 'technology' is not a log-slicing request. Routing
    such prompts to FLASH skipped scoping, so the informational path never ran."""
    agent = MantisAgent()
    assert agent.classify_intent_tier("Summarise the point catalog technology") == ModelTier.PRO
    assert agent.classify_intent_tier("The catalog of technology options") == ModelTier.PRO
    # Separators inside identifiers still expose the word.
    assert agent.classify_intent_tier("tail sequence.log") == ModelTier.FLASH


def test_agent_classify_intent_tier_routes_explanations_to_pro():
    """Explanation questions must run scoping (PRO) so the plan can declare
    INFORMATIONAL_SCOPE and receive INFORMATIONAL_DIRECTIVE, even when they
    mention a Flash-tier word such as 'schema', 'log' or 'status'."""
    agent = MantisAgent()
    for prompt in (
        "Explain the pointset schema",
        "Describe the pointset schema",
        "How does the sequencer check system status?",
        "How do I slice logs?",
        "What does the metadata version field mean?",
        "What is the status block in state?",
        "Should my device send logs on every config?",
        "Please explain the discovery log",
    ):
        assert agent.classify_intent_tier(prompt) == ModelTier.PRO, prompt
    # Word boundaries apply to explanation phrases too.
    assert agent.classify_intent_tier("List explainers schema") == ModelTier.FLASH


def test_agent_explanation_prompt_receives_informational_directive_without_explicit_tier():
    """End to end: with no tier supplied, an explanation prompt that mentions a
    Flash-tier word ('schema') must be classified PRO, run the scoping turn, and,
    once the plan declares NOT_A_FAILURE, receive INFORMATIONAL_DIRECTIVE."""
    resp_scoping = MockResponse(text=INFORMATIONAL_SCOPING_PLAN, function_calls=[])
    resp_actor = MockResponse(text="Summary: the pointset schema.", function_calls=[])
    mock_client = MockGenAIClient([resp_scoping, resp_actor])
    agent = MantisAgent(client=mock_client)
    pro_model = agent.config.get_model_for_tier(ModelTier.PRO)

    output = agent._run_llm(
        prompt="Describe the pointset schema",
        enable_tripartite=False,
    )

    assert output == "Summary: the pointset schema."
    assert mock_client.models.call_count == 2
    assert mock_client.models.calls[0][0] == pro_model
    _, actor_contents, _ = mock_client.models.calls[1]
    assert "read the ENTIRE test method" in _serialized_text(actor_contents)


def test_agent_two_tier_model_routing():
    agent = MantisAgent()
    flash_model = agent.config.get_model_for_tier(ModelTier.FLASH)
    pro_model = agent.config.get_model_for_tier(ModelTier.PRO)

    # 1. Flash Tier routing
    resp_flash = MockResponse(text="Pointset schema details", function_calls=[])
    client_flash = MockGenAIClient([resp_flash])
    agent.client = client_flash

    out_flash = agent._run_llm("Show pointset schema", enable_scoping=False)
    assert out_flash == "Pointset schema details"
    assert client_flash.models.calls[0][0] == flash_model

    # 2. Pro Tier routing
    resp_pro = MockResponse(text="Root cause diagnosed", function_calls=[])
    client_pro = MockGenAIClient([resp_pro])
    agent.client = client_pro

    out_pro = agent._run_llm("Why did pointset_publish fail for AHU-1?", enable_scoping=False)
    assert out_pro == "Root cause diagnosed"
    assert client_pro.models.calls[0][0] == pro_model

    # 3. Explicit tier override
    resp_override = MockResponse(text="Explicit tier handled", function_calls=[])
    client_override = MockGenAIClient([resp_override])
    agent.client = client_override

    # Query would normally be FLASH, but explicit tier=PRO overrides
    out_override = agent._run_llm("Show pointset schema", tier=ModelTier.PRO, enable_scoping=False)
    assert out_override == "Explicit tier handled"
    assert client_override.models.calls[0][0] == pro_model


def test_agent_run_deterministic_golden_verification(tmp_path):
    etc_dir = tmp_path / "etc"
    out_dir = tmp_path / "out"
    etc_dir.mkdir()
    out_dir.mkdir()

    content = "AHU-1 events_pointset {value: 10}\nAHU-1 events_system {status: ok}\n"
    (etc_dir / "validator.out").write_text(content)
    (out_dir / "validator.out").write_text(content)

    agent = MantisAgent(udmi_root=str(tmp_path))
    res = agent._run_deterministic("Verify golden baseline for validator")

    assert "Golden Baseline Verification" in res
    assert "100.0% parity" in res or "PASSED" in res


def test_agent_parse_patch_data():
    agent = MantisAgent()

    # 1. JSON payload
    data_json = agent._parse_patch_data('patch device AHU-1 {"system": {"min_loglevel": 200}}')
    assert data_json == {"system": {"min_loglevel": 200}}

    # 2. Dot notation
    data_dot = agent._parse_patch_data("set system.min_loglevel to 200")
    assert data_dot.get("system", {}).get("min_loglevel") == 200

    # 3. Proxy ID shortcut
    data_proxy = agent._parse_patch_data("set proxy_id to GAT-1")
    assert data_proxy.get("gateway", {}).get("gateway_id") == "GAT-1"

    # 4. Sample rate shortcut
    data_sr = agent._parse_patch_data("set pointset.sample_rate_sec=15")
    assert data_sr.get("pointset", {}).get("sample_rate_sec") == 15

    # 5. nostate flag
    data_nostate = agent._parse_patch_data("patch device AHU-1 with nostate")
    assert data_nostate.get("testing", {}).get("nostate") is True


def test_agent_run_deterministic_patch_site_model(tmp_path):
    site_dir = tmp_path / "sites" / "test_site"
    dev_dir = site_dir / "devices" / "AHU-1"
    dev_dir.mkdir(parents=True)
    meta_file = dev_dir / "metadata.json"
    meta_file.write_text('{"system": {"min_loglevel": 100}}\n')

    agent = MantisAgent(udmi_root=str(tmp_path))
    res = agent._run_deterministic(f"patch device AHU-1 in {site_dir} set system.min_loglevel to 200")

    assert "Applied Configuration Patch for `AHU-1`" in res
    import json
    updated = json.loads(meta_file.read_text())
    assert updated.get("system", {}).get("min_loglevel") == 200


def test_agent_tripartite_loop():
    """Test full Actor -> Critic -> Arbitrator tripartite cognitive loop."""
    # Step 1: Actor emits tool call
    resp_actor_tool = MockResponse(
        text=None,
        function_calls=[MockFunctionCall(name="inspect_udmi_schema", args={"schema_name": "pointset"})],
    )
    # Step 2: Actor finishes and emits hypothesis
    resp_actor_final = MockResponse(
        text="Actor hypothesis: Pointset schema requires points object.",
        function_calls=[],
    )
    # Step 3: Critic performs adversarial audit
    resp_critic = MockResponse(
        text="Critic audit: Claim confirmed by schema tool execution.",
        function_calls=[],
    )
    # Step 4: Arbitrator synthesizes final answer with diagram
    resp_arbitrator = MockResponse(
        text="Arbitrator verdict: Pointset schema requires points object.\n\n```mermaid\ngraph LR\n  A --> B\n```",
        function_calls=[],
    )

    mock_client = MockGenAIClient([resp_actor_tool, resp_actor_final, resp_critic, resp_arbitrator])
    agent = MantisAgent(client=mock_client)
    agent.config.provider_override = ProviderType.VERTEX_AI

    pro_model = agent.config.get_model_for_tier(ModelTier.PRO)

    output = agent._run_llm(
        prompt="Why did pointset fail for AHU-1?",
        tier=ModelTier.PRO,
        enable_tripartite=True,
        enable_scoping=False,
    )

    assert "Arbitrator verdict" in output
    assert "```mermaid" in output
    # Verify calls were made: 2 Actor turns, 1 Critic turn, 1 Arbitrator turn
    assert mock_client.models.call_count == 4
    # Verify Critic and Arbitrator used PRO tier model
    assert mock_client.models.calls[2][0] == pro_model
    assert mock_client.models.calls[3][0] == pro_model


def test_agent_run_tripartite_method():
    """Test agent.run_tripartite runs Scoping -> Actor -> Critic -> Arbitrator."""
    resp_scoping = MockResponse(
        text=(
            "FAILURE_SCOPE: NOT_A_FAILURE\n"
            "SCOPE_JUSTIFICATION: Informational request about state transitions.\n"
            "COMPETING_HYPOTHESES:\n"
            "- n/a\n"
            "REQUIRED_SUBSYSTEMS: docs, common"
        ),
        function_calls=[],
    )
    resp_actor = MockResponse(text="Actor answer", function_calls=[])
    resp_critic = MockResponse(text="Critic audit", function_calls=[])
    resp_arbitrator = MockResponse(text="Arbitrator final synthesis", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_actor, resp_critic, resp_arbitrator])
    agent = MantisAgent(client=mock_client)
    agent.config.provider_override = ProviderType.VERTEX_AI

    res = agent.run_tripartite("Explain device state transitions")
    assert res["status"] == "SUCCESS"
    assert res["final_answer"] == "Arbitrator final synthesis"
    assert res["metrics"] is not None
    # Scoping ran first, ahead of the Actor turn.
    assert mock_client.models.call_count == 4


def test_agent_run_deterministic_diagrams():
    """Test deterministic generation of topology and sequence diagrams."""
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC

    # Topology diagram
    out_topo = agent.run("Show topology diagram for sites/udmi_site_model")
    assert "Site Topology" in out_topo
    assert "digraph SiteTopology" in out_topo or "graph LR" in out_topo

    # Sequence diagram
    out_seq = agent.run("Show sequence diagram of test execution")
    assert "Sequence Execution Flow" in out_seq or "sequence diagram" in out_seq.lower()


def test_agent_run_deterministic_codebase_and_anomalies():
    """Test deterministic codebase search, doc location, and anomaly inspection."""
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC

    # Doc location
    out_doc = agent.run("Find spec for writeback")
    assert "UDMI Documentation for 'writeback'" in out_doc

    # Codebase search
    out_search = agent.run("Search codebase for SequenceBase")
    assert "Codebase search results" in out_search
    assert "SequenceBase" in out_search

    # Test inspection
    out_test = agent.run("Inspect sequencer test pointset_publish")
    assert "Sequencer Test: `pointset_publish`" in out_test


# ------------------------------------------------------------------------------
# Step Budget Exhaustion & Forced Synthesis
# ------------------------------------------------------------------------------

def test_agent_forced_synthesis_on_budget_exhaustion():
    """When the ReAct budget runs out mid-investigation, the Actor must still answer."""
    max_steps = 3
    # Model keeps requesting tools for the entire budget, never emitting a final answer.
    tool_responses = [
        MockResponse(
            text=None,
            function_calls=[MockFunctionCall(name="inspect_udmi_schema", args={"schema_name": "pointset"})],
        )
        for _ in range(max_steps)
    ]
    # The forced synthesis turn returns the real answer.
    synthesis_response = MockResponse(
        text="Root cause grounded in gathered evidence: the pointset schema requires a points map.",
        function_calls=[],
    )

    mock_client = MockGenAIClient(tool_responses + [synthesis_response])
    agent = MantisAgent(client=mock_client)

    chunks = []
    output = agent._run_llm(
        prompt="Why is the pointset failing?",
        max_steps=max_steps,
        stream_callback=chunks.append,
        enable_tripartite=False,
        enable_scoping=False,
    )

    # The Actor produced a genuine answer, not a raw dump of tool calls.
    assert "Root cause grounded in gathered evidence" in output
    assert "Actor exploration completed with the following tool evidence" not in output

    # One extra API call beyond the budget: the forced synthesis turn.
    assert mock_client.models.call_count == max_steps + 1

    # The synthesis turn must have tool access revoked.
    _, _, synthesis_config = mock_client.models.calls[-1]
    assert getattr(synthesis_config, "tools", None) is None

    # The user is told that synthesis was forced.
    assert any("Exploration budget" in c and "exhausted" in c for c in chunks)


def test_agent_budget_warning_injected_near_exhaustion():
    """The Actor is warned about remaining budget so it can converge proactively."""
    max_steps = 2
    tool_responses = [
        MockResponse(
            text=None,
            function_calls=[MockFunctionCall(name="inspect_udmi_schema", args={"schema_name": "pointset"})],
        )
        for _ in range(max_steps)
    ]
    synthesis_response = MockResponse(text="Final synthesized answer.", function_calls=[])

    mock_client = MockGenAIClient(tool_responses + [synthesis_response])
    agent = MantisAgent(client=mock_client)

    agent._run_llm(
        prompt="Investigate the failure",
        max_steps=max_steps,
        enable_tripartite=False,
        enable_scoping=False,
    )

    # Inspect the contents passed on the final synthesis call for the budget notice.
    _, final_contents, _ = mock_client.models.calls[-1]
    serialized = " ".join(
        getattr(part, "text", "") or ""
        for content in final_contents
        for part in getattr(content, "parts", []) or []
    )
    assert "Orchestrator Notice" in serialized
    assert "tool step(s) remaining" in serialized
    assert "Orchestrator Directive" in serialized


def test_agent_raises_when_synthesis_yields_no_answer():
    """Fail fast rather than emitting a degraded placeholder response."""
    max_steps = 2
    tool_responses = [
        MockResponse(
            text=None,
            function_calls=[MockFunctionCall(name="inspect_udmi_schema", args={"schema_name": "pointset"})],
        )
        for _ in range(max_steps)
    ]
    empty_synthesis = MockResponse(text="   ", function_calls=[])

    mock_client = MockGenAIClient(tool_responses + [empty_synthesis])
    agent = MantisAgent(client=mock_client)

    with pytest.raises(RuntimeError, match="failed to produce an answer"):
        agent._run_llm(
            prompt="Investigate the failure",
            max_steps=max_steps,
            enable_tripartite=False,
            enable_scoping=False,
        )


# ------------------------------------------------------------------------------
# Scoping Phase & Investigation Contract
# ------------------------------------------------------------------------------

SCOPING_PLAN = (
    "FAILURE_SCOPE: MULTI_TARGET_DEGRADATION\n"
    "SCOPE_JUSTIFICATION: Several independent targets fail simultaneously.\n"
    "COMPETING_HYPOTHESES:\n"
    "- Shared backend saturation\n"
    "- Client lifecycle defect\n"
    "REQUIRED_SUBSYSTEMS: validator, udmis"
)


def test_agent_scoping_phase_injects_contract():
    """Scoping runs before exploration and binds the Actor to a contract."""
    resp_scoping = MockResponse(text=SCOPING_PLAN, function_calls=[])
    resp_actor = MockResponse(text="Diagnosis complete.", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_actor])
    agent = MantisAgent(client=mock_client)

    chunks = []
    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        stream_callback=chunks.append,
    )

    assert output == "Diagnosis complete."
    # Scoping is the Actor's own first step; the investigation turn is second.
    assert mock_client.models.call_count == 2

    # Tool access must be withheld on the scoping step so it cannot be skipped.
    _, _, scoping_cfg = mock_client.models.calls[0]
    assert getattr(scoping_cfg, "tools", None) is None
    # ...and restored immediately afterwards.
    _, actor_contents, actor_cfg = mock_client.models.calls[1]
    assert getattr(actor_cfg, "tools", None) is not None

    # The plan must be recorded as the Actor's OWN model turn, not a user instruction.
    plan_turns = [
        c for c in actor_contents
        if getattr(c, "role", None) == "model"
        and any("MULTI_TARGET_DEGRADATION" in (getattr(p, "text", "") or "") for p in getattr(c, "parts", []) or [])
    ]
    assert plan_turns, "scoping plan was not recorded as a model turn"

    serialized = " ".join(
        getattr(part, "text", "") or ""
        for content in actor_contents
        for part in getattr(content, "parts", []) or []
    )
    assert "Scoping Step" in serialized
    assert "REQUIRED_SUBSYSTEMS: validator, udmis" in serialized
    assert any("Contracted subsystems" in c for c in chunks)


def test_agent_scoping_reports_coverage_gap():
    """Searching a subsystem is not examining it: a grep must not satisfy coverage."""
    resp_scoping = MockResponse(text=SCOPING_PLAN, function_calls=[])
    # Actor only ever greps validator, never opens a file, never touches udmis.
    resp_tool = MockResponse(
        text=None,
        function_calls=[MockFunctionCall(name="search_codebase", args={"query": "close", "path_prefix": "validator"})],
    )
    resp_final = MockResponse(text="Narrow diagnosis.", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_tool, resp_final])
    agent = MantisAgent(client=mock_client)

    agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
    )

    _, final_contents, _ = mock_client.models.calls[-1]
    serialized = " ".join(
        getattr(part, "text", "") or ""
        for content in final_contents
        for part in getattr(content, "parts", []) or []
    )
    assert "Contract coverage gap" in serialized
    # validator was searched but never read: still a gap, reported as such.
    assert "you have searched validator but have not opened any file there" in serialized
    # udmis was never touched at all.
    assert "you have not touched udmis at all" in serialized
    assert "Searching is not examining" in serialized


def test_agent_coverage_gap_cleared_by_reading_a_file():
    """Reading a file in a contracted subsystem clears it from the coverage gap."""
    resp_scoping = MockResponse(text=SCOPING_PLAN, function_calls=[])
    resp_tool = MockResponse(
        text=None,
        function_calls=[
            MockFunctionCall(
                name="read_udmi_file",
                args={"file_path": "validator/build.gradle", "start_line": 1, "end_line": 2},
            )
        ],
    )
    resp_final = MockResponse(text="Diagnosis.", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_tool, resp_final])
    agent = MantisAgent(client=mock_client)

    agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
    )

    _, final_contents, _ = mock_client.models.calls[-1]
    serialized = " ".join(
        getattr(part, "text", "") or ""
        for content in final_contents
        for part in getattr(content, "parts", []) or []
    )
    assert "validator" not in serialized.split("Contract coverage gap")[-1].split(".")[0]
    assert "you have not touched udmis at all" in serialized


def test_agent_scoping_rejects_nonexistent_subsystems():
    """Hallucinated directories are reported, not silently trusted."""
    plan = (
        "FAILURE_SCOPE: SINGLE_TARGET\n"
        "SCOPE_JUSTIFICATION: One device affected.\n"
        "COMPETING_HYPOTHESES:\n"
        "- Metadata defect\n"
        "REQUIRED_SUBSYSTEMS: validator, not_a_real_directory"
    )
    resp_scoping = MockResponse(text=plan, function_calls=[])
    resp_actor = MockResponse(text="Done.", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_actor])
    agent = MantisAgent(client=mock_client)

    chunks = []
    agent._run_llm(
        prompt="Why did one device fail?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        stream_callback=chunks.append,
    )

    joined = " ".join(chunks)
    assert "Ignoring subsystems absent from the repository" in joined
    assert "not_a_real_directory" in joined


def test_agent_scoping_raises_on_empty_plan():
    """Fail fast when the scoping planner returns nothing."""
    resp_scoping = MockResponse(text="", function_calls=[])
    mock_client = MockGenAIClient([resp_scoping])
    agent = MantisAgent(client=mock_client)

    with pytest.raises(RuntimeError, match="empty scoping plan"):
        agent._run_llm(
            prompt="Why are many devices timing out?",
            tier=ModelTier.PRO,
            enable_tripartite=False,
        )


# --- Streamed model turn coalescing -----------------------------------------
# Streaming splits one logical response into many fragments. Replaying that raw
# list as history sends a model turn containing content-less parts, which the API
# rejects with 400 "Requests ending with a model turn are not supported."

def _text_part(text):
    from google.genai import types
    return types.Part.from_text(text=text)


def _signature_only_part(signature=b"sig-bytes"):
    """A part with no text and no function_call, carrying only a thought_signature.
    Emitted as the final stream chunk when thinking_level is enabled."""
    from google.genai import types
    part = types.Part()
    part.thought_signature = signature
    return part


def test_coalesce_merges_streamed_text_fragments():
    from mantis.agent import _coalesce_model_parts
    parts = [_text_part("FAILURE_SCOPE: "), _text_part("MULTI_TARGET"), _text_part("_DEGRADATION")]
    result = _coalesce_model_parts(parts)
    assert len(result) == 1
    assert result[0].text == "FAILURE_SCOPE: MULTI_TARGET_DEGRADATION"


def test_coalesce_folds_signature_onto_preceding_part():
    """The trailing signature-only part must not survive as a standalone empty part,
    but its signature must be preserved: Vertex requires thought_signature round-trip."""
    from mantis.agent import _coalesce_model_parts
    parts = [_text_part("some reasoning"), _signature_only_part(b"abc")]
    result = _coalesce_model_parts(parts)
    assert len(result) == 1
    assert result[0].text == "some reasoning"
    assert result[0].thought_signature == b"abc"


def test_coalesce_produces_no_content_less_parts():
    """Regression for the Trial B 400. Reproduces the observed shape: 25 text
    fragments followed by one signature-only part."""
    from mantis.agent import _coalesce_model_parts
    parts = [_text_part(f"chunk{i} ") for i in range(25)] + [_signature_only_part()]
    result = _coalesce_model_parts(parts)
    for part in result:
        has_content = bool(getattr(part, "text", None)) or bool(getattr(part, "function_call", None))
        assert has_content, "coalesced model turn must not contain content-less parts"
    assert len(result) == 1


def test_coalesce_preserves_function_call_parts():
    from google.genai import types
    from mantis.agent import _coalesce_model_parts
    fc_part = types.Part.from_function_call(name="search_codebase", args={"query": "x"})
    parts = [_text_part("thinking "), _text_part("out loud"), fc_part]
    result = _coalesce_model_parts(parts)
    assert len(result) == 2
    assert result[0].text == "thinking out loud"
    assert result[1].function_call.name == "search_codebase"


def test_coalesce_drops_parts_carrying_nothing():
    from google.genai import types
    from mantis.agent import _coalesce_model_parts
    result = _coalesce_model_parts([_text_part("hello"), types.Part()])
    assert len(result) == 1
    assert result[0].text == "hello"


# --- Hypothesis resolution enforcement --------------------------------------
# The Actor generates a correct hypothesis during scoping and then abandons it
# without comment. The gate requires each to be CONFIRMED or REFUTED.

LABELLED_SCOPING_PLAN = """FAILURE_SCOPE: MULTI_TARGET_DEGRADATION
SCOPE_JUSTIFICATION: Several independent devices fail identically.
COMPETING_HYPOTHESES:
- H1: Backend dispatcher queue saturation drops responses.
- H2: Target devices are genuinely offline.
- H3: Routing affinity misdirects acknowledgements.
REQUIRED_SUBSYSTEMS: validator, udmis
"""


def _agent():
    return MantisAgent()


def test_parse_competing_hypotheses_extracts_labelled_entries():
    hypotheses = _agent()._parse_competing_hypotheses(LABELLED_SCOPING_PLAN)
    assert len(hypotheses) == 3
    assert hypotheses[0].startswith("Backend dispatcher queue saturation")
    assert hypotheses[2].startswith("Routing affinity")


def test_parse_competing_hypotheses_ignores_unlabelled_plan():
    """Unlabelled bullets are not tracked: the gate must not invent hypotheses
    it cannot deterministically match against later."""
    plan = "COMPETING_HYPOTHESES:\n- something vague\nREQUIRED_SUBSYSTEMS: validator\n"
    assert _agent()._parse_competing_hypotheses(plan) == []


def test_audit_violations_flags_silently_dropped_hypothesis():
    answer = (
        "H1: REFUTED - dispatcher metrics were nominal.\n"
        "H2: PRIMARY - devices show no active MQTT session.\n"
        "The routing layer looked fine overall."
    )
    violations = _agent()._audit_violations(answer, 3)
    assert any("H3" in v for v in violations)


def test_audit_violations_empty_when_ranked_and_complete():
    answer = "H1: REFUTED ...\nH2: PRIMARY ...\nH3: CONTRIBUTING ..."
    assert _agent()._audit_violations(answer, 3) == []


def test_audit_violations_rejects_endorsing_every_hypothesis():
    """The Trial D loophole: marking all hypotheses as causes excludes nothing."""
    answer = "H1: PRIMARY ...\nH2: PRIMARY ...\nH3: PRIMARY ..."
    violations = _agent()._audit_violations(answer, 3)
    assert any("PRIMARY" in v for v in violations)


def test_audit_violations_rejects_answer_with_no_primary():
    answer = "H1: CONTRIBUTING ...\nH2: CONTRIBUTING ...\nH3: REFUTED ..."
    violations = _agent()._audit_violations(answer, 3)
    assert any("No hypothesis is marked PRIMARY" in v for v in violations)


def test_audit_violations_rejects_mention_without_verdict():
    """Naming a hypothesis while dodging a verdict must not satisfy the gate."""
    answer = "Regarding H1, the backend seemed plausible but we moved on. H2: PRIMARY."
    violations = _agent()._audit_violations(answer, 2)
    assert any("H1" in v for v in violations)


def test_audit_violations_all_flagged_when_answer_omits_section():
    answer = "The devices are simply offline; register them and retry."
    violations = _agent()._audit_violations(answer, 3)
    assert any("H1" in v and "H2" in v and "H3" in v for v in violations)


def test_audit_violations_unresolved_counts_as_a_verdict_but_needs_primary():
    answer = "H1: UNRESOLVED ...\nH2: REFUTED ...\nH3: REFUTED ..."
    violations = _agent()._audit_violations(answer, 3)
    assert any("No hypothesis is marked PRIMARY" in v for v in violations)


def test_audit_violations_reads_verdict_rendered_below_the_hypothesis():
    """The audit is normally written as markdown, with the verdict on a nested
    bullet underneath the restated hypothesis rather than inline with it. A
    single-line scan rejected every such answer as unresolved, which discarded
    correctly audited arbitrator output and left the UI matrix blank."""
    answer = (
        "### Hypothesis Resolution Audit\n"
        "\n"
        "* **H1: The device failed to report the required error status.**\n"
        "  * **REFUTED**: The test timed out during setUp() before the broken\n"
        "    configuration was ever sent.\n"
        "* **H2: The sequencer ignored the device's state update as stale.**\n"
        "  * **PRIMARY**: The state timestamp lagged the cutoff threshold.\n"
    )
    verdicts = _agent()._hypothesis_verdicts(answer, 2)
    assert verdicts == {1: "REFUTED", 2: "PRIMARY"}
    assert _agent()._audit_violations(answer, 2) == []


def test_audit_violations_multiline_scan_does_not_borrow_next_verdict():
    """Spanning lines must not let an unresolved hypothesis claim the verdict
    belonging to the hypothesis rendered after it."""
    answer = (
        "* **H1: Something we never actually settled.**\n"
        "  * We ran out of evidence and moved on.\n"
        "* **H2: The real one.**\n"
        "  * **PRIMARY**: Confirmed by the dispatcher log.\n"
    )
    verdicts = _agent()._hypothesis_verdicts(answer, 2)
    assert verdicts == {1: None, 2: "PRIMARY"}


def test_gate_rejects_answer_that_drops_a_hypothesis_then_accepts_resolution():
    """End-to-end: the loop must reject an unresolved answer, push back, and accept
    the follow-up that resolves every hypothesis."""
    dropped = "The devices are simply offline. Register them and retry."
    resolved = (
        "H1: REFUTED - dispatcher queue depth was nominal in ReflectProcessor.\n"
        "H2: PRIMARY - no active MQTT session for the targets.\n"
        "H3: CONTRIBUTING - registry affinity map showed a single stable owner."
    )
    mock_client = MockGenAIClient([
        MockResponse(text=LABELLED_SCOPING_PLAN, function_calls=[]),
        MockResponse(text=dropped, function_calls=[]),
        MockResponse(text=resolved, function_calls=[]),
    ])
    agent = MantisAgent(client=mock_client)

    chunks = []
    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        stream_callback=chunks.append,
    )

    assert output == resolved
    assert mock_client.models.call_count == 3
    transcript = "".join(chunks)
    assert "[Mantis Gate] Answer rejected" in transcript
    assert "H1" in transcript and "H2" in transcript and "H3" in transcript


def test_gate_fires_only_once_so_a_noncompliant_model_still_terminates():
    """A model that never complies must not loop forever against the gate."""
    dropped = "The devices are simply offline."
    mock_client = MockGenAIClient([
        MockResponse(text=LABELLED_SCOPING_PLAN, function_calls=[]),
        MockResponse(text=dropped, function_calls=[]),
        MockResponse(text=dropped, function_calls=[]),
    ])
    agent = MantisAgent(client=mock_client)

    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
    )

    assert output == dropped
    assert mock_client.models.call_count == 3


def test_gate_inactive_when_scoping_produced_no_labelled_hypotheses():
    """Without labelled hypotheses there is nothing to enforce, so the first
    answer is accepted unchanged."""
    mock_client = MockGenAIClient([
        MockResponse(text=SCOPING_PLAN, function_calls=[]),
        MockResponse(text="Diagnosis complete.", function_calls=[]),
    ])
    agent = MantisAgent(client=mock_client)

    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
    )

    assert output == "Diagnosis complete."
    assert mock_client.models.call_count == 2


def test_forced_synthesis_carries_hypothesis_requirement():
    """Budget exhaustion must not become a bypass: the synthesis directive has to
    restate every committed hypothesis."""
    fc = MagicMock()
    fc.name = "search_codebase"
    fc.args = {"query": "x"}

    responses = [MockResponse(text=LABELLED_SCOPING_PLAN, function_calls=[])]
    # Step 0 is scoping, so max_steps=3 leaves exactly two tool steps before the
    # budget is exhausted. Never stop calling tools so synthesis is forced.
    responses += [MockResponse(text="", function_calls=[fc]) for _ in range(2)]
    responses += [MockResponse(text="Synthesized answer.", function_calls=[])]

    mock_client = MockGenAIClient(responses)
    agent = MantisAgent(client=mock_client)

    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        max_steps=3,
    )

    assert output == "Synthesized answer."
    synthesis_contents = mock_client.models.calls[-1][1]
    directive = synthesis_contents[-1].parts[0].text
    assert "budget is exhausted" in directive
    for label in ("H1:", "H2:", "H3:"):
        assert label in directive, f"{label} missing from synthesis directive"
    assert "PRIMARY" in directive
    assert "EXACTLY ONE" in directive


# --- Verdicts must be backed by examination ---------------------------------

def test_hypothesis_subsystems_maps_named_directories():
    hypotheses = [
        "Backend reflector routing in udmis drops downlinks.",
        "Client sequencer in validator and common hangs on timeouts.",
        "Something entirely unscoped.",
    ]
    mapping = _agent()._hypothesis_subsystems(hypotheses, ["validator", "udmis", "common"])
    assert mapping[1] == ["udmis"]
    assert mapping[2] == ["validator", "common"]
    assert mapping[3] == []


def test_refuting_an_unread_subsystem_is_rejected():
    """The Trial E failure: udmis hypothesis REFUTED after one grep, zero file reads."""
    answer = "H1: REFUTED - udmis forwarded correctly.\nH2: PRIMARY - devices offline."
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems={"validator"}
    )
    assert any("H1" in v and "udmis" in v for v in violations)


def test_primary_verdict_on_unread_subsystem_is_rejected():
    answer = "H1: PRIMARY - udmis saturated.\nH2: REFUTED - devices fine."
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems=set()
    )
    assert any("H1" in v and "udmis" in v for v in violations)


def test_unresolved_verdict_on_unread_subsystem_is_allowed():
    """UNRESOLVED is the honest option when the subsystem was never examined."""
    answer = "H1: UNRESOLVED - udmis not examined.\nH2: PRIMARY - devices offline."
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems=set()
    )
    assert not any("H1" in v for v in violations)


def test_verdict_accepted_once_the_subsystem_was_read():
    answer = (
        "H1: REFUTED - udmis forwarded correctly.\n"
        "RUNTIME_EVIDENCE: NONE (source inference only)\n"
        "H2: PRIMARY - devices offline."
    )
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems={"udmis"}
    )
    assert violations == []


# --- Backend verdicts must declare their evidentiary basis -------------------

def test_backend_verdict_without_declaration_is_rejected():
    """A backend verdict must say whether it was observed or inferred."""
    answer = "H1: PRIMARY - udmis saturates its dispatcher queue.\nH2: REFUTED - devices fine."
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems={"udmis"}
    )
    assert any("H1" in v and "RUNTIME_EVIDENCE" in v for v in violations)


def test_source_inference_declaration_is_accepted():
    """Arguing a backend cause from source alone is allowed when declared.

    This is the stale-incident case: the logs that would prove the mechanism are
    gone, but the mechanism is still legible in the source.
    """
    answer = (
        "H1: PRIMARY - udmis saturates its dispatcher queue.\n"
        "RUNTIME_EVIDENCE: NONE (source inference only) - the logs needed to prove this "
        "are not available; the mechanism is plausible from the source.\n"
        "H2: REFUTED - devices fine."
    )
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems={"udmis"}
    )
    assert violations == []


def test_log_backed_declaration_is_accepted():
    answer = (
        "H1: PRIMARY - udmis saturates its dispatcher queue.\n"
        "RUNTIME_EVIDENCE: LOCAL_FILE - out/udmis.log shows the queue at 1.000.\n"
        "H2: REFUTED - devices fine."
    )
    violations = _agent()._audit_violations(
        answer,
        2,
        {1: ["udmis"], 2: []},
        read_subsystems={"udmis"},
        obtained_evidence_tiers={"LOCAL_FILE"},
    )
    assert violations == []


def test_bare_none_declaration_is_rejected():
    """NONE must carry its qualifier, or the reader cannot tell inference from observation."""
    answer = (
        "H1: PRIMARY - udmis saturates its dispatcher queue.\n"
        "RUNTIME_EVIDENCE: NONE\n"
        "H2: REFUTED - devices fine."
    )
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems={"udmis"}
    )
    assert any("H1" in v and "qualifier" in v for v in violations)


def test_declaration_not_required_for_client_hypothesis():
    """Client-side subsystems are observable through existing test evidence."""
    answer = "H1: PRIMARY - validator drops the message.\nH2: REFUTED - devices fine."
    violations = _agent()._audit_violations(
        answer, 2, {1: ["validator"], 2: []}, read_subsystems={"validator"}
    )
    assert violations == []


def test_declaration_not_required_for_unresolved_backend_hypothesis():
    """UNRESOLVED asserts nothing about runtime behavior, so it declares nothing."""
    answer = "H1: UNRESOLVED - udmis not examined.\nH2: PRIMARY - devices offline."
    violations = _agent()._audit_violations(
        answer, 2, {1: ["udmis"], 2: []}, read_subsystems=set()
    )
    assert violations == []


def test_declaration_is_not_borrowed_from_a_neighbouring_hypothesis():
    """H2's declaration must not satisfy H1."""
    answer = (
        "H1: PRIMARY - udmis saturates its dispatcher queue.\n"
        "H2: REFUTED - udmis routing is fine.\n"
        "RUNTIME_EVIDENCE: LOCAL_FILE - out/udmis.log shows correct routing.\n"
    )
    declarations = _agent()._runtime_evidence_declarations(answer, 2)
    assert declarations[1] is None
    assert declarations[2] == "LOCAL_FILE"



# --- Content-less model turns must never reach the API ----------------------

def test_empty_model_turn_is_not_replayed_as_history():
    """Reproduces the Trial F crash signature: a Content with zero parts.

    The Actor returned a blank final answer, the hypothesis gate rejected it for
    having no verdicts, and the blank turn was then replayed as history. Vertex
    rejects a request containing a part-less Content with 400 'must include at
    least one parts field', which aborted the run and handed the diagnosis to the
    fallback engine.
    """
    resp_scoping = MockResponse(text=LABELLED_SCOPING_PLAN, function_calls=[])
    resp_blank = MockResponse(text="", function_calls=[])
    resp_final = MockResponse(
        text=(
            "H1: PRIMARY - backend saturation.\n"
            "RUNTIME_EVIDENCE: NONE (source inference only)\n"
            "H2: REFUTED - not the devices.\nH3: CONTRIBUTING - client waits."
        ),
        function_calls=[],
    )

    mock_client = MockGenAIClient([resp_scoping, resp_blank, resp_final])
    agent = MantisAgent(client=mock_client)

    chunks = []
    agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        stream_callback=chunks.append,
    )

    for _, contents, _ in mock_client.models.calls:
        for content in contents:
            parts = getattr(content, "parts", None) or []
            assert parts, f"a {getattr(content, 'role', '?')} turn was sent with no parts"
            assert any(
                (getattr(p, "text", "") or "").strip()
                or getattr(p, "function_call", None)
                or getattr(p, "function_response", None)
                or getattr(p, "thought_signature", None)
                for p in parts
            ), "a turn was sent whose parts carry no content"
    assert any("no usable content" in c for c in chunks)


def test_part_content_predicate():
    from mantis.agent import _part_carries_content

    class _Part:
        def __init__(self, text=None, function_call=None, thought_signature=None):
            self.text = text
            self.function_call = function_call
            self.function_response = None
            self.thought_signature = thought_signature

    assert not _part_carries_content(_Part(text=""))
    assert not _part_carries_content(_Part(text="   \n"))
    assert _part_carries_content(_Part(text="hello"))
    assert _part_carries_content(_Part(function_call=object()))
    assert _part_carries_content(_Part(thought_signature=b"sig"))


def test_unobtained_log_tier_declaration_is_rejected():
    """Trial G regression: LOCAL_FILE declared when no such tier was ever returned.

    The Critic caught this but the Arbitrator did not apply the correction, so the
    false declaration reached the final answer. The declaration is checkable
    against tool history, so it is checked.
    """
    answer = (
        "H1: REFUTED - udmis forwards errors correctly.\n"
        "RUNTIME_EVIDENCE: LOCAL_FILE\n"
        "H2: PRIMARY - devices offline."
    )
    violations = _agent()._audit_violations(
        answer,
        2,
        {1: ["udmis"], 2: []},
        read_subsystems={"udmis"},
        obtained_evidence_tiers=set(),
    )
    assert any("H1" in v and "LOCAL_FILE" in v for v in violations)


def test_declared_tier_accepted_when_the_tool_returned_it():
    answer = (
        "H1: REFUTED - udmis forwards errors correctly.\n"
        "RUNTIME_EVIDENCE: LOCAL_FILE - out/udmis.log line 42 shows the forward.\n"
        "H2: PRIMARY - devices offline."
    )
    violations = _agent()._audit_violations(
        answer,
        2,
        {1: ["udmis"], 2: []},
        read_subsystems={"udmis"},
        obtained_evidence_tiers={"LOCAL_FILE"},
    )
    assert violations == []


def test_compound_markers_detects_joined_clauses():
    """Trial H's H1 joined an offline-device claim to a thread-leak claim with
    'while', and the well-evidenced half carried the other to PRIMARY."""
    from mantis.agent import _compound_markers

    assert _compound_markers(
        "Devices are unregistered while executor threads fail to terminate."
    ) == ["while"]
    assert _compound_markers("Queue saturates; heartbeats are dropped.") == [";"]
    assert _compound_markers("The map is stale as well as unbounded.") == ["as well as"]


def test_compound_markers_allows_a_bare_and():
    """'and' routinely joins parts of ONE mechanism. Treating it as compound would
    reject sound hypotheses, so it is deliberately not a marker."""
    from mantis.agent import _compound_markers

    assert _compound_markers("The routing and dispatch layer drops acks.") == []
    assert _compound_markers("Queue saturation drops responses.") == []


COMPOUND_SCOPING_PLAN = """FAILURE_SCOPE: MULTI_TARGET_DEGRADATION
SCOPE_JUSTIFICATION: Several independent devices fail identically.
COMPETING_HYPOTHESES:
- H1: Devices are unregistered while validator threads fail to terminate.
- H2: Routing affinity misdirects acknowledgements.
REQUIRED_SUBSYSTEMS: validator, udmis
"""


def test_scoping_rejects_compound_hypothesis_and_rescopes():
    """A plan whose hypothesis asserts two mechanisms is sent back once, and the
    rewritten plan is what the investigation is held to."""
    final = (
        "H1: REFUTED - devices had active sessions.\n"
        "H2: CONTRIBUTING - threads leak but do not cause the timeouts.\n"
        "H3: PRIMARY - affinity map misdirects acknowledgements."
    )
    mock_client = MockGenAIClient([
        MockResponse(text=COMPOUND_SCOPING_PLAN, function_calls=[]),
        MockResponse(
            text=(
                "FAILURE_SCOPE: MULTI_TARGET_DEGRADATION\n"
                "SCOPE_JUSTIFICATION: Several independent devices fail identically.\n"
                "COMPETING_HYPOTHESES:\n"
                "- H1: Devices are unregistered in the cloud registry.\n"
                "- H2: Validator executor threads fail to terminate.\n"
                "- H3: Routing affinity misdirects acknowledgements.\n"
                "REQUIRED_SUBSYSTEMS: validator\n"
            ),
            function_calls=[],
        ),
        MockResponse(text=final, function_calls=[]),
    ])
    agent = MantisAgent(client=mock_client)

    chunks = []
    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        stream_callback=chunks.append,
        enable_tripartite=False,
    )

    transcript = "".join(chunks)
    assert "[Mantis Scoping] Plan rejected" in transcript
    assert "H1 joins clauses with 'while'" in transcript
    # Three hypotheses are tracked, which is only true of the REWRITTEN plan.
    assert "Tracking 3 hypotheses" in transcript
    assert output == final


def test_scoping_gate_fires_only_once_so_a_stubborn_plan_still_proceeds():
    """A model that re-emits the same compound plan must not loop on the gate."""
    # H1 names validator, which is never read here, so it must stay UNRESOLVED or the
    # unrelated read-before-ruling gate fires and masks what this test measures.
    final = (
        "H1: UNRESOLVED - validator source was not opened.\n"
        "H2: PRIMARY - affinity map misdirects acknowledgements."
    )
    mock_client = MockGenAIClient([
        MockResponse(text=COMPOUND_SCOPING_PLAN, function_calls=[]),
        MockResponse(text=COMPOUND_SCOPING_PLAN, function_calls=[]),
        MockResponse(text=final, function_calls=[]),
    ])
    agent = MantisAgent(client=mock_client)

    chunks = []
    output = agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        stream_callback=chunks.append,
        enable_tripartite=False,
    )

    transcript = "".join(chunks)
    assert transcript.count("[Mantis Scoping] Plan rejected") == 1
    assert "Tracking 2 hypotheses" in transcript
    assert output == final
    assert mock_client.models.call_count == 3


def test_clean_scoping_plan_is_not_rescoped():
    mock_client = MockGenAIClient([
        MockResponse(text=LABELLED_SCOPING_PLAN, function_calls=[]),
        MockResponse(
            text="H1: REFUTED ...\nH2: PRIMARY ...\nH3: CONTRIBUTING ...",
            function_calls=[],
        ),
    ])
    agent = MantisAgent(client=mock_client)

    chunks = []
    agent._run_llm(
        prompt="Why are many devices timing out?",
        tier=ModelTier.PRO,
        stream_callback=chunks.append,
        enable_tripartite=False,
    )

    assert "[Mantis Scoping] Plan rejected" not in "".join(chunks)
    assert mock_client.models.call_count == 2


def test_agent_llm_failure_fails_fast_without_silent_fallback():
    class FailingModels:
        def generate_content(self, *args, **kwargs):
            raise RuntimeError("Simulated Vertex AI 503 Service Unavailable")

    class FailingClient:
        models = FailingModels()

    agent = MantisAgent(client=FailingClient())
    with pytest.raises(RuntimeError, match="Simulated Vertex AI 503 Service Unavailable"):
        agent.run("Diagnose why the test failed")


def test_deterministic_does_not_fabricate_ahu1_on_generic_failure_query():
    agent = MantisAgent()
    agent.config.provider_override = ProviderType.OFFLINE_DETERMINISTIC
    res = agent.run("Diagnose why my build is broken")
    assert "AHU-1" not in res
    assert "pointset_publish" not in res
    assert "Please specify both the device ID and test name" in res


def test_get_genai_tools_fails_fast_on_error(monkeypatch):
    from mantis.tools.registry import get_genai_tools
    import mantis.tools.registry as reg

    # If get_tool_schemas yields a broken schema, get_genai_tools must raise RuntimeError, not return []
    monkeypatch.setattr(reg, "get_tool_schemas", lambda: [{"name": "bad_tool", "description": "desc", "parameters": 12345}])
    with pytest.raises(RuntimeError, match="Failed to create FunctionDeclaration"):
        get_genai_tools()


def test_agent_arbitrator_gate_violation_falls_back_to_actor(monkeypatch):
    from mantis.tests.test_agent import MockResponse, MockGenAIClient, MockFunctionCall

    resp_scoping = MockResponse(
        text=(
            "FAILURE_SCOPE: SINGLE_TARGET\n"
            "SCOPE_JUSTIFICATION: Single device test failure.\n"
            "COMPETING_HYPOTHESES:\n"
            "- H1: Config mismatch in common\n"
            "- H2: Timeout failure in common\n"
            "REQUIRED_SUBSYSTEMS: common"
        ),
        function_calls=[],
    )
    # Actor reads a file in common, then renders valid verdicts
    resp_actor_tool = MockResponse(
        text=None,
        function_calls=[MockFunctionCall(name="read_udmi_file", args={"file_path": "common/README.md"})],
    )
    actor_valid_text = (
        "Actor diagnosis:\n"
        "- H1: PRIMARY (Config mismatch confirmed)\n"
        "- H2: REFUTED (No timeout occurred)\n"
    )
    resp_actor = MockResponse(text=actor_valid_text, function_calls=[])
    resp_critic = MockResponse(text="Critic audit confirms actor findings.", function_calls=[])
    
    # Arbitrator emits an invalid verdict (both H1 and H2 marked PRIMARY, violating single-PRIMARY gate)
    arbitrator_invalid_text = (
        "Arbitrator synthesis:\n"
        "- H1: PRIMARY\n"
        "- H2: PRIMARY\n"
    )
    resp_arbitrator = MockResponse(text=arbitrator_invalid_text, function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_actor_tool, resp_actor, resp_critic, resp_arbitrator])
    agent = MantisAgent(client=mock_client)
    agent.config.provider_override = ProviderType.VERTEX_AI

    # Mock execute_tool to return file content for read_udmi_file
    monkeypatch.setattr("mantis.tools.registry.execute_tool", lambda **kwargs: "Mock content in common")

    res = agent.run_tripartite("Why did pointset_publish fail for AHU-1?")
    # Arbitrator's invalid answer should be rejected and fall back to actor_valid_text
    assert res["status"] == "DEGRADED"
    assert "Actor diagnosis:" in res["final_answer"]
    assert res["metrics"].tripartite_degraded is True
    assert res["metrics"].tripartite_status == "ARBITRATOR_GATE_VIOLATION"


def test_critic_snippet_includes_truncation_marker(monkeypatch):
    from mantis.tests.test_agent import MockResponse, MockGenAIClient, MockFunctionCall

    resp_actor_tool = MockResponse(
        text=None,
        function_calls=[MockFunctionCall(name="read_udmi_file", args={"file_path": "common/README.md"})],
    )
    resp_actor = MockResponse(text="Actor answer", function_calls=[])
    resp_critic = MockResponse(text="Critic audit", function_calls=[])
    resp_arbitrator = MockResponse(text="Arbitrator synthesis", function_calls=[])

    mock_client = MockGenAIClient([resp_actor_tool, resp_actor, resp_critic, resp_arbitrator])
    agent = MantisAgent(client=mock_client)
    agent.config.provider_override = ProviderType.VERTEX_AI

    # Return large tool output > 600 chars
    large_output = "X" * 1000
    monkeypatch.setattr("mantis.tools.registry.execute_tool", lambda **kwargs: large_output)

    agent._run_llm(
        prompt="Explain pointset",
        tier=ModelTier.PRO,
        enable_tripartite=True,
        enable_scoping=False,
    )

    # Inspect calls made to Critic (call index 2)
    model, critic_contents, config = mock_client.models.calls[2]
    critic_prompt = critic_contents[0].parts[0].text
    assert "...[truncated 400 chars]" in critic_prompt



def test_neutralize_schema_refs_renames_the_reserved_key():
    """Vertex reserves `$ref` inside function_response.response and resolves it
    against attached file parts. UDMI tool output carries real JSON Schema whose
    `$ref`s name other schema files, so the request is rejected with
    400 INVALID_ARGUMENT and the investigation dies mid-run.

    Probing the live API established that the KEY is the trigger, not the value:
    with `$ref` present every value was rejected identically, including an inert
    control string of ordinary prose.
    """
    assert _neutralize_schema_refs({"$ref": "file:common.json#/definitions/depth"}) == (
        {"ref": "file:common.json#/definitions/depth"}
    )
    # The exact payload that killed the recorded demo run.
    assert _neutralize_schema_refs({"$ref": "file:config_pointset.json#"}) == (
        {"ref": "file:config_pointset.json#"}
    )


def test_neutralize_schema_refs_preserves_the_value_exactly():
    """The value is irrelevant to Vertex, so it must survive untouched.

    An earlier fix stripped the `file:` scheme out of these values, which did not
    address the trigger and silently altered schema content on the way to the
    model. Renaming the key means the model sees the reference verbatim.
    """
    payload = {"$ref": "file:///abs/path/x.md"}
    assert _neutralize_schema_refs(payload) == {"ref": "file:///abs/path/x.md"}


def test_neutralize_schema_refs_reaches_nested_and_listed_refs():
    """`allOf`/`oneOf` wrap their refs in lists, and schemas nest arbitrarily.
    A single missed `$ref` anywhere in the payload rejects the whole request."""
    payload = {
        "properties": {"pointset": {"$ref": "file:config_pointset.json#"}},
        "allOf": [{"$ref": "file:test_base.json#"}, {"type": "object"}],
    }
    assert _neutralize_schema_refs(payload) == {
        "properties": {"pointset": {"ref": "file:config_pointset.json#"}},
        "allOf": [{"ref": "file:test_base.json#"}, {"type": "object"}],
    }


def test_neutralize_schema_refs_leaves_dollar_ref_inside_strings_alone():
    """Only a real JSON key triggers the scan; `$ref` inside a string value is
    inert. This is what makes the oversized-payload path safe, because that path
    sends the serialised JSON as a single string."""
    payload = {"truncated_snippet": '{"$ref": "file:common.json#"}'}
    assert _neutralize_schema_refs(payload) == payload


def test_neutralize_schema_refs_leaves_ordinary_payloads_untouched():
    """The rename must not disturb tool output that carries no schema at all."""
    payload = {"status": "SUCCESS", "rows": [1, 2, 3], "note": "see profile:x"}
    assert _neutralize_schema_refs(payload) == payload



# ------------------------------------------------------------------------------
# Informational Scope (NOT_A_FAILURE)
# ------------------------------------------------------------------------------

INFORMATIONAL_SCOPING_PLAN = (
    "FAILURE_SCOPE: NOT_A_FAILURE\n"
    "SCOPE_JUSTIFICATION: The user asks how a sequencer test works.\n"
    "COMPETING_HYPOTHESES:\n"
    "- H1: The test schedules a future scan.\n"
    "- H2: The test triggers an immediate scan.\n"
    "REQUIRED_SUBSYSTEMS: validator, schema"
)


def _serialized_text(contents):
    return " ".join(
        getattr(part, "text", "") or ""
        for content in contents
        for part in getattr(content, "parts", []) or []
    )


@pytest.mark.parametrize(
    "plan, expected",
    [
        ("FAILURE_SCOPE: NOT_A_FAILURE\n", "NOT_A_FAILURE"),
        ("FAILURE_SCOPE: **single_target**\n", "SINGLE_TARGET"),
        ("FAILURE_SCOPE: <MULTI_TARGET_DEGRADATION>\n", "MULTI_TARGET_DEGRADATION"),
        ("FAILURE_SCOPE: SOMETHING_ELSE\n", None),
        ("SCOPE_JUSTIFICATION: no scope line\n", None),
    ],
)
def test_parse_failure_scope(plan, expected):
    assert MantisAgent()._parse_failure_scope(plan) == expected


def test_informational_scope_answers_without_hypothesis_gate():
    """An explanation question must not be forced through the hypothesis audit.

    The plan lists hypotheses anyway, which models routinely do; they are discarded
    rather than tracked, so an answer with no verdicts is accepted on the first try.
    """
    resp_scoping = MockResponse(text=INFORMATIONAL_SCOPING_PLAN, function_calls=[])
    resp_actor = MockResponse(text="Summary: the test schedules a scan.", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_actor])
    agent = MantisAgent(client=mock_client)

    chunks, events = [], []
    output = agent._run_llm(
        prompt="Explain how scan_single_future works",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        stream_callback=chunks.append,
        event_callback=events.append,
    )

    assert output == "Summary: the test schedules a scan."
    # Scoping + one Actor turn: no gate rejection and no re-ask.
    assert mock_client.models.call_count == 2
    assert not any("Answer rejected" in c for c in chunks)
    assert any("Informational question" in c for c in chunks)
    assert any("discarded" in c for c in chunks)
    # Neither the plan's hypotheses nor an audit reach the UI.
    assert not [e for e in events if e["type"] in ("hypotheses", "audit")]

    _, actor_contents, _ = mock_client.models.calls[1]
    serialized = _serialized_text(actor_contents)
    assert "informational" in serialized
    assert "read the ENTIRE test method" in serialized
    assert "Scoping accepted. Tool access is now restored. Begin the investigation" not in serialized


def test_defect_scope_still_tracks_hypotheses():
    """The informational path must not weaken the gate for real defect reports."""
    plan = INFORMATIONAL_SCOPING_PLAN.replace("NOT_A_FAILURE", "SINGLE_TARGET")
    resp_scoping = MockResponse(text=plan, function_calls=[])
    resp_unaudited = MockResponse(text="It is the scheduler.", function_calls=[])
    resp_audited = MockResponse(
        text="H1: PRIMARY because the config sets a future generation.\n"
             "H2: REFUTED because the start time is in the future.",
        function_calls=[],
    )

    mock_client = MockGenAIClient([resp_scoping, resp_unaudited, resp_audited])
    agent = MantisAgent(client=mock_client)

    chunks, events = [], []
    agent._run_llm(
        prompt="Why does scan_single_future fail on DDC-1?",
        tier=ModelTier.PRO,
        enable_tripartite=False,
        stream_callback=chunks.append,
        event_callback=events.append,
    )

    assert any("Answer rejected" in c for c in chunks)
    assert [e for e in events if e["type"] == "hypotheses"]
    assert mock_client.models.call_count == 3


def test_informational_scope_uses_explanation_critic_and_arbitrator():
    """Critic audits completeness/accuracy; Arbitrator must not demand an audit section."""
    resp_scoping = MockResponse(text=INFORMATIONAL_SCOPING_PLAN, function_calls=[])
    resp_actor = MockResponse(text="Summary: the test schedules a scan.", function_calls=[])
    resp_critic = MockResponse(text="- Omitted Steps or Checks: scan pending wait.", function_calls=[])
    resp_arbitrator = MockResponse(text="Final explanation.", function_calls=[])

    mock_client = MockGenAIClient([resp_scoping, resp_actor, resp_critic, resp_arbitrator])
    agent = MantisAgent(client=mock_client)

    output = agent._run_llm(
        prompt="Explain how scan_single_future works",
        tier=ModelTier.PRO,
        enable_tripartite=True,
    )

    assert output == "Final explanation."
    _, _, critic_cfg = mock_client.models.calls[2]
    assert "informational question" in critic_cfg.system_instruction
    assert "Omitted Steps or Checks" in critic_cfg.system_instruction
    assert "Unsound Hypothesis Verdicts" not in critic_cfg.system_instruction

    _, arbitrator_contents, arbitrator_cfg = mock_client.models.calls[3]
    assert "Preserve the Actor's 'Hypothesis Resolution Audit'" not in arbitrator_cfg.system_instruction
    assert "retaining the Hypothesis Resolution Audit" not in _serialized_text(arbitrator_contents)


def test_informational_critic_sees_source_reads_in_full():
    """The Critic must see the helper body the Actor read, not its first 6000 chars.

    Live signature: a 400-line read of DiscoverySequences.java was cut before
    scanAndVerify, the Critic reported the body "never successfully read", and
    the Arbitrator deleted the correct step-by-step checks.
    """
    read_call = MockFunctionCall(
        name="read_udmi_file",
        args={
            "file_path": "validator/src/main/java/com/google/daq/mqtt/sequencer/sequences/DiscoverySequences.java",
            "start_line": 100,
            "end_line": 500,
        },
    )
    mock_client = MockGenAIClient([
        MockResponse(text=INFORMATIONAL_SCOPING_PLAN, function_calls=[]),
        MockResponse(function_calls=[read_call]),
        MockResponse(text="Summary: the test schedules a scan.", function_calls=[]),
        MockResponse(text="- Verified Claims: all.", function_calls=[]),
        MockResponse(text="Final explanation.", function_calls=[]),
    ])
    MantisAgent(client=mock_client)._run_llm(
        prompt="Explain how scan_single_future works",
        tier=ModelTier.PRO,
        enable_tripartite=True,
    )
    _, critic_contents, critic_cfg = mock_client.models.calls[3]
    evidence = _serialized_text(critic_contents)
    # Checks deep inside scanAndVerify, far past the old 6000-character cut.
    assert "received expected number of discovery events" in evidence
    assert "received proper discovery termination event" in evidence
    assert "Not verifiable from the excerpt" in critic_cfg.system_instruction


# ------------------------------------------------------------ cancellation ---
def test_cancel_before_first_step_makes_no_model_call():
    import threading

    from mantis.agent import MantisCancelled

    client = MockGenAIClient([MockResponse(text="never used")])
    cancel = threading.Event()
    cancel.set()
    chunks = []
    with pytest.raises(MantisCancelled, match="before step 1"):
        MantisAgent(client=client)._run_llm(
            prompt="Explain the pointset schema",
            stream_callback=chunks.append,
            enable_scoping=False,
            cancel_event=cancel,
        )
    assert client.models.call_count == 0
    assert any("Stopped by operator before step 1." in c for c in chunks)


def test_cancel_during_model_call_skips_the_requested_tool_and_adds_no_answer():
    import threading

    from mantis.agent import MantisCancelled
    from mantis.models import MessageRole, SessionContext

    cancel = threading.Event()

    class _StopDuringCall(MockModels):
        def generate_content(self, model, contents, config):
            # The operator presses Stop while this model call is in flight.
            cancel.set()
            return super().generate_content(model, contents, config)

    client = MockGenAIClient([])
    client.models = _StopDuringCall([
        MockResponse(function_calls=[MockFunctionCall(name="inspect_udmi_schema", args={"schema_name": "pointset"})]),
        MockResponse(text="never used"),
    ])
    records = []
    context = SessionContext(active_session_id="sess")
    with pytest.raises(MantisCancelled, match="before tool call inspect_udmi_schema"):
        MantisAgent(client=client).run(
            "Tell me about the pointset schema",
            context=context,
            event_callback=records.append,
            cancel_event=cancel,
        )
    assert client.models.call_count == 1
    assert not [r for r in records if r.get("type") == "tool_call"]
    assert all(m.role != MessageRole.ASSISTANT for m in context.history)
