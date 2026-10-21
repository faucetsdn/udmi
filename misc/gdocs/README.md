# Google Docs API Helper (`misc/gdocs/`)

This directory provides a canonical Python helper module and CLI ([`doc_builder.py`](doc_builder.py)) for creating, updating, and fetching Google Docs directly via the Google Docs v1 REST API (`https://docs.googleapis.com/v1/documents`).

## Why Direct Google Docs API via `venv/bin/python3` Is Used

Do **not** use `/google/bin/releases/gemini-agents-gdocs/gdocs` on roaming-client workstations:
1. The release binary has `0444` permissions (`Permission denied` on direct execution).
2. Its internal `ssoproxyclient` dials `blade:sso` over `Stubby`, which fails on `roaming-clients` `LOAS2` certificates with `RPC::RPC_RESTRICTIONS_VIOLATION` / `RPC::DEADLINE_EXCEEDED`.

Instead, run [`misc/gdocs/doc_builder.py`](doc_builder.py) using `/home/peringknife/udmi/venv/bin/python3`, which authenticates via Google Application Default Credentials (`~/.config/gcloud/application_default_credentials.json`) and works inside the standard sandbox (`BypassSandbox: false`).

## CLI Usage

### 1. Create a New Google Doc from Markdown

```bash
/home/peringknife/udmi/venv/bin/python3 /home/peringknife/udmi/misc/gdocs/doc_builder.py create \
  --title "Document Title" \
  --markdown /path/to/content.md
```

### 2. Update (Replace Body of) an Existing Google Doc from Markdown

```bash
/home/peringknife/udmi/venv/bin/python3 /home/peringknife/udmi/misc/gdocs/doc_builder.py update \
  --doc-id "<DOCUMENT_ID>" \
  --markdown /path/to/content.md
```

### 3. Fetch an Existing Google Doc as Markdown

```bash
/home/peringknife/udmi/venv/bin/python3 /home/peringknife/udmi/misc/gdocs/doc_builder.py fetch \
  --doc-id "<DOCUMENT_ID>" \
  --output /path/to/output.md
```

## Programmatic Python Usage (`DocBuilder`)

```python
from misc.gdocs.doc_builder import DocBuilder, b, c, get_docs_service, link

client = get_docs_service()
created = client.create_document("My Spec")
doc_id = created["documentId"]

builder = DocBuilder()
builder.add_heading("Specification Title", level=1)
builder.add_paragraph([
    "Configure ",
    c("localnet.networks"),
    " in ",
    b("metadata.json"),
    " (see ",
    link("Modbus Spec", "https://github.com/faucetsdn/udmi"),
    ").",
])
builder.add_code_block('{\n  "family": "modbus"\n}')
builder.add_table([
    ["Key", "Type"],
    [c("baud"), c("string")],
])
builder.apply(client, doc_id, clear_existing=False)
```
