
const deviceTemplates = [
    { make: 'Cisco', models: ['Mediator'], name: 'cis' },
    { make: 'BitBox USA', models: ['BitBox'], name: 'bb-usa' },
    { make: 'Automated Logic', models: ['LGR', 'G5CE'], name: 'aut-log' },
    { make: 'Enlightened', models: ['Light Gateway'], name: 'enl' },
    { make: 'Tridium', models: ['JACE 8000'], name: 'tri' },
    { make: 'Delta Controls', models: ['Entelibus Manager 100', 'CopperCube'], name: 'dc' },
    { make: 'Acquisuite', models: ['Obvious AcquiSuite A88 12-1'], name: 'acq' },
    { make: 'Schneider Electric / APC', models: ['PowerLogic ION', 'AP9630', 'AP9631', 'AP9635'], name: 'apc' },
];

const sites = [
    { site: 'CA-US-M1', sections: ['LK', 'PK'] },
    { site: 'CA-US-M2', sections: ['AK', 'DK'] },
    { site: 'CA-US-M3', sections: ['JK', 'FK'] },
];

let n = 1;
while (n <= 10000) {
    const deviceTemplate = getRandom(deviceTemplates);
    const deviceModel = getRandom(deviceTemplate.models);
    const deviceSite = getRandom(sites);
    const deviceSection = getRandom(deviceSite.sections);

    const name = `${deviceTemplate.name}-${n}`;
    const make = `${deviceTemplate.make}`;
    const model = deviceModel;
    const site = deviceSite.site;
    const section = deviceSection;
    const lastPayload = new Date(new Date() - getRandomInt(1000000000)).toISOString();
    const operational = n % 3 == 0 ? false : true;

    db.device.insertOne({ "id": UUID(), name, make, model, site, section, lastPayload, operational, "tags": [] });
    n++;
}

function getRandom(array) {
    return array[getRandomInt(array.length)];
}

function getRandomInt(max) {
    return Math.floor(Math.random() * max);
}