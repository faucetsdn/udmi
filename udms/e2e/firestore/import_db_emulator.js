/**
 * @fileoverview Populates emulator database with json object picked from file
 * mentioned.
 */

process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099";
const firebase = require("firebase-admin");

const json_data_source = "db.json"
const db_collection = "origin"

const firebaseConfig = {
    // Any id will work as long as UI is also connecting using the same id
    // Default projects are specified in project root/.firebasrc
    projectId: "bos-daq-testing",
};
firebase.initializeApp(firebaseConfig);
const db = firebase.firestore();
const import_db = require('./import_db.js');

import_db.clearDB(db, db_collection).then(import_db.importDB(json_data_source, db, db_collection));
