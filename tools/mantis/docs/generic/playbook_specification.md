# Mantis Declarative Playbook Specification

Mantis uses a declarative YAML configuration format called a **Playbook** to control active pipeline stages, specify target GenAI models, customize system instructions, limit loops, register toolsets, and define custom log parsing extensions.

---

## 1. Playbook Schema Overview

A playbook YAML contains four primary sections:
1. `metadata`: Descriptive tags (name, version, target app context).
2. `pipeline`: Global execution variables (default model, loop bounds, timeout policies).
3. `extensions`: Custom log parsing patterns and plugin runner commands.
4. `stages`: Step-by-step diagnostic agent configs (timeline, intent, analysis, critique).

```yaml
metadata:
  name: "Sample Triage Playbook"
  description: "Declarative guidelines for system triage"
  version: "1.0.0"

pipeline:
  default_model: "gemini-3.5-pro"
  max_loops: 8
  fail_open_timeout_seconds: 45

extensions:
  log_parser:
    type: "regex"
    pattern: '^(?P<timestamp>\S+) \[(?P<severity>\w+)\] (?P<tag>\w+): (?P<message>.*)$'
    timestamp_format: "%Y-%m-%dT%H:%M:%S.%fZ"

stages:
  timeline:
    enabled: true
    model: "gemini-3.5-flash-lite"
    system_instruction: "Construct a chronological sequence from the raw logs."
    headers:
      - "## Chronological Sequence"
    tools:
      - read_file_lines
  analysis:
    enabled: true
    model: "gemini-3.5-pro"
    tools:
      - list_directory
      - grep_codebase
  critique:
    enabled: true
    type: "critique"
    target_stage: "analysis"
```

---

## 2. Parameter Definitions

### 2.1. `pipeline` Configuration Keys
* **`default_model`** (*string*): The default Gemini model to route stages to if no stage-specific override model is set. (Must target `gemini-3.5-pro`, `gemini-3.5-flash`, or `gemini-3.5-flash-lite`).
* **`max_loops`** (*integer*): Safety execution boundary. The maximum number of iterative tool execution runs allowed in the analysis phase before terminating. (Prevents infinite loops).
* **`fail_open_timeout_seconds`** (*integer*): Maximum time budget allowed for any external tool or model query before continuing with partial results.

### 2.2. `extensions` (Extensibility Hooks)
* **`log_parser`** (*object*):
  * **`type`**: Currently supporting `"regex"`.
  * **`pattern`**: A Python-compatible regular expression string containing named capture groups. Supported groups:
    * `(?P<timestamp>...)`: The timestamp string. (Required).
    * `(?P<severity>...)`: Log severity/level (e.g. `ERROR`, `WARNING`, `INFO`).
    * `(?P<tag>...)`: Component tag or logger name (e.g. `pubber`, `db`).
    * `(?P<message>...)`: The primary message payload string.
  * **`timestamp_format`** (*string*, optional): A `strptime`-compatible format string to parse the custom timestamp.

### 2.3. `stages` Configuration Keys
Each stage under `stages` maps to a specific GenAI execution step:
* **`enabled`** (*boolean*): Set `false` to completely skip execution of this stage.
* **`model`** (*string*, optional): Route this stage to a specific model (e.g., `gemini-3.5-flash-lite` for timelines, `gemini-3.5-pro` for deep analysis).
* **`system_instruction`** (*string*, optional): System-level prompt injected in the model context to enforce specific formatting or domain rules.
* **`tools`** (*array*, optional): List of allowed codebase tools this stage is permitted to invoke. Unlisted tools are locked out (Security sandboxing).
* **`type`** (*string*, optional): The stage evaluation type. If set to `"critique"`, the stage functions as a sanity checker.
* **`target_stage`** (*string*, optional): (Required if `type` is `"critique"`). Specifies which stage output this stage should critique and evaluate.
