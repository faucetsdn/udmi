
## enumerate_multi (PREVIEW)

Check enumeration of multiple categories

1. Update config before enumeration not active
    * Add `discovery.enumerations` = { "features": `details`, "families": `details`, "points": `details` }
1. Wait for enumeration not active
1. Update config before matching enumeration generation
    * Add `discovery.generation` = `generation start time`
1. Wait for matching enumeration generation
1. Update config before cleared enumeration generation
    * Remove `discovery.generation`
1. Wait for cleared enumeration generation
1. Check that family enumeration size matches
1. Check that feature enumeration matches metadata
1. Check that all enumerated features are official buckets
1. Check that enumerated point count matches

Test passed.
