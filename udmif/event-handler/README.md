[**UDMI**](../../) / [**UDMIF**](../) / [**Event Handler**](./) [README](#)

# UDMI Event Handler

This project is a GCP Cloud function written in Typescript that uses Google Cloud Functions Infrastructure to allow faster development.

## MongoDB
---

In order to properly run the cloud function, it requires MongoDB to be up and running.  Following the instruction linked below.

[Installing MongoDB](https://www.mongodb.com/docs/manual/administration/install-community/)

You will need to configure the connection parameters in your runDev.sh file if they are different from a standard install.

## PostgreSQL
---

In order to run this cloud function, a connection to a PostgreSQL DB is required.  Please follow these instructions for installing PostgreSQL locally.

[PostgreSQL Download and Instructions](https://www.postgresql.org/download/)

The shell script `runMigration.sh` runs a DB schema migration against the local PostgreSQL instance.

In production, the DB schema migration will be handled by the API service found in the sibling `udmif/api` folder to `udmif/event-handler`.

## Build and Run
---

### Install dependencies and transpile code into JS.

Installs all the npm dependencies and transpiles code into JS

```
./buildDev.sh
```

### Run the Simple Cloud Function Server

Run the project, which will bind to port 8080;

```
./runDev.sh
```

## Submit a Pub Sub style Event
---

Send an event to the local server using curl.  Example events can be found in the sample_messages folder.

```
curl -d "@sample.json" \
  -X POST \
  -H "Ce-Type: true" \
  -H "Ce-Specversion: true" \
  -H "Ce-Source: true" \
  -H "Ce-Id: true" \
  -H "Content-Type: application/json" \
  http://localhost:8080
```

There are canned curl messages in the sample_messages/canned-curl-messages.txt file.

## Unit Tests
---

To run the unit tests once, issue the following command:

```
npm run test
```

or to run the tests automatically with every code change, issue the following command:

```
npm run testInteractive
```
## Notes
---

* Basic Instructions taken from [GCP Cloud Platform (Node)](https://github.com/GoogleCloudPlatform/functions-framework-nodejs) \
* The data inside the json is expected to be base64 encoded, to decode it (on macos) copy the base64 string and execute the following command in your terminal:

   ```
   pbpaste | base64 --decode && echo -e
   ```
