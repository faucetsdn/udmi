const { v4: uuid } = require('uuid');

const deviceTemplates = [
    { make: 'Cisco', models: ['Mediator'], name: 'cis', firmwareVersion: ['v1.2'] },
    { make: 'BitBox USA', models: ['BitBox'], name: 'bb-usa', firmwareVersion: ['v2.3'] },
    { make: 'Automated Logic', models: ['LGR', 'G5CE'], name: 'aut-log', firmwareVersion: ['drv_melgr_vanilla_6-02-034', 'drv_melgr_vaisala_6-00a-001', 'drv_melgr_mb_combo_6-00-01'] },
    { make: 'Enlightened', models: ['Light Gateway'], name: 'enl', firmwareVersion: ['drv_fwex_101-00-2051'] },
    { make: 'Tridium', models: ['JACE 8000'], name: 'tri', firmwareVersion: ['4.2.36.36'] },
    { make: 'Delta Controls', models: ['CopperCube'], name: 'dc', firmwareVersion: ['535847'] },
    { make: 'Delta Controls', models: ['Entelibus Manager 100'], name: 'dc', firmwareVersion: ['4.3.7.64'] },
    { make: 'Acquisuite', models: ['Obvious AcquiSuite A88 12-1'], name: 'acq', firmwareVersion: ['v3.4'] },
    { make: 'Schneider Electric / APC', models: ['PowerLogic ION', 'AP9630', 'AP9631', 'AP9635'], name: 'apc', firmwareVersion: ['v4.5'] },
];

const sites = [
    { site: 'CA-US-M1', sections: ['LK', 'PK'] },
    { site: 'CA-US-M2', sections: ['AK', 'DK'] },
    { site: 'CA-US-M3', sections: ['JK', 'FK'] },
];


const pointTemplates = [
    { id: "ZT-1", name: "Zone Temperature", value: "78.12", units: "℉" },
    { id: "FSP-1", name: "Fan Speed Command", value: "true", units: "" },
    { id: "IAF-1", name: "Inlet Air Flow", value: "15", units: "CFM" },
    { id: "DAT-1", name: "Discharge Air Temperature", value: "67.8", units: "℉" },
    { id: "CO2", name: "CO2", value: "0.00034", units: "PPM" },
];

const pointStates = ['Applied', 'Updating', 'Overriden', 'Invalid', 'Failure'];


let n = 1;
while (n <= 10000) {
    const deviceTemplate = getRandom(deviceTemplates);
    const deviceModel = getRandom(deviceTemplate.models);
    const deviceSite = getRandom(sites);

    const id = uuid();
    const name = `${deviceTemplate.name}-${n}`;
    const make = `${deviceTemplate.make}`;
    const model = deviceModel;
    const site = deviceSite.site;
    const lastPayload = new Date(new Date() - getRandomInt(1000000000)).toISOString();
    const operational = n % 3 == 0 ? false : true;
    const section = getRandom(deviceSite.sections);
    const serialNumber = generateSerial();
    const tags = [];
    const firmware = getRandom(deviceTemplate.firmwareVersion);
    const points = getPoints(getRandomNumberBetween(1, pointTemplates.length));

    db.device.insertOne({ id, name, make, model, site, section, lastPayload, operational, serialNumber, firmware, tags, points });
    n++;
}

function getPoints(count) {
    return [...Array(count)].map((_, i) => {
        return { ...pointTemplates[i], state: pointStates[i] };
    });
}

function getRandom(array) {
    return array[getRandomInt(array.length)];
}

function getRandomInt(max) {
    return Math.floor(Math.random() * max);
}

function getRandomNumberBetween(min, max) {
    return Math.floor(Math.random() * max - 1) + min;
}

function generateSerial() {
    var chars = '1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ',
        serialLength = 10,
        randomSerial = "";

    [...Array(serialLength)].forEach(() => {
        const randomNumber = getRandomInt(chars.length);
        randomSerial += chars.substring(randomNumber, randomNumber + 1);
    });

    return randomSerial;

}