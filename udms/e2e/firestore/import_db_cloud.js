/**
 * @fileoverview Populates firestore db with JSON object in a given file
 */

// Point json_data_source to the json file with data to be populated into the DB
// Point db_collection to the collection name/topmost field of json object being
// populated.
// If script fails with permission issues, run:
// 'gcloud auth application-default login'

const firebase = require("firebase-admin");

const json_data_source = "db.json"
const db_collection = "origin"

const firebaseConfig = {
    // Any id will work as long as UI is also connecting using the same id
    // Default projects are specified in project root/.firebasrc
    projectId: "bos-daq-testing",
    apiKey: 'AIzaSyANL8VhKgHhD71lMNw6MUa_CRlpQs6Z0Pc',
    authDomain: 'bos-daq-testing.firebaseapp.com',
};
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();

const import_db = require('./import_db.js');

import_db.importDB(json_data_source, db, db_collection);
