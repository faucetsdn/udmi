[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Categories](#)

# Entry Error Categories

Categories are assembled in a hierarical fashion to represent the intended level
of detail (more specific is better). E.g. a complete system config parsing message
would be categorized as `system.config.parse`, while a system config entry of
unspecified category would be just `system.config`. Some categories come with
implicit expected `level` values, indicated by '(**LEVEL**)' in the hierarchy below.

* _system_: Basic system operation
  * _base_: Baseline system operational messages
    * _start_: (**NOTICE**) System is in the process of (re)starting and essentially offline
    * _shutdown_: (**NOTICE**) System is shutting down
    * _ready_: (**NOTICE**) System is fully ready for operation
    * _comms_: Baseline message handling
  * _config_: Configuration message handling
    * _receive_: (**DEBUG**) Receiving a config message
    * _parse_: (**DEBUG**) Parsing a received message
    * _apply_: (**NOTICE**) Application of a parsed config message
  * _network_: Network (IP) message handling
    * _connect_: (**NOTICE**) Connected to the network
    * _disconnect_: (**NOTICE**) Disconnected from a network
  * _auth_: Authentication to local application (e.g. web server, SSH)
    * _login_: (**NOTICE**) Successful login. The entry message should include the username and application
    * _logout_: (**NOTICE**) Successful logout 
    * _fail_: (**WARNING**) Failed authentication attempt. The entry message should include the application
* _pointset_: Handling managing data point conditions
  * _point_: Conditions relating to a specific point, the entry `message` should start with "Point _pointname_"
    * _applied_: (**INFO**) The `set_value` for a point has been applied
    * _updating_: (**NOTICE**) The point is in the process of updating
    * _overridden_: (**WARNING**) The reported value has been overridden locally
    * _failure_: (**ERROR**) The system failed to read/write the point
    * _invalid_: (**ERROR**) A `config` parameter for the point is invalid in some way
* _discovery_: Handling on-prem discovery flow
  * _family_: Conditions specific to an entire address family (e.g. bacnet)
    * _scan_: (**INFO**) Relating to scanning a particular address family
  * _device_: Conditions specific to device scanning
    * _enumerate_: (**INFO**) Handling point enumeration for a given device
  * _point_: Conditions specific to point enumeration
    * _describe_: (**INFO**) Relating to describing a particular point
* _blobset_: Handling update of device data blobs
  * _blob_: Conditions specific to an individual blob
    * _received_: (**DEBUG**) Request for an update has been received
    * _fetched_: (**DEBUG**) Update blob has been successfully fetched
    * _applied_: (**NOTICE**) Update has been successfully applied
