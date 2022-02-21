[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Categories](#)

# Entry Error Categories

Categories are assembled in a hierarical fashion to represent the intended level
of detail (more specific is better). E.g. a complete system config parsing message
would be categorized as `system.config.parse`, while a system config message of
unspecified category would be just `system.config`.

* _system_: Basic system operation
  * _base_: Baseline system operational messages
    * _start_: System is in the process of (re)starting and essentially offline
    * _ready_: System is fully ready for operation
    * _comms_: Baseline message handling
  * _config_: Configuration message handling
    * _receive_: Receiving of a config message
    * _parse_: Parsing of a receved message
    * _apply_: Application of a parsed
