Backend service for BAMBI (BOS Automated Management Building Interface).

Build and push using bin/container as done for other modules.
```shell
bin/set_project gcp_project[/udmi_namespace]
bin/container bambi { prep, build, push, apply } [--no-check] [repo]
```