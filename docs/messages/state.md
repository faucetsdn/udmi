[**UDMI**](../../) / [**Docs**](../) / [**Messages**](./) / [State](#)

# State Message

**Schema Definition:** [state.json](../../schema/state.json)
 ([_🧬View_](../../gencode/docs/state.html))

* There is an implicit minimum update interval of _one second_ applied to state updates, and it
is considered an error to update device state more often than that. If there are multiple
_state_ updates from a device in under a second they should be coalesced into one update
(sent after an appropriate backoff timer) and not buffered (sending multiple messages).
* `last_config` should be the timestamp _from_ the `timestamp` field of the last successfully
parsed `config` message (not the timestamp the message was received/processed).
* The state message are sent as a part of [sequences](../specs/sequences/)

This [working example](../../tests/state.tests/example.json) shows how a typical `state` message
is constructed.