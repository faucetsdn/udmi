# UDMI Device Management Console UI

The project uses [Angular](https://angular.io/cli) and it's CLI. The CLI depends on Node and npm already being installed on your machine. If you do not already have Node installed, install [Node](https://nodejs.org/en/download/) which comes with npm.


## Prerequisite
---

A Google account that can login to web application at https://web.staging.udmi.buildingsiot.com/login.  This will be used to retrieve an idToken.

## Config
---
Copy the `env.template.js` file in the `/src` folder and rename the copy to `env.js`. Populate with the appropriate values from the [notebook](https://appriver3651002181.sharepoint.com/:o:/r/sites/BuildingsIOTRD/SiteAssets/Buildings%20IOT%20R%26D%20Notebook?d=wdddf0b145b9e4e319750499fbb8c7864&csf=1&web=1&e=NKrbDB).

```
cp src/env.template.js src/.env.js
```

## Running the Project
---

1.  Optional: if you just want to spin up the app, skip this step. If you plan to use some of the Angular CLI commands but don't have the Angular CLI installed yet, install it globally on your machine;
    ```
    npm install -g @angular/cli
    ```
2.  Install the project dependencies;
    ```
    npm install
    ```
3.  Run the project, which will bind to port 4200;
    ```
    npm start
    ```
4.  Navigate to [http://localhost:4200/](http://localhost:4200/) to see the app. The app will automatically reload if you change any of the source files.

## Adding new envs
---
When adding a new env variable, it will need to be added in a few places.
- `env.template.js`, this is the config file which GitLab will use to inject it's private values during the build. These variables must be added to the CI.
- `env.js`, this is your personal config file, this file is not comitted to the repo and is the one used during development. It is used in the `index.html` file.
- `env.service.ts`, this is the service which exposes the env variables for use in Angular. They should be initialized in here with some default value, and will be overridden by the ones set by `env.js`.
- `docker-entrypoint.sh`, this is the file which invokes the replacement of the GitLab CI variables into the `env.template.js` file. It outputs the `env.js` file.

## Notes
---
- Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`. (requires Step 1 to have been done)
- Run `npm build` to build the project. The build artifacts will be stored in the `dist/` directory.
- Run `npm test` to execute the unit tests via [Karma](https://karma-runner.github.io).
- Run `npm e2e` to execute the end-to-end tests via a platform of your choice. To use this command, you need to first add a package that implements end-to-end testing capabilities.
- This project was generated with Angular CLI version 13.2.5.