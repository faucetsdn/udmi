# UDMI Dashboard

The UDMI dashboard is a very simple dashboard that can be used to view and
browse device-specific data. Includes additional 


To make data persistent, it can be written to a back-end database, e.g. Firestore. See the `udmi_target` and
  `udmi_state` [example cloud functions](../dashboard/functions/index.js) for details.
* A similar function called `device_config` shows how PubSub can be used to update the Cloud IoT configuration.

## Using the UDMI Dashboard
One 

## Setup

There are three four, which are needed at different stages
of deployment/updates.

1. _Initial setup of GCP_  (for deploying a new GCP project)
2. _Initial setup of Firebase_ (for deploying the dashboard)
3. _Code deploy_ (to deploy a new or updated version of the code)
4. _Authentication_ (to authenticate any new users to the web page)


### Initial Setup

3. Enable GCP APP ENGINE on GCP
    * Go to [GCP AppEngine console](https://cloud.google.com/appengine)
    * Enable!

4. Create a Firebase project linked to the GCP account
   * Goto the [Firebase Console](https://console.firebase.google.com/) and click add a new project.
   * Click the text field to enter your project name. A prompt to "Add Firebase to one of your existing Google Cloud projects" will appear. Ensure you select your GCP project or enter it's name. This will be indicated by the hexagonal GCP logo
   * Chose an appropriate billing option. The _Blaze_ plan should be fine.
   * Disable Google Analytics, unless you also want to setup an account for that.
   * Continue on to add Firebase to your GCP project.

5. Enable a native mode database
   * https://console.firebase.google.com/
   * Select your project.
   * Select `Firestore Database` from the options on the left.
   * Select to start the database in production mode.
   * Select a location for the database - this should be the same geography as your GCP IoT Core Registry

6. Add a web-app to the Firebase project
   * https://console.firebase.google.com/
   * Select your project.
   * Select "+ Add app" (or this may be auto-selected for you).
   * Select "</>" (Web) to add a new web-app.
   * Use a suitable nickname and register app. 
   * Ensure _Firebase Hosting_ is enabled by ticking the checkbox to "Also set up Firebase Hosting for this app." 
   * Skip the part about adding the Firebase SDK.
   * Follow the instructions for installing the Firebase CLI. (refer to step below for additional information)
   * Ignore the bit about "Deploy to Firebase Hosting" for now.

7. Install the Firebase CLI on your local machine
   * Follow the instructions 
   https://firebase.google.com/docs/cli/?authuser=0#install-cli-mac-linux
   * Make sure `npm` is installed.

8. Get your [firebase config object](https://support.google.com/firebase/answer/7015592?authuser=0) for a _web app_.
   * Copy the `const firebaseConfig = { ... }` snippet to `dashboard/public/firebase_config.js`

9.. Add an API key restriction:
   * Go to [API Key Restriction](https://console.cloud.google.com/apis/credentials)
   * There should be an _API Keys_ with the name  _Browser key (auto created by Firebase)_
   * Edit this, and add an _HTTP Referrer_ configured for any URL within your web app address using a wildcard asterisk (*), for example  `https://your-project-name.web.app/*`
   * Your Web App address is given when enabling Firebase Web Hosting, and can be retrieved


### Code Deploy
1. Install Firebase function dependencies.
   * Make sure `npm` is installed.
   * `cd` to the `dashboard/functions` directory and run `npm install`.

3. In `dashboard/`, run `deploy_dashboard` to deploy to your project.
   * Follow the link to the indicated _Hosting URL_ to see the newly installed pages.
   * You will likely see an _Authentication failed_ message. See next section for details.


### Authentication
10. Enable Google sign-in for your Firebase We App
   * https://console.firebase.google.com/
   * Select your project.
   * Select "Authentication"
   * Select "Sign-in method"
   * Enable "Google" sign-in.


 Have intended user access page and fail auth
 
* Enable user in Firestore create a new collection under intended user id
  * CollectionID: iam
  * Document ID: default
  * Field: enabled (boolean) true
* Setup registry and topics


### Installing Alongside Other Webapps (e.g. DAQ Dashboard)
You will need to update the target of the webapp


Steps to get a new dashboard up and going (rough draft):

* Create Firebase Project
* One of the two things to enable cloud functions for Firebase deploy
  * Wait for updates to propagate after previous step (most likely)
  * or start to create a Cloud Function to enable from GCP console (not likely)
* Firestore Database in Native Mode fr om GCP Console
* Deploy UDMI Dashboard from dashboard utility
* Enable Firebase Google Authentication from firebase console
*

