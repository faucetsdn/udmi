[**UDMI**](../../) / [**Docs**](../) / [**Guides**](./) / [Sequencing](#)

# Sequencing

Sequencing is a set of testing steps for verifying that _sequences_ of device behavior are correct.

## Developer Test Suite

`bin/test_sequencer $PROJECT_ID`

## Sequencer Testing

`bin/sequencer $PROJECT_ID`
`bin/pubber`

## Individual Tests

At its heard, sequence tests are JUnit `@Test` classes that can be run indivudally within an IDE. This
is useful for development because it enables breakpoints and all that jazz.
