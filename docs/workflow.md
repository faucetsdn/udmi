# UDMI Validation Workflow

The overall UDMI project validation workflow generally looks like this:
1. Login to the GCP account using `gcloud auth application-default login`
2. Setup a [`site_model`](site_model.md) in a git repo with appropriate configuration and metadata.
3. Run [`registrar`](registrar.md) with no specified project to validate metadata.
4. Use [`keygen`](keygen.md) as necessary to generate keys.
5. Run `registrar` tool again with a cloud project to actually register devices.
6. Do the needful to get data flowing from devices.
7. Run [`validator`](validator.md) to validate that the devices are producing the correct data.

It is strongly recommended to go through all of these steps with one or two test devices,
rather than trying to take on an entire site without fully vetting the workflow and tools.
The [pubber](pubber.md) tool can be used to mock out devices in lieu of physical devices,
this is very useful for isolating problems between a cloud-configuration issue vs. a
device configuration one.

An example site directory, as a reference, is available at [udmi_site_model](http://github.com/faucetsdn/udmi_site_model).
