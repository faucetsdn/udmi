# UDMI Demonstration Dashboard

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
