# UDMI Device Management Console Dashboard

The project uses an Angular Front End and a GraphQL API Service to get data and present it.

---

### Running the Project

1.  In order to be able to run both sub projects in parallel, you will need to install npm-run-all
    ```
    npm install -g npm-run-all`
    ```

2.  Build the sub projects
    ```
    npm-run-all buill-all
    ```
3.  Run the sub projects in parallel, UI will bind to port 4200 and API will bind to port 4300
    ```
    npm-run-all start-all
    ```
4.  Navigate to [http://localhost:4200/](http://localhost:4200/) to see the app. The app will automatically reload if you change any of the source files.