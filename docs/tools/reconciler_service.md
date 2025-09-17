[**UDMI**](../../) / [**Docs**](../) / [**Tools**](./) / [Reconciler Service](#)

# Reconciler Service

The Reconciler Service automates the initial steps of the code review process within UDMI. It actively monitors Google Cloud Source Repositories for new branches prefixed with `proposal/`. When a new proposal branch is created, the service automatically generates a pull request to merge the changes into the `main` branch, streamlining the workflow for submitting and reviewing configuration changes.

---

## 1. Prerequisites

Before running the service, ensure you have the [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) installed and configured on your local machine.

---

## 2. Running the Reconciler Service

### 2.1. Authentication

First, you'll need to authenticate with your Google Cloud account. This command will open a browser window for you to log in.

```shell
gcloud auth login
```

Next, obtain application-default credentials. These credentials allow the Reconciler service to authenticate with Google Cloud APIs, such as Pub/Sub and Google Sheets, on your behalf.

```shell
gcloud auth application-default login \
--scopes="openid,[https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets](https://www.googleapis.com/auth/userinfo.email,https://www.googleapis.com/auth/cloud-platform,https://www.googleapis.com/auth/spreadsheets)"
```

### 2.2. Starting the Service

To start the Reconciler service, navigate to your UDMI project's root directory and run the `reconciler_service` script with the required arguments.

#### Command syntax:

```shell
services/bin/reconciler_service ${message_spec} ${cloning_dir} [options]
```

#### Arguments & Options:

| Argument/Option     | Description                                                                                                                                                                             | Example                                               |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------|
| `${message_spec}`   | **(Required)** Defines the message source. For a production environment, this will be a Google Cloud Pub/Sub topic. For local testing, you can use an MQTT broker.                      | `//pubsub/bos-platform-dev/udmis`, `//mqtt/localhost` |
| `${cloning_dir}`    | **(Required)** An absolute path to a local directory where the service will temporarily clone git repositories for import and export operations.                                        | `/tmp/udmi/sites`                                     | 
| `--local`           | **(Optional)** A flag to run the service in a local-only mode, often used with an MQTT message_spec for development.                                                                    | N/A                                                   |
| `--create`          | **(Optional)** A flag that attempts to create the necessary Google Cloud Pub/Sub topics and subscriptions if they don't already exist. This is useful for initial setup.                | N/A                                                   |
| `local_origin_dir`  | **(Optional)** Provides an absolute path to a local directory containing git repositories. This is used for testing to simulate a remote origin without making actual network requests. | `/home/user/udmi_test_repos`                          |

### 2.3. Message Format and Triggering

The Reconciler Service is triggered by standard notifications from Google Cloud Source Repositories sent over Pub/Sub.

The service specifically filters for messages that indicate a `CREATE` event for any branch whose name begins with the prefix `proposal/`. When such a branch is detected, the service initiates the process to create a pull request against the `main` branch. All other repository events are ignored.

Here is a sample message:
```json
{
  "name": "projects/bos-platform-dev/repos/ZZ-TRI-FECTA-1",
  "url": "https://source.developers.google.com/p/bos-platform-dev/r/ZZ-TRI-FECTA-1",
  "eventTime": "2025-06-24T07:09:38.818511Z",
  "refUpdateEvent": {
    "email": "user@example.com",
    "refUpdates": {
      "refs/heads/proposal/1-Ne0K56ngkAsk7nKlVRAY2qI6Fm9gpj0zPwwp6pdnho/20250624.070925": {
        "refName": "refs/heads/proposal/1-Ne0K56ngkAsk7nKlVRAY2qI6Fm9gpj0zPwwp6pdnho/20250624.070925",
        "updateType": "CREATE",
        "newId": "36aaaf00bef44d301469e2dc9a8b757f05aad55e"
      }
    }
  }
}
```

---

### 3. Building and Deploying the Container

To package the Reconciler service into a Docker container for deployment, use the provided `container` script.

First, set your target GCP project and optional UDMI namespace:

```shell
bin/set_project gcp_project[/udmi_namespace]
```

Then, navigate to the UDMI root and use the container script with one of the following commands:
```shell
# General Syntax: bin/container services.reconciler {command} [repo]
bin/container services.reconciler push
```

| Command   | Action                                                                                                |
|-----------|-------------------------------------------------------------------------------------------------------|
| `prep`    | Prepares dependencies and prerequisites for the container build.                                      |
| `build`   | Builds the Docker container image locally.                                                            |
| `push`    | Pushes the built container image to a container registry (pushes to GHCR if `repo` is not specified). |
| `apply`   | Applies the container to GKE cluster                                                                  |
