// Point json_data_source to the json file with data to be populated into the DB
// Point db_collection to the collection name/topmost field of json object being
// populated.
// If script fails with permission issues, run:
// 'gcloud auth application-default login'

const firebase = require("firebase-admin");
require("firebase/firestore");
const fs = require('fs');
const path = require('path');
const assert = require('assert').strict;

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

function isDoc(data) {
    return !!data._id;
}

function isCollection(data) {
    return data instanceof Array;
}

function isTimestamp(data) {
    return typeof data === 'number' && data > 1000000000;
}

function isMap(data) {
    return Object.prototype.toString.call(data) === "[object Object]";
}

function formatMap(doc) {
    let formattedMap = {};
    Object.keys(doc).forEach((key) => {
        let value = doc[key];
        if (isTimestamp(value)) {
            formattedMap[key] = new Date(value);
        } else if (isMap(value)){
            formattedMap[key] = formatMap(value);
        } else {
            formattedMap[key] = value;
        }
    });
    return formattedMap;
}

async function importDoc(ref, doc) {
    assert.ok(isDoc(doc));
    const formattedDoc = {};
    const collections = [];
    Object.keys(doc).forEach((key) => {
        let value = doc[key];
        if (isTimestamp(value)) {
            formattedDoc[key] = new Date(value);
        } else if (isCollection(value)) {
            collections.push(key);
        } else if (isMap(value)) {
            formattedDoc[key] = formatMap(value)
        } else {
            formattedDoc[key] = value;
        }
    });
    await ref.doc(doc._id).set(formattedDoc);
    await Promise.all(collections.map((name) => {
        return importCollection(ref.doc(doc._id), name, doc[name]);
    }));
}

async function importCollection(ref, name, data) {
    const collection = ref.collection(name);
    data.forEach(async (doc) => {
        await importDoc(collection, doc);
    });
}

async function clearDB() {
    const query = await db.collection(db_collection).get();
    await Promise.all(query.docs.map((doc) => {
        return doc.ref.delete();
    }));
}

async function importDB() {
    const dbFile = fs.readFileSync(path.resolve(__dirname, json_data_source));
    const db_json = JSON.parse(dbFile);
    await importCollection(db, db_collection, db_json[db_collection]);
}

importDB();
