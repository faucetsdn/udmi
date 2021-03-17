process.env.FIRESTORE_EMULATOR_HOST = "localhost:8080";
process.env.FIREBASE_AUTH_EMULATOR_HOST = "localhost:9099";
const firebase = require("firebase-admin");
const fs = require('fs');
const path = require('path');
const assert = require('assert').strict;

const firebaseConfig = {
    projectId: "daq-haoli", // Any id will work as long as UI is also connecting using the same id
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
    const query = await db.collection("origin").get();
    await Promise.all(query.docs.map((doc) => {
        return doc.ref.delete();
    }));
}

async function importDB() {
    const dbFile = fs.readFileSync(path.resolve(__dirname, "db.json"));
    const db_json = JSON.parse(dbFile);
    await importCollection(db, "origin", db_json.origin);
}

clearDB().then(importDB);
