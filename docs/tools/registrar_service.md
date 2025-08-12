[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Registrar Service](#)

# Registrar Service

The Registrar Service is a backend tool that automates the process of registering devices within the UDMI framework. It acts as a wrapper for the core Registrar tool, listening for specific update events in a Google Cloud Source Repository. When triggered by a push to the `main` branch, it automatically clones the repository, runs the registration process against the site model, and logs the output.

---
## 1. Prerequisites

Before running the service, ensure you have the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) installed and configured on your local machine.

---

## 2. Running the Registrar Service

### 2.1. Authentication

First, you'll need to authenticate with your Google Cloud account. This command will open a browser window for you to log in.

```shell
gcloud auth login
```

Next, obtain application-default credentials. These credentials allow the Registrar service to authenticate with Google Cloud APIs, such as Pub/Sub and Google Sheets, on your behalf.

```shell
gcloud auth application-default login \
--scopes="openid,[https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets](https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets)"
```

### 2.2. Starting the Service

To start the Registrar service, navigate to your UDMI project's root directory and run the `registrar_service` script with the required arguments.
#### Command syntax:

```shell
services/bin/registrar_service ${message_spec} ${registrar_target} ${cloning_dir} [options]
```

#### Arguments & Options:

| Argument/Option        | Description                                                                                                                                                                             | Example                                               |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| `${message_spec}`      | **(Required)** Defines the message source. For a production environment, this will be a Google Cloud Pub/Sub topic. For local testing, you can use an MQTT broker.                      | `//pubsub/bos-platform-dev/udmis`, `//mqtt/localhost` |
| `${registrar_target}`  | **(Required)** The target project and service for the registration process itself.                                                                                                      | `//gbos/bos-platform-dev/udmis`, `//mqtt/localhost`   |
| `${cloning_dir}`       | **(Required)** An absolute path to a local directory where the service will temporarily clone git repositories for import and export operations.                                        | `/tmp/udmi/sites`                                     | 
| `--local`              | **(Optional)** A flag to run the service in a local-only mode, often used with an MQTT message_spec for development.                                                                    | N/A                                                   |
| `--create`             | **(Optional)** A flag that attempts to create the necessary Google Cloud Pub/Sub topics and subscriptions if they don't already exist. This is useful for initial setup.                | N/A                                                   |
| `local_origin_dir`     | **(Optional)** Provides an absolute path to a local directory containing git repositories. This is used for testing to simulate a remote origin without making actual network requests. | `/home/user/udmi_test_repos`                          |

### 2.3. Message Format and Triggering

The Registrar Service is triggered by standard notifications from Google Cloud Source Repositories sent over Pub/Sub. It does not use a custom message format.

The service specifically filters for and processes messages that indicate a `CREATE` or `UPDATE_FAST_FORWARD` event on the repository's main branch. All other repository events are ignored.

Here is a sample message:
```json
{
  "name": "projects/bos-platform-dev/repos/ZZ-TRI-FECTA-1",
  "url": "https://source.developers.google.com/p/bos-platform-dev/r/ZZ-TRI-FECTA-1",
  "eventTime": "2025-06-24T07:09:38.818511Z",
  "refUpdateEvent": {
    "email": "user@example.com",
    "refUpdates": {
      "refs/heads/main": {
        "refName": "refs/heads/main",
        "updateType": "UPDATE_FAST_FORWARD",
        "oldId": "b0495df010eef73fa3ce5325ad3c1784e9b7d126",
        "newId": "d609da9d365386b83b6eb235488af6bf377ba2f9"
      }
    }
  }
}
```

---

### 3. Building and Deploying the Container

To package the Registrar service into a Docker container for deployment, use the provided `container` script.

First, set your target GCP project and optional UDMI namespace:

```shell
bin/set_project gcp_project[/udmi_namespace]
```

Then, navigate to the UDMI root and use the container script with one of the following commands:
```shell
# General Syntax: bin/container services.registrar {command} [repo]
bin/container services.registrar push
```

| Command   | Action                                                                                                |
|-----------|-------------------------------------------------------------------------------------------------------|
| `prep`    | Prepares dependencies and prerequisites for the container build.                                      |
| `build`   | Builds the Docker container image locally.                                                            |
| `push`    | Pushes the built container image to a container registry (pushes to GHCR if `repo` is not specified). |
| `apply`   | Applies the container to GKE cluster                                                                  |
