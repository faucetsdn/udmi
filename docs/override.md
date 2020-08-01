# Point Fixed-Value Override

In this example, the "local" switch is modelled as a virtual on/off switch (think of a touch
panel with two buttons). When touched, it's priority _temporarily_ changes to _high_, and then
returns to _low_ after a (locally defined) timeout value.

| step     | local | priority | cloud | output | notes                      |
|----------|-------|----------|-------|--------|----------------------------|
| start    | off   | low      | off   | off    | start of day               | 
| switch   | on    | high     | off   | on     | user switch on             |
| converge | on    | high     | on    | on     | cloud converges with local |
| expire   | on    | low      | on    | on     | local override expires     |
| settle   | on    | low      | off   | off    | return to quiescent state  |

* Highest priority state determines the _output_ value.
* The cloud system is always at an implicit _medium_ priority. More complex interactions could
utilize other cloud priorities following the same logic.
* Cloud interactions are _not_ real-time, so the delay between _switch_ and _converge_ could be
on the order of minutes in extreme cases.
