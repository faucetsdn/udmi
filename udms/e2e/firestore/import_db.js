/**
 * @fileoverview Defines methods used to populate a firestore/emulato database
 * from a JSON object in a file
 */

const fs = require('fs');
const path = require('path');
const assert = require('assert').strict;

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

async function clearDB(db, db_collection) {
    const query = await db.collection(db_collection).get();
    await Promise.all(query.docs.map((doc) => {
        return doc.ref.delete();
    }));
}

async function importDB(json_data_source, db, db_collection) {
    const dbFile = fs.readFileSync(path.resolve(__dirname, json_data_source));
    const db_json = JSON.parse(dbFile);
    await importCollection(db, db_collection, db_json[db_collection]);
}

module.exports.importDB = importDB;
module.exports.clearDB = clearDB;

// clearDB(db_collection).then(importDB(json_data_source, db, db_collection));
