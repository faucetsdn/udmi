exports.handleUDMIEvent = (event, context) => {
    console.log(getEventObject(event));
}

function getEventObject(event) {
    const stringData = Buffer.from(event.data, 'base64').toString();
    event.data = JSON.parse(stringData);
    return event;
}