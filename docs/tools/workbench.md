[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [workbench](#)

# UDMI Workbench

**UDMI Workbench** is a local web application for running sequencer compliance tests
against a device, reviewing recorded results, and diagnosing failures with the
[Mantis](mantis.md) agent. It drives the same `bin/sequencer`, `bin/udmi`, and
`bin/pubber` tools you would run by hand and shows their real output; it never
invents devices, tests, or results.

Architecture and API contracts for contributors are in
[`workbench/WORKBENCH_CONTRACTS.md`](../../workbench/WORKBENCH_CONTRACTS.md).

## Launching

`bin/workbench` starts the gateway (`workbench/server/gateway.py`) and opens it in a browser.

```bash
bin/workbench                  # start on port 8080 (or the next free port if 8080 is taken)
bin/workbench --port=9090      # start on an exact port; fails if another program holds it
bin/workbench stop             # stop the gateway on port 8080 (add --port=N for another port)
```

If a Workbench gateway is already running on the port, it is reused. Server output goes
to `out/workbench_server.log`. After pulling new code, run `bin/workbench stop` and start
again so the running gateway picks it up.

### What the launcher checks before starting

When no gateway is running on the port yet, `bin/workbench` does the following before starting one:

1. Stops if `lsof` is missing, because it uses `lsof` to find the gateway's port.
2. Runs `bin/ensure_venv`, which runs `bin/setup_base` when `venv/` is missing or was built
   from a different `etc/requirements.txt`.
3. Runs `python -m workbench.server.preflight`. It checks the Python modules, the UDMI common
   package (`common/src/main/python`, which the launcher puts on `PYTHONPATH`), `tmux`,
   `java`, Playwright's Chromium, and a model provider. It lists every problem with its fix.

System packages (`tmux`, `lsof`, Mosquitto) need root: install them with `sudo bin/setup_base`.

Mantis needs a model provider. Set one of:

| Provider | Setup |
|---|---|
| Google AI Studio | `export GEMINI_API_KEY=<key>` (or `GOOGLE_API_KEY`) |
| Vertex AI (one-time setup) | `gcloud auth application-default login`, then `bin/mantis setup --vertex=<project>[/<region>]` |
| Vertex AI (this shell only) | `export GOOGLE_CLOUD_PROJECT=<project>` (or let the application-default credentials name the project) |
| Offline (no model) | `export MANTIS_OFFLINE=true` |

A saved setup is applied at startup by `bin/mantis`, `bin/workbench` and the Workbench gateway. If the environment names a different provider (`GEMINI_API_KEY`, `GOOGLE_API_KEY`, `MANTIS_OFFLINE`, or a different `GOOGLE_CLOUD_PROJECT` / `GOOGLE_CLOUD_REGION`), they refuse to start and name both; unset the variable or run `bin/mantis setup --clear`.

## Layout

The header has two screens, **Sequencer** and **Devices**, plus three buttons:
**Workbench Logs** (terminal icon), **Settings** (gear icon), and the server status.
Mantis is a drawer on the right edge (the **MANTIS** tab, or `Ctrl/Cmd+K`) that stays
open across both screens.

## Sequencer screen (`/sequencer`)

### Run configuration
* **Site model**: chosen from the site models found under the UDMI checkout and any
  registered site-model roots. Use **Site model somewhere else? Add its path…** to register a
  directory outside the checkout (for example a lab repository such as
  `~/sites/ZZ-TEST-SITE`). Registered roots are stored in
  `~/.config/udmi/workbench.json`.
* **Device**: searchable device picker for the selected site model.
* **Project spec**: the target, in the form `bin/sequencer` accepts, e.g.
  `//mqtt/localhost:18833` locally or `//gbos/bos-platform-staging` in the cloud.
  Suggestions come from the site model's `cloud_iot_config.json`.
* **Options**: log level (`INFO`, `DEBUG` = `-v`, `TRACE` = `-vv`) and an optional
  serial number (`-s`).
* **Run selected** / **Stop**: start `bin/sequencer` for the checked tests, or stop the run
  (the whole process group is terminated). Only one run can execute at a time because
  `bin/sequencer` writes shared files; a second run is refused until the first finishes.
* **Email me when done**: email the results of this run when it finishes (see
  [Email notifications](#email-notifications)).

### Sequences
* The list is read from the compiled validator (`bin/sequencer_catalog`), grouped by
  feature bucket, with each test's stage.
* Filter by name, bucket, stage (**Stage: Preview & above** is the default, matching
  `bin/sequencer`), and by the last recorded result (**Passed**, **Skipped**, **Failed**).
* Each test shows its last recorded result from `<site_model>/out/devices/<device>/`.
  Click a test to open its artifacts (`sequence.md`, `sequence.log`, message traces), with
  a **Diagnose with Mantis** action for failed tests and **Explain this test** for any test.
* The console under the list streams the live `bin/sequencer` output, and the run summary
  shows pass/fail/skip/pending counts and the elapsed time. Closing the tab does not stop a
  run; reopening the Workbench reattaches to it.

### Local Test Setup drawer
**Start Local Setup** opens a drawer that runs a local, unprivileged test stack for the
selected site model with `bin/udmi start/stop`. Enter a local project spec with an explicit
port (e.g. `//mqtt/localhost:18833`) in the drawer. It shows the topology and the health of
Mosquitto, UDMIS, and etcd, and can start a Pubber instance as the device under test.
Physical devices are the default; the connection card shows the broker address and
credentials the physical device must use. Keyboard shortcut: `Ctrl/Cmd+Shift+L`.

## Devices screen (`/devices`)

For the selected site model, the Devices screen shows what the sequencer recorded for each
device: the score per feature bucket and stage (the same matrix `bin/sequencer_report`
writes into `results.md`), the project each run actually targeted, and download links for
the `results.md` and `RESULT.log` reports that exist on disk. `0/0` means not applicable,
and only `stable` and `beta` results decide a verdict.

* **Commit results…** commits the changes sequencer runs made to the site model, restricted
  to the site-model directory. The dialog lists the affected devices and files, and the
  branch and remote are always chosen explicitly.

**Export support bundle** (in the Sequencer run summary and in the artifact viewer)
packages the selected site model, the UDMI `out/` directory and the cached tool configs
into a `.tar.gz` for sharing with a device manufacturer. Private key files are left out.

## Mantis drawer

Ask questions about UDMI or a test ("How does `scan_single_future+bacnet` work?"), or
triage a failure from the test list. The context line shows the site model, device, and
test that Mantis will use. Each answer shows its reasoning (phases, tool calls) in a
collapsible **Agent Reasoning** section and, for triage, a hypothesis audit below the
report. Diagrams can be copied or downloaded, and **Copy transcript** copies the whole chat
as markdown.

* **Stop** replaces **Send** while Mantis is working. It stops the run at the next step
  boundary; nothing from the stopped run is kept.
* **Clear** resets the conversation.
* Slash commands: `/help`, `/status`, `/logs <window>`, `/clear`.
* **Email me when done**: email this answer when it is ready.

See [Mantis](mantis.md) for how Mantis reasons and how to use it from the command line.

## Email notifications

Sequencer runs and Mantis answers can take minutes. With email notifications you can close
the tab and get the full result in your inbox.

1. Your Application Default Credentials must include the Gmail send scope:
   ```bash
   gcloud auth application-default login --scopes=https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/gmail.send,openid,https://www.googleapis.com/auth/userinfo.email
   ```
2. Open **Settings** (gear icon) and allow the Workbench to email results to your address.
   Use **Send test email** to check delivery. Consent is stored in
   `~/.config/udmi/workbench.json` and is tied to that address; mail is always sent from
   and to your own account, never to anyone else.
3. Tick **Email me when done** under **Run selected**, or under the Mantis message box,
   before starting. The checkbox applies to that one run or message and then turns off.

A run with notification turned on keeps going if you close the tab. A sequencer email
carries the verdict, the pass/fail/skip counts, every test that did not pass with its
message, and that run's `results.md` as an attachment. A Mantis email carries the question,
the answer with diagrams as images, and the hypothesis audit. Pressing **Stop** cancels the
run and sends nothing. **Settings** lists recent deliveries, including failures and why they
failed.

### Desktop notifications

Every finished sequencer run and Mantis answer also raises a desktop notification,
whether or not **Email me when done** is ticked. It appears only while the Workbench tab
is not in front (hidden, minimized, or behind another window), and clicking it brings
the tab forward. The browser asks for permission the first time you press **Run
selected**, **Send** or **Diagnose**. If you block it, the app bar shows **Desktop
notifications blocked** until you allow notifications for this site in the browser's
site settings. With the tab closed there is no desktop notification; use email for that.

## Workbench Logs

The terminal icon opens a drawer with the structured logs of the browser and the gateway,
filterable by level and source and linked by correlation ID. Use it when a request fails.
