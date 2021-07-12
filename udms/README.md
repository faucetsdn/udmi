# Udms

This project was generated with [Angular CLI](https://github.com/angular/angular-cli) version 11.0.6.


# Development

## Install
Run `npm install --include=dev` to install local dependencies. Follow [Firebase Doc](https://firebase.google.com/docs/cli) to install Firebase Cli.

## Firebase Emulator
Run `firebase emulators:start` in a separate shell. Emulator settings(e.g. Ports) can be configured in `firebase.json` if necessary. To populate the local firestore emulator with data, run `node e2e/firestore/import_db.js`. To view local emulators' statuses, visit `http://localhost:3000/`.

## Development server

Run `ng serve` for a dev server. Navigate to `http://localhost:4200/`. The app will automatically reload if you change any of the source files.

## Code scaffolding

Run `ng generate component component-name` to generate a new component. You can also use `ng generate directive|pipe|service|class|guard|interface|enum|module`.

## Build

Run `ng build` to build the project. The build artifacts will be stored in the `dist/` directory. Use the `--prod` flag for a production build.

## Lint

Run `ng lint`.

# Testing

## Running unit tests

Run `ng test` to execute the unit tests via [Karma](https://karma-runner.github.io).

## Running end-to-end tests

Run `ng e2e` to execute the end-to-end tests via [Protractor](http://www.protractortest.org/).

# Further help

To get more help on the Angular CLI use `ng help` or go check out the [Angular CLI Overview and Command Reference](https://angular.io/cli) page.
