# UDMI Dashboard

The UDMI dashboard is a very simple dashboard that can be used to view and
browse device-specific data. Deploying the dashboard will deploy additional cloud functions, including:
* Functions to show how data can be made more persistent, by  being written to a back-end database, e.g. Firestore. See the `udmi_target` and
  `udmi_state` [example cloud functions](../dashboard/functions/index.js) for details.
* A function called `device_config` shows how PubSub can be used to update the Cloud IoT configuration.

## Seting Up the UDMI Dashboard

There are three four, which are needed at different stages
of deployment/updates.
1. _Initial setup of Firebase_ (for deploying the dashboard)
2. _Code deploy_ (to deploy a new or updated version of the code)
3. _Authentication_ (to authenticate any new users to the web page)

### Initial Setup

1. Ensure you have 
2. Create a Firebase project linked to the GCP account
   * Goto the [Firebase Console](https://console.firebase.google.com/) and click add a new project.
   * Click the text field to enter your project name. A prompt to "Add Firebase to one of your existing Google Cloud projects" will appear. Ensure you select your GCP project or enter it's name. This will be indicated by the hexagonal GCP logo
   * Chose an appropriate billing option. The _Blaze_ plan should be fine.
   * Disable Google Analytics, unless you also want to setup an account for that.
   * Continue on to add Firebase to your GCP project.
3. Enable a native mode database
   * https://console.firebase.google.com/
   * Select your project.
   * Select `Firestore Database` from the options on the left.
   * Select to start the database in production mode.
   * Select a location for the database - this should be the same geography as your GCP IoT Core Registry
4. Add a web-app to the Firebase project
   * https://console.firebase.google.com/
   * Select your project.
   * Select "+ Add app" (or this may be auto-selected for you).
   * Select "</>" (Web) to add a new web-app.
   * Use a suitable nickname and register app. 
   * Ensure _Firebase Hosting_ is enabled by ticking the checkbox to "Also set up Firebase Hosting for this app." 
   * Skip the part about adding the Firebase SDK.
   * Follow the instructions for installing the Firebase CLI. (refer to step below for additional information)
   * Ignore the bit about "Deploy to Firebase Hosting" for now.
5. Install the Firebase CLI on your local machine
   * Follow the instructions 
   https://firebase.google.com/docs/cli/?authuser=0#install-cli-mac-linux
   * Make sure `npm` is installed.
6. Get your [firebase config object](https://support.google.com/firebase/answer/7015592?authuser=0) for a _web app_.
   * Click on Project Settings 
   * Scroll down to "Your Apps"
   * Under _Firebase SDK Snippet_ select _Config_
   * Copy the `const firebaseConfig = { ... }` snippet to `dashboard/public/firebase_config.js`
7. Add an API key restriction:
   * Go to [API Key Restriction](https://console.cloud.google.com/apis/credentials)
   * There should be an _API Keys_ with the name  _Browser key (auto created by Firebase)_
   * Edit this, and add an _HTTP Referrer_ configured for any URL within your web app address using a wildcard asterisk (*), for example  `https://your-project-name.web.app/*`
   * Your Web App address is given when enabling Firebase Web Hosting, and can be retrieved
8. Enable Google sign-in for your Firebase We App
   * https://console.firebase.google.com/
   * Select your project.
   * Select "Authentication"
   * Select "Sign-in method"
   * Enable "Google" sign-in.

### Code Deploy
1. Install Firebase function dependencies.
   * Make sure `npm` is installed.
   * `cd` to the `dashboard/functions` directory and run `npm install`.

2. `cd` to the `dashboard` directory, run `deploy_dashboard <project id>` where project ID is the firebase project ID to deploy to your project.
   * Follow the link to the indicated _Hosting URL_ to see the newly installed pages.
   * You will likely see an _Authentication failed_ message. See next section for details.

### Authentication

1. Have intended user access the page page and login using a Google Account (the authentication will fail at this stage)

2. From the Firebase Console, click on _Authentication_ in the side bar, and identify the `User UID` of the intended account

3. Enable access for the user within the Dashboard application
   * From the Firebase Console, click on _Firestore Database_. 
   * Click on the users collection
   * Click document which matches the `User UID` you are granting access for
   * Add a new Collection under this user
      * CollectionID: `iam`
      * Document ID: `default`
      * Field `enabled` = (boolean) `true`
