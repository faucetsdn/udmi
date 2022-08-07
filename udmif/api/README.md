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
    
2.  If you want to point your API to a MongoDB, you can edit the values in .env as needed

3.  Update the AUTH_CLIENT_ID and CLIENT_IDS with the values for Google identity provider.  These values should be provided by whomever manages your Auth Provider.

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

1. mongodb is installed and running
2. udmi db has been created in mongo

Install the required mongodb tools
```
brew tap mongodb/brew
brew install mongodb-database-tools
```

Execute the data import
```
mongoimport --uri="mongodb://127.0.0.1:27017/udmi" -c=device --file=util/devices.json --mode=upsert --jsonArray
```

## Convert runtime to use MongoDB
---

By using Mongo as the DB, the data will not be static or hard coded and can be changed without needing to restart the service or changing code.  Switching to Mongo entails:

1. Change the DATASOURCE field to 'MONGO' in the .env file.

## Testing
---

### Automated Tests

Followed instructions at [Using with MongoDB](https://jestjs.io/docs/mongodb) and [jest-mongodb](https://github.com/shelfio/jest-mongodb) to configure and run in memory mongodb for testing

- Run `npm test` to execute the unit tests via [Jest](https://jestjs.io).
- Run `npm run testInteractive` to continuosly execute the unit tests via [Jest](https://jestjs.io).  The tests will be run every time a src file is saved.

### Manual Testing

You can send GraphQL requests to [http://localhost:4300/](http://localhost:4300/).  In order to send a GraphQL request, you will need to add the idToken collected in the Prerequisites as a header to the requests called 'idToken' 

## Notes
---
- Run `npm build` to build the project. The build artifacts will be stored in the `dist/` directory.
- Creating a MongoDB - Create a 'udmi' db with a 'device' collection by following the instructions here: [Creating a DB](https://www.mongodb.com/basics/create-database).  

## Common Problems and Solutions
---
| Problem | Solution | 
| --- | --- |
| The API does not return data. | Fix - Double check that the client id's are filled in the .env file |