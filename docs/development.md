# Development 

## Configuring CI Tests

To enable the CI tests, there first needs to be a dedicated GCP Project with an IoT Core
registry which mirrors the [example site model](https://github.com/faucetsdn/udmi_site_model).
A Github secret must also configured to point to the GCP project

They key steps to setup the dedicated project are as follows:
1.  Setup up a GCP Project and IoT Core Registry. The 
    [cloud setup](cloud_setup.md) and [dashboard setup](dashboard.md) documents 
    give guidance on this. If GCP Cloud SDK and Firebase CLI are already
    installed, re-authentication may be required. The registry name and cloud
    region are as follows:
    -   **Registry Name**: ZZ-TRI-FECTA
    -   **Cloud Region**: us-central1
2.  Setup the site model by cloning the 
    [example site model](https://github.com/faucetsdn/udmi_site_model) 
    in the udmi root directory and running the [registrar](registrar.md) 
    tool to configure the site model in the IoT Core Registry.
    -   `git clone https://github.com/faucetsdn/udmi_site_model.git`
    -   `bin/registar <GCP_PROJECT_ID> udmi_site_model`
2.  Set up the [sequence tests](sequence.md). The public key used for the
    virtual device in the IoT Core registry is the public key from
    [`udmi_site_model/devices/AHU-1/rsa_public.pem`](https://raw.githubusercontent.com/faucetsdn/udmi_site_model/master/devices/AHU-1/rsa_public.pem).
    A `validator_config.json` configuration file is not needed (this is
    generated automatically during the CI test)
    -   The registry name is **ZZ-TRI-FECTA**. 
4.  A Github Secret needs to be added to the project. This is accessed from the
    Project's _Settings_ page. The secret is as follows:
        -   **Name**: GCP_TARGET_PROJECT
        -   **Value**: _GCP Project ID_
5.  Enable Github Actions

The workflow can be tested with an empty commit 
(`git commit --allow-empty -m "Blank commit to trigger CI"; git push`). 
On an unmodified branch, these tests should pass if correctly configured
        
