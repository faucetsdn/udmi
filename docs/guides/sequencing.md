[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Sequencing](#)

# Sequencing

Sequencing is a set of testing steps for verifying that _sequences_ of device behavior are correct.

## Contained Test Suite

`bin/test_sequencer $PROJECT_ID`

* Sets up `/tmp/validator_config.json`
* Sets up `/tmp/pubber_config.json`
* Automatically runs `pubber` instance for `bin/sequencer` test run.
* Runs complete battery of sequence tests by default, or incremental tests from command line.
* Updates sequence cache in `validator/sequences/` to optimize incremental runs.
* Compares output in `/tmp/sequencer.out` against `etc/sequencer.out`

## Sequencer Testing

`bin/sequencer $PROJECT_ID`
`bin/pubber`

## Individual Tests

At its heard, sequence tests are JUnit `@Test` classes that can be run individually within an IDE. This
is useful for development because it enables breakpoints and all that jazz.
