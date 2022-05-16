# UDMI Event Handler

The Typescript project uses Google Cloud Functions Infrastructure to allow faster iteration of a GCP Node Cloud function.

---

## Setup the Poject

1.  Run the build script

```
npm install
```  

---

## Run the Simple Cloud Function Server

1.  Run the project, which will bind to port 8080;

```
npm run watch
```

---

## Submit a Pub Sub style Event

1. Send an event to the local server using curl

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

## Deploying to GCP

TBD

---

## Notes
-- Basic Instructions taken from [GCP Cloud Platform (Node)](https://github.com/GoogleCloudPlatform/functions-framework-nodejs)
-- The data inside the json is expected to be base64 encoded