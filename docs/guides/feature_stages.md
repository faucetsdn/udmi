[**UDMI**](../../) / [**Docs**](../) / [Guides](#)

# Feature Stages

Sequence tests use the @Feature annotation to tag test functions with their stage of release.

* ALPHA
    * Definition
        * The feature (definition, code, schema, tests, etc) is still expected to change, maybe even substantially, while in ALPHA stage.
        * The feature may not work as expected.
        * Ready for external testing by manufacturers who are trusted testers/we have a close relationship with.
        * One CI test needs to pass.
* BETA
    * Definition
        * Development team has finished initial development of feature.
        * The feature is defined in device qualification plans.
        * Ready for external testing by many manufacturers.
        * The feature will only change if significant bugs or feature mismatch is found from the industry, partners, or testers.  (Note that feature mismatch should be a very rare occurrence if the feature was fully vetted in the ALPHA stage.)
* STABLE
    * "Qualification Lab” stage in SmartReady Managed Device Release Pipeline
    * Definition
        * Feature is complete, and should not change. 
        * Minor revision update (1.0.0 → 1.1.0) if feature changes.

## Example

[Sequence tests](../.././validator/src/main/java/com/google/daq/mqtt/sequencer/sequences/) contain @Feature annotations.

For example:

```
    @Test
    @Description("Restart and connect to same endpoint and expect it returns.")
    @Feature(stage = STABLE, bucket = SYSTEM_MODE)
    public void system_mode_restart() {
      // Prepare for the restart.
      final Date dateZero = new Date(0);
      ...
```

