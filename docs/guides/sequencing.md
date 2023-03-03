[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Sequencing](#)

# Sequencing

Sequencing is a set of testing steps for verifying that _sequences_ of device behavior are correct.

## Contained Test Suite

`bin/test_sequencer $PROJECT_ID`

This:
* Sets up `/tmp/validator_config.json`
* Sets up `/tmp/pubber_config.json`
* Automatically runs `pubber` instance for `bin/sequencer` test run.
* Runs complete battery of sequence tests by default, or incremental tests from command line.
* Updates sequence cache in `validator/sequences/` to optimize incremental runs.
* Compares output in `/tmp/sequencer.out` against `etc/sequencer.out`

## Sequencer Testing

In two separate terminals:
* `pubber/bin/run /tmp/pubber_config`
* `bin/sequencer $PROJECT_ID`

This:
* Runs pubber with the configuration setup by `bin/test_sequencer`
* Pubber does not normally need to be restarted with each run.
* Runs the sequencer, which can likewise be limited to individual tests.
* Doesn't check results or do any other wrapped-for-testing processing.

## Individual Tests

At its heart, sequence tests are JUnit `@Test` classes that can be run individually within an IDE. This
is useful for development because it enables breakpoints and all that jazz. Still need to run the two
parts, with several different options:

For Pubber:
* `bin/pubber` with appropriate arguments.
* `pubber/bin/run /tmp/pubber_config` as per above.
* Run pubber in the IDE if you want to breakpoint, etc...

For sequence testing:
* `bin/sequencer` as per above.
* Direct in IDE as a JUnit @Test. Either at the class level or method level.
