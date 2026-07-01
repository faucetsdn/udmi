import asyncio
import yaml
from pathlib import Path
from typing import Any, Dict, List, Optional, Callable


class StageConfig:
    """Represents the configuration for a single pipeline stage."""

    def __init__(self, name: str, data: Dict[str, Any], pipeline_config: Dict[str, Any] = None):
        self.name = name
        self.enabled = data.get("enabled", True)
        self.system_instruction = data.get("system_instruction")
        self.headers = data.get("headers", [])
        self.tools = data.get("tools", [])
        self.type = data.get("type", "")
        self.target_stage = data.get("target_stage")
        
        model_val = data.get("model")
        if pipeline_config and model_val in pipeline_config:
            self.model = pipeline_config[model_val]
        else:
            self.model = model_val

    def __repr__(self):
        return f"StageConfig(name={self.name}, enabled={self.enabled}, model={self.model}, tools={self.tools}, type={self.type}, target_stage={self.target_stage})"


class Playbook:
    """
    Parses and represents a declarative SRE triage playbook.
    Configures active pipeline stages and tools dynamically.
    """

    def __init__(self, filepath: Path):
        self.filepath = Path(filepath)
        self.metadata: Dict[str, Any] = {}
        self.pipeline_config: Dict[str, Any] = {}
        self.stages: Dict[str, StageConfig] = {}
        self._raw_data: Dict[str, Any] = {}

    @classmethod
    def load_default(cls) -> "Playbook":
        """Loads the default fallback playbook config from the package directory."""
        default_path = Path(__file__).parent / "default_playbook.yaml"
        playbook = cls(default_path)
        playbook.reload()
        return playbook

    @classmethod
    async def load_default_async(cls) -> "Playbook":
        """Asynchronously loads the default fallback playbook config from the package directory."""
        default_path = Path(__file__).parent / "default_playbook.yaml"
        playbook = cls(default_path)
        await playbook.reload_async()
        return playbook

    @classmethod
    async def load_async(cls, filepath: Path) -> "Playbook":
        """Asynchronously loads and parses a playbook from YAML."""
        playbook = cls(filepath)
        await playbook.reload_async()
        return playbook

    def load(self) -> "Playbook":
        """Synchronously loads and parses a playbook from YAML."""
        self.reload()
        return self

    async def reload_async(self):
        """Asynchronously reloads the playbook file."""
        loop = asyncio.get_running_loop()
        content = await loop.run_in_executor(None, self._read_file)
        self._parse(content)

    def reload(self):
        """Synchronously reloads the playbook file."""
        content = self._read_file()
        self._parse(content)

    def _read_file(self) -> str:
        if not self.filepath.exists():
            raise FileNotFoundError(f"Playbook file not found at {self.filepath}")
        with open(self.filepath, "r", encoding="utf-8") as f:
            return f.read()

    def _parse(self, content: str):
        try:
            data = yaml.safe_load(content) or {}
        except yaml.YAMLError as e:
            raise ValueError(f"Failed to parse YAML playbook: {e}")

        self._raw_data = data
        self.metadata = data.get("metadata", {})
        self.pipeline_config = data.get("pipeline", {})
        self.extensions = data.get("extensions", {})


        stages_data = data.get("stages", {})
        self.stages = {}
        for name, stage_data in stages_data.items():
            self.stages[name] = StageConfig(name, stage_data or {}, self.pipeline_config)

    def is_stage_enabled(self, stage_name: str) -> bool:
        return (
            self.stages.get(stage_name).enabled
            if stage_name in self.stages
            else False
        )

    def get_stage_config(self, stage_name: str) -> Optional[StageConfig]:
        return self.stages.get(stage_name)

    def resolve_tools(
        self, stage_name: str, available_tools: Dict[str, Callable]
    ) -> Dict[str, Callable]:
        """
        Filters the available tools to only include the ones specified in the playbook for the given stage.
        Supports resolving both built-in ToolBelt methods and custom extensions.
        """
        stage_cfg = self.get_stage_config(stage_name)
        if not stage_cfg or not stage_cfg.enabled:
            return {}

        resolved = {}
        for tool_name in stage_cfg.tools:
            if tool_name in available_tools:
                resolved[tool_name] = available_tools[tool_name]
            elif hasattr(self, "extensions") and tool_name in self.extensions:
                ext_cfg = self.extensions[tool_name]
                cmd_args = ext_cfg.get("command")
                if not cmd_args:
                    print(f"Warning: Extension '{tool_name}' has no command configured.")
                    continue
                
                from engine.harness.plugin import SubprocessPluginRunner
                
                def make_wrapper(name: str, args: List[str]) -> Callable:
                    runner = SubprocessPluginRunner(args)
                    def wrapper(params: Dict[str, Any]) -> Dict[str, Any]:
                        """Executes the custom extension tool. Passes params payload to the plugin stdin."""
                        try:
                            return runner.run(params)
                        except Exception as e:
                            print(f"[Plugin Crash Guard] Extension '{name}' crashed: {e}")
                            return {
                                "status": "UNVERIFIED_PLUGIN_FAILURE",
                                "error": str(e)
                            }
                    wrapper.__name__ = name
                    return wrapper

                resolved[tool_name] = make_wrapper(tool_name, cmd_args)
            else:
                print(
                    f"Warning: Tool '{tool_name}' requested by stage '{stage_name}' is not available in the ToolBelt or Extensions."
                )
        return resolved
