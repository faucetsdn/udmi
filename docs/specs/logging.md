# Logging

## Status Categories

This document defines some of the minimum predefined status categories for
system log messages or status entries. 

Other statuses and log entries with not described below can (and should) be
published. Additionally, other log/status entries related to these (such as
exceptions) should be published.

| Category | Scope | Loglevel |Description |
|---|---|---|---|
| `state.pointset.points.config.failure` | state.pointset.point | 600 | When a writeback failed to apply
| `state.pointset.points.config.invalid` | state.pointset.point, state.pointset | 600 | When a writeback is invalid (e.g. set_value, etag, expiry, )|
| `event.system.startup` | event.system | 300 | When the system has started |
| `event.system.gateway.attach` | event.system | 600 | Error gateway attaching to proxy device |
