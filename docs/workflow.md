# UDMI Validation Workflow

The overall UDMI project validation workflow generally looks like this:
1. Setup site-specific repo with appropriate configuration and metadata.
2. Run [`registrar`](registrar.md) with no specified project to validate metadata.
3. Use [`keygen`](keygen.md) as necessary to generate keys.
4. Run `registrar` tool again with a cloud project to actually register devices.
5. Do the needful to get data flowing from devices.
6. Run [`validator`](validator.md) to validate that the devices are producing the correct data.

It is strongly recommended to go through all of these steps with one or two test devices,
rather than trying to take on an entire site without fully vetting the workflow and tools.
The [pubber](pubber.md) tool can be used to mock out devices in liu of physical devices,
this is very useful for isolating problems between a cloud-configuration issue vs. a
device configuraiton one.
