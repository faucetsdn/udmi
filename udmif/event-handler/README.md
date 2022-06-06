# UDMI Event Handler

The Typescript project uses Google Cloud Functions Infrastructure to allow faster iteration of a GCP Node Cloud function.

---

## Datasource

In order to properly run the cloud function, it requires MongoDB to be up and running.  You will need to configure the connection parameters in your buildDev.sh file if they are different from a standard install.

---

## Install dependencies and transpile code into JS.

Installs all the npm dependencies and transpiles code into JS

```
./buildDev.sh
```

## Run the Simple Cloud Function Server

Run the project, which will bind to port 8080;

```
./runDev.sh
```

---

## Submit a Pub Sub style Event

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

---

## Testing the code

```
npm run test
```

or

```
npm run testInteractive
```

---


## Notes

* Basic Instructions taken from [GCP Cloud Platform (Node)](https://github.com/GoogleCloudPlatform/functions-framework-nodejs) \
* The data inside the json is expected to be base64 encoded, to decode it (on macos) copy the base64 string and execute the following command in your terminal:
   ```
   pbpaste | base64 --decode && echo -e
   ```
