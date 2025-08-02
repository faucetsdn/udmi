[**UDMI**](../../) / [**Docs**](../) / [Guides](#)

# Feature Sequence Stages

The following are feature release stages and their definition within the UDMI development environment:

* ALPHA
    * The feature (definition, code, schema, tests, etc) is still expected to change, maybe even substantially, while in ALPHA stage.
    * The feature may not work as expected.
* PREVIEW
    * The feature is actively under co-development with at least one external partner.
    * The feature may not work as expected, and is expected to change somewhat.
* BETA
    * Development team has finished development of feature.
    * Ready for external testing by many manufacturers.
    * The feature will only change if significant bugs or feature mismatch is found from the industry, partners, or testers.
* STABLE
    * High confidence the feature will not change. 

## Code Example

Our [sequence tests](../.././validator/src/main/java/com/google/daq/mqtt/sequencer/sequences/) have @Feature annotations. This ties the stage closely to the code which is ideal. Here are two examples of annotations:

```
@Feature(stage = STABLE, bucket = SYSTEM_MODE)
@Feature(stage = BETA)
```
