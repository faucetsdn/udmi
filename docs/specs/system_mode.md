[**UDMI**](../../) / [**Docs**](../) / [**Specs**](./) / [System Mode](#)

# System Mode

The system 'mode' controls the high-level operating mode of the entire system.
    
## Restart Sequence
```mermaid
flowchart LR
  Z[[SYSTEM<br/>RESTART]]
  style Z fill:#f88
  A[DEVICE STATE<br/><code>system.mode = <i>initial</i></code>]
  B[DEVICE STATE<br/><code>system.mode = <i>active</i></code>]
  C[DEVICE STATE<br/><code>system.mode = <i>restart</i></code>]
  Z --> A
  A -- DEVICE CONFIG <br/><code>system.mode = active</code> --> B
  B -- DEVICE CONFIG <br/><code>system.mode = restart</code> --> C
  C --> Z
```
  
Notes:
* The _active_/_restart_ config modes are explicitly to prevent restart loops.
* Conceptually, _restart_ could be other operating modes (e.g. _shutdown_).
* Initial device would be _undefined_ if capability is not supported (legacy).
