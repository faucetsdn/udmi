# Mantis Language-Agnostic Plugin Developer Guide

Mantis supports extending log parsing, content scrubbing, and code analysis tools using custom scripts or binaries written in any programming language (Go, C++, Java, Node.js, Python, or Bash). 

Plugins communicate with the host Python executor via standard subprocess channels (`stdin` and `stdout`) using serialized JSON messages.

---

## 1. The IPC Protocol (JSON over Stdin/Stdout)

When the Mantis orchestrator invokes an extension plugin:
1. It launches the configured command as a subprocess, redirecting `stdin`, `stdout`, and `stderr`.
2. It writes a single-line or block-serialized JSON payload to the subprocess's `stdin`.
3. It closes the `stdin` stream (sending EOF) to signal that all parameter inputs have been written.
4. The plugin reads the JSON parameters from `stdin`, executes its internal logic, and writes a single JSON output payload to `stdout`.
5. The plugin exits with status `0` (Success) or $\neq 0$ (Failure).

---

## 2. Input & Output Payloads

### 2.1. Standard Log Parser Plugin
* **Input JSON** (`stdin`):
  ```json
  {
    "input_file_path": "/tmp/mantis/run_trace.log",
    "byte_offset": 0,
    "byte_limit": 5000000
  }
  ```
  > [!NOTE]
  > To avoid memory bloat and execution delays over standard pipes, large payloads are never passed directly as strings. The orchestrator writes the chunk to a local workspace file and passes the reference path and byte boundaries.

* **Output JSON** (`stdout`):
  The plugin must write a JSON array of parsed, normalized log lines:
  ```json
  {
    "lines": [
      {
        "timestamp": "2026-06-12T10:00:00.000Z",
        "severity": "INFO",
        "tag": "sequencer",
        "message": "Starting test case 'system_min_loglevel'"
      },
      {
        "timestamp": "2026-06-12T10:00:01.050Z",
        "severity": "ERROR",
        "tag": "pubber",
        "message": "Connection refused by MQTT broker"
      }
    ]
  }
  ```

---

## 3. Error Handling and Stderr Contract

* **Panic Isolation**: If a plugin fails or crashes, it must write a description of the failure to `stderr` and exit with a non-zero exit code (e.g. `exit(1)`).
* **Crash Guards**: The Mantis host orchestrator will catch the non-zero exit code, capture the raw logs from the plugin's `stderr` channel, and output them to the main triage trace for the developer, marking that candidate stage or hypothesis as `UNVERIFIED_PLUGIN_FAILURE` without crashing the main run.

---

## 4. Sample Implementations

### 4.1. Go Implementation Example
Below is a simple Go-based plugin that parses JSON input, performs a task, and returns JSON output:

```go
package main

import (
	"encoding/json"
	"fmt"
	"io"
	"os"
)

type InputParams struct {
	InputFilePath string `json:"input_file_path"`
	ByteOffset    int64  `json:"byte_offset"`
	ByteLimit     int64  `json:"byte_limit"`
}

type LogLine struct {
	Timestamp string `json:"timestamp"`
	Severity  string `json:"severity"`
	Message   string `json:"message"`
}

type OutputPayload struct {
	Lines []LogLine `json:"lines"`
}

func main() {
	// 1. Read stdin
	inputBytes, err := io.ReadAll(os.Stdin)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[ERROR] Failed to read stdin: %v\n", err)
		os.Exit(1)
	}

	// 2. Parse input JSON
	var params InputParams
	if err := json.Unmarshal(inputBytes, &params); err != nil {
		fmt.Fprintf(os.Stderr, "[ERROR] Invalid input JSON: %v\n", err)
		os.Exit(1)
	}

	// 3. Perform file reading/parsing locally
	file, err := os.Open(params.InputFilePath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "[ERROR] Failed to open file: %v\n", err)
		os.Exit(1)
	}
	defer file.Close()

	// ... [Implementation parsing code logic here] ...

	// 4. Write output JSON to stdout
	result := OutputPayload{
		Lines: []LogLine{
			{Timestamp: "2026-06-12T10:00:00Z", Severity: "INFO", Message: "Sample parsed line"},
		},
	}
	outputBytes, _ := json.Marshal(result)
	os.Stdout.Write(outputBytes)
}
```

### 4.2. Python Script Example
A simple Python-based companion script example:

```python
import sys
import json

def main():
    try:
        # Read parameters from stdin
        params = json.load(sys.stdin)
        target_file = params.get("input_file_path")
        
        # ... process log file ...
        
        # Output JSON result
        result = {
            "lines": [
                {"timestamp": "2026-06-12T10:00:00Z", "severity": "WARNING", "message": "Example output"}
            ]
        }
        print(json.dumps(result))
    except Exception as e:
        print(f"[ERROR] Script failed: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == '__main__':
    main()
```
