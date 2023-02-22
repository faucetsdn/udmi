[**UDMI**](../../../) / [**Docs**](../../) / [**Specs**](../) / [**Sequences**](./) / [Generated](#)

# Generated sequences

These are the exact sequences being checked by the sequence tool. They are programmatically generated
so maybe a bit cryptic, but they accurately represent the specific steps required for each test.

<!--

To regenerate the contents of this file below, first generate a message trace sequence and then run bin/gencode_seq

* Running "bin/test_sequencer target-gcp-project" will run through the complete battery of test sequences
  against the AHU-1 device to create the requisite trace files. This takes about 4 min for a complete test run.

* Then run "bin/gencode_seq" which consumes the generated trace files and creates "generated.md" (this file)
  with the output. The diff (using git, usually) should then reflect the changes against the committed version.

Some caveats:

* Flaky tests are annoying. Sometimes something goes wrong and one entire test will be borked. Easist thing
  is to just re-run the sequence tests until it's clean, but that's not always the fastest.

* The gencode part requires a complete test run to work properly, but you can run individual test runs
  as needed, e.g. "bin/sequencer sites/udmi_site/model target-gcp-project AHU-1 21632 system_last_update"
  (you will need to run an instance of pubber separately).

-->

<!-- START GENERATED, do not edit anything after this line! -->
* [extra_config](#extra_config): Check that the device correctly handles an extra out-of-schema field
* [system_last_update](#system_last_update): Check that last_update state is correctly set in response to a config update.

## extra_config (BETA)

Check that the device correctly handles an extra out-of-schema field

1. Update config before last_config not null:
    * Set `system.min_loglevel` = `100`
1. Wait for last_config not null
1. Wait for system operational
1. Check that no interesting system status
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for last_config updated
1. Wait for system operational
1. Check that no interesting system status
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged
1. Wait for log category `system.config.receive` level `DEBUG` was logged
1. Wait for last_config updated again
1. Wait for system operational
1. Check that no interesting system status
1. Wait for log category `system.config.parse` level `DEBUG` was logged
1. Wait for log category `system.config.apply` level `NOTICE` was logged

## system_last_update (STABLE)

Check that last_update state is correctly set in response to a config update.

