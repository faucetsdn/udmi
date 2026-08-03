* The `moreutils` package (including `sponge`) is available and expected to be used in shell scripts for in-place file editing. Do not replace `sponge` with temporary files.
* When running local services or integration tests in unprivileged environments (without root/sudo permissions), specify an explicit port number in the target project specification (e.g., `//mqtt/localhost:18833` instead of `//mqtt/localhost`). This triggers automatic isolated mode in `shell_common.sh`, configuring unprivileged ports (`MQTT_PORT`, `ETCD_PORT`) and setting `UDMI_NO_SUDO=true` automatically without requiring root privileges.
* Do not amend commits (`git commit --amend`) or rewrite git history. Always create new, standard git commits.
* Do not automatically create git commits. Leave changes unstaged/uncommitted unless explicitly instructed by the user to commit.

