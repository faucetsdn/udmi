const mongodb = require('mongodb');

let client;

/**
 * Triggered from a message on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 * @param {!Object} context Metadata for the event.
 */
exports.handleUDMIEvent = async (event, context) => {

    const eventObject = getEventObject(event);

    if (event.attributes.subFolder === "pointset" || event.attributes.subFolder === "system") {
        console.log(eventObject);
        console.log(JSON.stringify(prepareDeviceDocument(eventObject)));
        // await initMongoClient();
        if (client) {
            // const deviceDocument = await client.db(process.env.MONGO_DB).collection('device').findOne({ name: 'cis-20', site: 'CA-US-M1' });
        }
    }
};

async function initMongoClient() {
    try {
        if (client) {
            return;
        }

        console.log(`Initializing a new Mongo Client: ${process.env.MONGO_PROTOCOL}://<user>:<pwd>@${process.env.MONGO_HOST}`);

        // setup connection details
        const uri = `${process.env.MONGO_PROTOCOL}://${process.env.MONGO_USER}:${process.env.MONGO_PWD}@${process.env.MONGO_HOST}`;
        client = await mongodb.MongoClient.connect(uri, { useNewUrlParser: true });
    } catch (e) {
        console.error('Failed to create and connect client', e);
    }
}

function getEventObject(event) {
    const stringData = Buffer.from(event.data, 'base64').toString();
    event.data = JSON.parse(stringData);
    return event;
}

function prepareDeviceDocument(event) {
    const deviceDocument = {};
    deviceDocument.name = event.attributes.deviceId;
    deviceDocument.site = event.attributes.deviceRegistryId;
    return deviceDocument;
}