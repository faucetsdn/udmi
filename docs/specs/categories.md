[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Categories](#)

# Entry Error Categories

Categories are assembled in a hierarical fashion to represent the intended level
of detail (more specific is better). E.g. a complete system config parsing message
would be categorized as `system.config.parse`, while a system config entry of
unspecified category would be just `system.config`.

* _system_: Basic system operation
  * _base_: Baseline system operational messages
    * _start_: System is in the process of (re)starting and essentially offline
    * _ready_: System is fully ready for operation
    * _comms_: Baseline message handling
  * _config_: Configuration message handling
    * _receive_: Receiving of a config message
    * _parse_: Parsing of a receved message
    * _apply_: Application of a parsed config message
* _pointset_: Handling managing data point conditions
  * _point_: Conditions relating to a specific point, the affected _point_ name should be included in the entry message
    * _invalid_: A `config` parameter for the point is invalid in some way
    * _applied_: The `set_value` for a point has been implied
    * _overridden_: The reported value has been overridden locally
    * _updating_: The point is in the process of updating
