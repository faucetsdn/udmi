[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Categories](#)

# Entry Error Categories

Categories are assembled in a hierarical fashion to represent the intended level
of detail (more specific is better). E.g. a complete system config parsing message
would be categorized as `system.config.parse`, while a system config entry of
unspecified category would be just `system.config`. Some categories come with
implicit expected `level` values, indicated by '(**LEVEL**)' in the hierarchy below.

* _system_: Basic system operation
  * _base_: Baseline system operational messages
    * _start_: System is in the process of (re)starting and essentially offline
    * _ready_: System is fully ready for operation
    * _comms_: Baseline message handling
  * _config_: Configuration message handling
    * _receive_: Receiving a config message
    * _parse_: Parsing a receved message
    * _apply_: Application of a parsed config message
* _pointset_: Handling managing data point conditions
  * _point_: Conditions relating to a specific point, the entry `message` field should
  start with "Point _pointname_" followed by descriptive information.
    * _applied_ (**INFO**): The `set_value` for a point has been implied
    * _updating_ (**NOTICE**): The point is in the process of updating
    * _overridden_ (**WARNING**): The reported value has been overridden locally
    * _invalid_ (**ERROR**): A `config` parameter for the point is invalid in some way
