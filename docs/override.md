# Point Fixed-Value Override

In this example, the "local" switch is modelled as a virtual on/off controller (think of a touch
panel with two buttons). When touched, it's priority _temporarily_ changes to _high_, and then
returns to _low_ after a (locally defined) timeout value.

| step     | local | priority | cloud | output | message     | notes                            |
|----------|-------|----------|-------|--------|-------------|----------------------------------|
| start    | off   | low      | off   | off    |             | quiescent state                  | 
| switch   | on    | high     | off   | on     | state/telem | manual switch on                 | 
| wait     | on    | high     | off   | on     |             | waiting for cloud response       |
| converge | on    | high     | on    | on     | config      | cloud converges with local       |
| expire   | on    | low      | on    | on     | state       | local override expires           |
| settle   | on    | low      | off   | off    | telem       | return to quiescent state        |

* Highest priority _local_ vs _cloud_ state determines the _output_ value.
* The cloud system is always at an implicit _medium_ priority. More complex interactions could
utilize other cloud priorities following the same logic.
* The _message_ sent in each case corresponds to three different associations:
  * _state_ messages represent the state of the _controller_ (the touch panel in this case).
  * _telem_ (telemetry) messages represent the state of the _light_ (visible output).
  * _config_ messages represent the cloud-based configuration/control of the system.
* Cloud interactions are _not_ real-time, so the delay between _switch_ and _converge_ could be
on the order of minutes in extreme cases.
