# Point Fixed-Value Override

In this example, the "local" switch is modelled as a virtual on/off controller (think of a touch
panel with two buttons). When touched, it's priority _temporarily_ changes to _high_, and then
returns to _low_ after a (locally defined) timeout value.

| step     | local | priority | cloud | output | message      | notes                            |
|----------|-------|----------|-------|--------|--------------|----------------------------------|
| start    | off   | low      | off   | off    |              | quiescent state                  |
| switch   | on    | high     | off   | off    | switch/telem | manual switch on                 |
| activate | on    | high     | off   | on     | act/telem    | actuator on                      |
| wait     | on    | high     | off   | on     |              | waiting for cloud response       |
| converge | on    | high     | on    | on     | config       | cloud converges with local       |
| expire   | on    | low      | on    | on     | switch/telem | local override expires           |
| settle   | on    | low      | off   | on     | config       | cloud-based shutdown             |
| restore  | on    | low      | off   | off    | act/telem    | return to quiescent state        |

* Highest priority _local_ vs _cloud_ state determines the _output_ value.
* The cloud system is always at an implicit _medium_ priority. More complex interactions could
utilize other cloud priorities following the same logic.
* The _message_ sent in each case corresponds to two different associations:
  * _telem_ (telemetry) messages represent output values from the _switch_ or _act_ (actuator).
  * _config_ messages represent the cloud-based configuration/control of the system.
* Cloud interactions are _not_ real-time, so the delay between _switch_ and _converge_ could be
on the order of minutes in extreme cases.
