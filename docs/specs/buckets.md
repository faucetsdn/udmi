[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Categories](#)

# Entry Error Categories

Categories are assembled in a hierarical fashion to represent the intended level
of detail (more specific is better). E.g. a complete system config parsing message
would be categorized as `system.config.parse`, while a system config entry of
unspecified category would be just `system.config`. Some categories come with
implicit expected `level` values, indicated by '(**LEVEL**)' in the hierarchy below.

* _enumeration_: Basic platform property enumeration
  * _pointset_: Conditions specific to an entire address family (e.g. bacnet)
  * _features_: Conditions specific to an entire address family (e.g. bacnet)
  * _families_: Conditions specific to an entire address family (e.g. bacnet)
* _discovery_: Handling on-prem discovery flow
  * _scan_: (**INFO**) Relating to scanning a particular address family
* _endpoint_: Handling on-prem discovery flow
  * _config_: Handling on-prem discovery flow
* _system_: Basic system operations
  * _mode_: System mode testing
