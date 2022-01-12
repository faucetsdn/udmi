# State Message

* There is an implicit minimum update interval of _one second_ applied to state updates, and it
is considered an error to update device state more often than that. If there are multiple
_state_ updates from a device in under a second they should be coalessed into one update
(sent after an appropriate backoff timer) and not buffered (sending multiple messages).
* `last_config` should be the timestamp _from_ the `timestamp` field of the last successfully
parsed `config` message (not the timestamp the message was received/processed).