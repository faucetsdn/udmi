[**UDMI**](../../) / [**UDMIF**](../) / [**API**](../) / [Readme](#)

# UDMI Device Management Console API

The project uses Typescript and depends on Node and npm already being installed on your machine. If you do not already have Node installed, install [Node](https://nodejs.org/en/download/) which comes with npm.

## Prerequisites
---

A Google account that can login to web application at https://web.staging.udmi.buildingsiot.com/login.  This will be used to retrieve an idToken.  In order to get the token, you will need to copy the idToken headers for a request to the api service in the browser developer tools.  The idToken is needed in order to make GraphQL requests to the api service.

## Build the Project
---

1.  Run the build script which installs dependencies and sets up a environment file from an example env file

    ```
    ./buildDev.sh
    ```
    
2.  Update the AUTH_CLIENT_ID and CLIENT_IDS with the values for Google identity provider.  These values should be provided by whomever manages your Auth Provider.

## Run the Project
---

1.  Start the project, which will bind to port 4300;
    ```
    ./runDev.sh
    ```
2.  Navigate to [http://localhost:4300/](http://localhost:4300/) to see the GraphQL interface. The app will automatically reload if you change any of the source files.


## Populate Data for the Project - MacOS specific
---

**Assumptions:** 

1. PostgreSQL is installed and running
2. udmi db has been created in PostgreSQL

## Testing
---

### Automated Tests

- Run `./runTests.sh` (could also run `npm test` if all node modules have already been installed) to execute the unit tests via [Jest](https://jestjs.io).
- Run `npm run testInteractive` to continuously execute the unit tests via [Jest](https://jestjs.io).  The tests will be run every time a src file is saved.

### Manual Testing

You can send GraphQL requests to [http://localhost:4300/](http://localhost:4300/).  In order to send a GraphQL request, you will need to add the idToken collected in the Prerequisites as a header to the requests called 'idToken' 

## Notes
---
- Run `npm build` to build the project. The build artifacts will be stored in the `dist/` directory.

## Common Problems and Solutions
---
| Problem | Solution | 
| --- | --- |
| The API does not return data. | Fix - Double check that the client id's are filled in the .env file |


