[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Errors](#)

# Entry error categories

## Level

* ERROR
* WARNING
* INFO
* DEBUG

## Categories

The entry _category_ is a dot-separated string providing a canonical hierarchy for a given message.
For example, a `system.config.parse` entry corresponds to a configuration parsing error.

* _system_: Messages relating to basic system operation
  * _config_: Messages relating to handling of a config update
    * _parse_: Could not parse configuration (JSON syntax error)

## Message

Should be roughly a _single sentence_ of human-readable output describing what went wrong.

## Detail

More detail used for specifically diagnosing the error. E.g. a complete stack-trace or parsing
message that details exactly where the error occured.
