
## enumerate_features (PREVIEW)

Check enumeration of device features

1. Wait for config sync
1. Update config before enumeration not active:
    * Add `discovery` = { "depths": { "features": `entries` } }
1. Wait for enumeration not active
1. Wait for config sync
1. Update config before matching enumeration generation:
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Wait for config sync
1. Update config before cleared enumeration generation:
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that no family enumeration exists
1. Check that feature enumeration matches metadata
1. Check that all enumerated features are official buckets
1. Check that no point enumeration exists
