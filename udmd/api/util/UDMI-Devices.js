
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
    { site: 'site1', sections: ['section-a', 'section-b'] },
    { site: 'site2', sections: ['section-c', 'section-d'] },
    { site: 'site3', sections: ['section-e', 'section-f'] },
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

    db.device.insertOne({ "id": n, name, make, model, site, section, lastPayload, operational, "tags": [] });
    n++;
}

function getRandom(array) {
    return array[getRandomInt(array.length)];
}

function getRandomInt(max) {
    return Math.floor(Math.random() * max);
}