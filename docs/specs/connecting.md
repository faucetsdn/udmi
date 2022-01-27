# Connecting Devices to Cloud

## Abstract Framing
```
On-prem             Cloud
        /---*---\
 [D1]---|       |---[D1]
        | stuff |
 [D2]---|       |---[D2]
        \---*---/
```
* _Dx_:
  * **On-prem**:
  * **Cloud**:
* _\*_:_Connectivity_
  * **Network**
  * **Transport**

## Connection Models

### Direct
```
On-prem       Cloud
  
 [D1]----*----[D1]

 [D2]----*----[D2]
```

### Adapter
```
On-prem          Cloud
  
 [D1][D1']--*----[D1]

 [D2][D2']--*----[D2]
```

### Gateway
```
On-prem              Cloud
  
 [D1]-\           /--[D1]
       [G1]-*-[G1]
 [D2]-/           \--[D2]
```

### Gateway (Singleton)
```
On-prem              Cloud
  
 [D1]--[G1]-*-[G1]---[D1]
```

### Aggregator
```
On-prem              Cloud
  
 [D1]-\
       [A1]----------[A1]
 [D2]-/
```
