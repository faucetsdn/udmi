[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Presentation Layer Roots](#)

# UDMI Presentation Layer Configuration

This document outlines the root schemas used to generate
the `gencode/presentation/presentation.json`configuration for the user
interface. The UI will render sections and fields based on the `$presentation`
attributes defined within these schemas and their references.

### Root Schemas

The following schemas are the primary entry points for building the presentation
layer. The generation script will parse this list and traverse each schema to
build the final configuration.

```json
{
  "roots": [
    "site_metadata.json",
    "configuration_execution.json",
    "metadata.json"
  ]
}
```

By defining the root schemas here, we ensure that the documentation and the
implementation are always in sync.

