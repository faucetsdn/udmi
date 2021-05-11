# Development 

## Configuring CI Tests

To enable the CI tests, you must set up a dedicated GCP Project with an IoT Core
registry which mirrors the [example site model](https://github.com/faucetsdn/udmi_site_model), 
and configure a GitHub secret to point to your GCP project

The guidance below describes the steps you need to take enable Github Actions
your own fork

1.  Follow the [cloud setup](cloud_setup.md) and [dashboard setup](dashboard.md)
    guides to set up a new GCP project and IoT Core registry with the
    configuration below. If you have already installed the GCP Cloud SDK and
    Firebase CLI, you will need to change projects. You may be required to
    reauthenticate or change your IAM permissions if you used a service account.
    Please refer to GCP and Firebase documentation as required
    -   **Registry Name**: ZZ-TRI-FECTA
    -   **Cloud Region**: us-central1
2.  In your UDMI directory, clone the 
    [example site model](https://github.com/faucetsdn/udmi_site_model)
    -   `git clone https://github.com/faucetsdn/udmi_site_model.git`
3.  Run the registrar tool on the example site model to register the devices
    -   `bin/registar <GCP_PROJECT_ID> udmi_site_model`
2.  Follow the [sequence tests setup](sequence.md) guide, however instead of
    generating a new public/private key, you should use the public key from
    `udmi_site_model/devices/AHU-1/rsa_public.pem` to register the validator.
    The registry name is **ZZ-TRI-FECTA**. You do not need to create a
    `validator_config.json`
4.  Add a Github Secret
    -   Click on _Settings_
    -   Click _Secrets_ on the sidebar
    -   Click _New Repository Secret_
        -   **Name**: GCP_TARGET_PROJECT
        -   **Value**: _Your GCP Project ID_
5.  Enable Github Actions
6.  Push a commit to your repo to test the actions workflow works
    -   If you are using an unmodified branch, then the tests should pass
    -   To push a blank commit, you can use
        `git commit --allow-empty -m "Blank commit to trigger CI"; git push`
