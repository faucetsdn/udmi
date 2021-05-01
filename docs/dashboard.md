# UDMI Dashboard

The UDMI dashboard is a very simple dashboard that can be used to view and
browse device-specific data. Installation of the dashboard 

There's three parts, which are needed at different stages
of deployment/updates.

1. _Initial setup_ (for deploying a new GCP project)
2. _Code deploy_ (to deploy a new or updated version of the code)
3. _Authentication_ (to authenticate any new users to the web page)

## Software Pre-requisites
Through out the tutorial, you will be guided through installing
* `JDK v11`
* `NPM & Node JS`
* `coreutils`
* `jq`
* `Firebase CLI` https://firebase.google.com/docs/cli/
* `Google Cloud SDK` 


## Initial Setup

1. Create a GCP project that you can to host the system, or acquire a new one if necessary.
   * 
   * Ensure billing has been enabled https://cloud.google.com/billing/docs/how-to/modify-project
2. Create a GCP IoT Core
   * Refer to https://cloud.google.com/iot/docs/how-tos/getting-started for additional information
   * Search for IoT Core
   * Click `Enable`

2. Create a Firebase project linked to the GCP account
   * Goto the [Firebase Console](https://console.firebase.google.com/) and click add a new project.
   * Click the text field to enter your project name. A prompt to "Add Firebase to one of your existing Google Cloud projects" will appear. Ensure you select your GCP project or enter it's name. This will be indicated by the hexagonal GCP logo
   * Chose an appropriate billing option. The _Blaze_ plan should be fine.
   * Disable Google Analytics, unless you also want to setup an account for that.
   * Continue on to add Firebase to your GCP project.

1. Enable a native mode database
   * https://console.firebase.google.com/
   * Select your project.
   * Select `Firestore Database` from the options on the left.
   * Select to start the database in production mode.
   * Select a location for the database - this should be the same geography as your GCP IoT Core Registry

2. Add a web-app to the Firebase project
   * https://console.firebase.google.com/
   * Select your project.
   * Select "+ Add app" (or this may be auto-selected for you).
   * Select "</>" (Web) to add a new web-app.
   * Use a clever nickname and register app. Not sure if/how/when this matters.
   * Ensure _Firebase Hosting_ is enabled by ticking the checkbox to "Also set up Firebase Hosting for this app." 
   * Skip the part about adding the Firebase SDK.
   * Follow the instructions for installing the Firebase CLI. (also next step)
   * Ignore the bit about "Deploy to Firebase Hosting" for now.

3. Install the Firebase CLI if you have not done so already
   * Follow the instructions 
   https://firebase.google.com/docs/cli/?authuser=0#install-cli-mac-linux


3. Get your [firebase config object](https://support.google.com/firebase/answer/7015592?authuser=0) for a _web app_.
   * Copy the `const firebaseConfig = { ... }` snippet to `local/firebase_config.js`
4. Add an API key restriction:
   * Go to [API Key Restriction](https://cloud.google.com/docs/authentication/api-keys#api_key_restrictions)
   * There should be an _API Keys_ as a _Browser key (auto creatd by Firebase)_
   * Edit this, and add an _HTTP Referrer_, which will be the https:// address of the daq hosted web app: `https://your-project-name.firebaseapp.com/*`
5. Enable Google sign-in from
   * https://console.firebase.google.com/
   * Select your project.
   * Select "Authentication"
   * Select "Sign-in method"
   * Enable "Google" sign-in.

## Code Deploy
1. Install Firebase function dependencies.
   * Make sure `npm` is installed.
   * `cd` to the <code>firebase/functions</code> directory and run <code>npm install</code>.
2. Enable appengine on GCP
    * Go to [GCP AppEngine console](https://cloud.google.com/appengine)
    * Enable!
3. In <code>firebase/</code>, run <code>./deploy.sh</code> to deploy to your project.
   * Follow the link to the indicated _Hosting URL_ to see the newly installed pages.
   * You will likely see an _Authentication failed_ message. See next section for details.



## Installing Alongside Other Apps (e.g. DAQ Dashboard)
You will need to update the target

Steps to get a new dashboard up and going (rough draft):

* Create Firebase Project
* One of the two things to enable cloud functions for Firebase deploy
  * Wait for updates to propagate after previous step (most likely)
  * or start to create a Cloud Function to enable from GCP console (not likely)
* Firestore Database in Native Mode from GCP Console
* Deploy UDMI Dashboard from dashboard utility
* Enable Firebase Google Authentication from firebase console
* Have intended user access page and fail auth
* Enable user in Firestore create a new collection under intended user id
  * CollectionID: iam
  * Document ID: default
  * Field: enabled (boolean) true
* Setup registry and topics

