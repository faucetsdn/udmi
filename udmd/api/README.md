# UDMI Device Management Console API

The project uses Typescript and depends on Node and npm already being installed on your machine. If you do not already have Node installed, install [Node](https://nodejs.org/en/download/) which comes with npm.

---

### Setup the Poject to be Run

1.  Run the build script

    ```
    ./build.sh
    ```
    Build.sh runs the install and sets up a template environment

    `npm install`  
    `cp .env.example .env` 
    
2.  Edit the values in .env as needed

### Running the Project

1.  Run the project, which will bind to port 4300;
    ```
    npm run dev
    ```
2.  Navigate to [http://localhost:4300/](http://localhost:4300/) to see the GraphQL interface. The app will automatically reload if you change any of the source files.


### Populate Data for the Project

**Assumption:** mongodb and mongosh are already installed.

1. Create a 'udmi' db with a 'device' collection by following the instructions here: [Creating a DB](https://www.mongodb.com/basics/create-database).  
2. Connect to the local mongo db using Mongo-Shell if not already connected
```
mongosh -host mongo-db udmi
```
3. Load the data using the following command:

```
load("/util/UDMI-devices.js");
```

### Notes
- Run `npm build` to build the project. The build artifacts will be stored in the `dist/` directory.
- Run `npm test` to execute the unit tests via [Jest](https://jestjs.io).
- Run `npm run testInteractive` to continuosly execute the unit tests via [Jest](https://jestjs.io).  The tests will be run every time a file is saved.
- Followed instructions here [Using with MongoDB](https://jestjs.io/docs/mongodb) and [jest-mongodb](https://github.com/shelfio/jest-mongodb) to configure and run in memory mongodb for testing
