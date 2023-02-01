[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Entries](#)

# Log and Status Entries

Both system log and various state status entries expect information in a well defined format
that can be properly handled in backend system processing.

## Level

General indication of the severity of the message, using standard categorizations:

| Level | Label | Description |
|---|---|---|
| 100 | DEBUG | Stream of consciousness useful for detailed debugging. |
| 200 | INFO | Just information about all that is well in the world. |
| 300 | NOTICE | Normal but significant events, such as start up, shut down, or a configuration change. |
| 400 | WARNING | Something is not right that should be investigated. |
| 500 | ERROR | Something bad that needs immediate attention. |

## Category

The entry _category_ is a dot-separated string providing a semantic hierarchy for a given message.
[Canonical categories](categories.md) correspond to expected values for log and status entries. These
are designed to be automatically categorized and processed by backend systems.

## Message

The entry _message_ is a single sentence of human-readable output describing what went wrong.
This will likely be directly exposed to a system operator to give them high-level information on
how to triage, diganose, or rectify the situation. Exact messages are not prescribed, although some
specific categories will have guiding principles (e.g. prepend point _message_ with "Point _pointname_").

## Detail

The entry _detail_ is use for specifically diaganosing the error. E.g. a complete stack-trace or parsing
message that details exactly where the error occured. This would be used for detailed debugging by
a domain expert.
