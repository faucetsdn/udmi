[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Entries](#)

# Log and Message Entries

## Level

* ERROR
* WARNING
* INFO
* DEBUG

## Categories

The entry _category_ is a dot-separated string providing a canonical hierarchy for a given message.
For example, a `system.config.update` entry corresponds to a configuration parsing error.

* _system_: Basic system operation
  * _config_: Configuration message handling
    * _receive_: Receiving of a config message
    * _parse_: Parsing of a receved message
    * _apply_: Application of a parsed


## Message

The entry _message_ is a single sentence of human-readable output describing what went wrong.

## Detail

More detail used for specifically diagnosing the error. E.g. a complete stack-trace or parsing
message that details exactly where the error occured.
