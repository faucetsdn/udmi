# UDMI Device Management Console UI

The project uses [Angular](https://angular.io/cli) and it's CLI. The CLI depends on Node and npm already being installed on your machine. If you do not already have Node installed, install [Node](https://nodejs.org/en/download/) which comes with npm.

---

### Config
Copy the `env.template.js` file in the `/src` folder and rename the copy to `env.js`. Populate with the appropriate values obtained from a teammate.

### Running the Project

1.  Optional: if you just want to spin up the app, skip this step. If you plan to use some of the Angular CLI commands but don't have the Angular CLI installed yet, install it globally on your machine;
    ```
    npm install -g @angular/cli`
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

### Notes
-  Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`. (requires Step 1 to have been done)
- Run `npm build` to build the project. The build artifacts will be stored in the `dist/` directory.
- Run `npm test` to execute the unit tests via [Karma](https://karma-runner.github.io).
- Run `npm e2e` to execute the end-to-end tests via a platform of your choice. To use this command, you need to first add a package that implements end-to-end testing capabilities.
- This project was generated with Angular CLI version 13.2.5.