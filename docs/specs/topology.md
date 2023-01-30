[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [Topology](#)

# Topology

## Terminology

* local networks
  * properties
* direct devices
  * networks
  * credentials
* proxy devices
  * gateway
  * networks
  * through
* pointset mapping
  * ref

## Example Topology

Example `ZZ-ABC-ATL` site topology:
```mermaid
%%{wrap}%%
flowchart LR
  D2[<u>DEV-02</u><br/>0x827323]
  D1[<u>DEV-01</u>]
  LG[<u>ALG-01</u><br/>0x712387<br/>plc-master]
  D3[<u>DEV-03</u><br/>plc-9<br/>192.168.1.3]
  D4[<u>DEV-04</u><br/>0x92a344<br/>192.168.1.2]
  IG[<u>GAT-01</u><br/>0xa982b7<br/>192.168.1.1]
  BN([<i>bacnet-10</i><br/>0x??????])
  MB([<i>modbus</i><br/>plc-???])
  IP([<i>upnp</i><br/>192.168.x.x])
  IN([<i>internet</i>])
  CP[<b>Cloud Provider</b><br/>endpoint_url:???<br/>project_id/<i>???</i><br/>registry/<i>ZZ-ABC-ATL</i><br/>device/<i><u>IOT-ID</u></i>]
  D2 ==> BN
  D4 ==> IP
  D3 --> IP
  LG ==> BN
  D4 --> BN
  D1 ==> IN
  D3 ==> MB
  IG ==> IN
  BN ==> IG
  IP ==> IG
  IN ==> CP
  MB ==> LG
```

The corresponding `encoded information` provides all the details necessary to define the topology:
* local networks
  * `bacnet-10`: family _bacnet_, network-number _10_
  * `modbus`: family _modbus_, baud _9600_
  * `upnp`: family _upnp_
* direct devices
  * `DEV-01`:
  * `GAT-01`:
    * network `bacnet-10`: address _0xa982b7_
    * network `upnp`: address _192.168.1.1_
* proxy devices
  * `DEV-02`
    * gateway `GAT-01`
    * network `bacnet-10`: address _0x827323_
  * `DEV-03`
    * gateway `ALG-01` (through `modbus`)
    * network `modbus`: address _plc-9_
    * network `upnp`: address _192.168.1.3_
  * `DEV-04`
    * gateway `GAT-01` (through `upnp`)
    * network `bacnet-10`: address _0x92a344_
    * network `upnp`: address _192.168.1.2_
  * `ALG-01`
    * gateway `GAT-01` (through `bacnet-10`)
    * netowrk `bacnet-10`: address _0x712387_
    * network `modbus`: address _plc-master_
* pointset mapping
  * `DEV-01`
    * points
      * _master\_frambibulator_
  * `DEV-02`
    * points (for `GAT-01`/`bacnet-10`)
      * _abstract\_air\_handler_: ref _AV10.present_value_
      * _fixator\_resonant\_structure_: ref _BV2.present_value_
  * `DEV-03`
    * points (for `ALG-01`/`modbus`)
      * _reticulating\_reticulator_: ref _reg-10_
      * _running\_rabbit\_speed_: ref _reg-21_
  * `DEV-04`
    * points (for `GAT-01`/`upnp`)
      * _figurating\_flambing_: ref _points.json#.points.figurating\_flambing.present\_value_
