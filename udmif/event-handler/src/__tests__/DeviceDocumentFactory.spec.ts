import { createDeviceDocument } from '../DeviceDocumentFactory';
import { Device } from '../model/Device';
import {
  CONFIG,
  EVENT,
  MODEL,
  POINTSET_SUB_FOLDER,
  STATE,
  SYSTEM_SUB_FOLDER,
  VALIDATION_SUB_FOLDER,
} from '../MessageUtils';
import { UdmiMessage } from '../model/UdmiMessage';
import { Point } from '../model/Point';
import { Validation } from '../model/Validation';

const name: string = 'name';
const site: string = 'site-1';
const BASIC_SYSTEM_ATTRIBUTES = { deviceId: name, deviceRegistryId: site, subFolder: SYSTEM_SUB_FOLDER };
const BASIC_POINTSET_ATTRIBUTES = { deviceId: name, deviceRegistryId: site, subFolder: POINTSET_SUB_FOLDER };

describe('DeviceDocumentFactory.createDeviceDocument.default', () => {
  const tags: string[] = [];

  test('creates a default device document', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: {} };
    const expectedDeviceDocument: Device = { name, site, tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });

  test('creates a default device document with a timestamp', () => {
    const timestamp: string = '2022-04-25T17:06:12.454Z';
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: { timestamp } };
    const expectedDeviceDocument: Device = { name, site, lastPayload: timestamp, tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.createDeviceDocument.system', () => {
  const tags: string[] = [];

  test('creates a device document with system state', () => {
    const make: string = 'make-a';
    const model: string = 'model-a';
    const operational: string = 'true';
    const serialNumber: string = 'serial-no';
    const firmware: string = 'v1';

    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: STATE },
      data: {
        software: { firmware },
        hardware: { make, model },
        operational,
        serial_no: serialNumber,
      },
    };
    const expectedDeviceDocument: Device = { name, site, make, model, operational, serialNumber, firmware, tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with system model', () => {
    const section: string = 'section-a';

    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: MODEL },
      data: {
        location: { section, site },
      },
    };
    const expectedDeviceDocument: Device = { name, section, site, tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.createDeviceDocument.pointset', () => {
  const NO_UNITS = 'No-units';
  const faps = 'filter_alarm_pressure_status';
  const fdpsp = 'filter_differential_pressure_setpoint';
  const fdps = 'filter_differential_pressure_sensor';

  let existingPoints: Point[];
  const state: string = '';

  beforeEach(() => {
    existingPoints = [];
  });

  test('creates a device document with pointset', () => {
    // arrange
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp: '2022-04-25T17:00:26Z',
        points: {
          [faps]: { present_value: 78 },
          [fdpsp]: { present_value: 71 },
          [fdps]: { present_value: 82 },
        },
      },
    };

    const expectedPoints: Point[] = [
      { name: faps, id: faps, value: '78', meta: { code: faps }, state },
      { name: fdpsp, id: fdpsp, value: '71', meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, value: '82', meta: { code: fdps }, state },
    ];

    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with pointset model', () => {
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: MODEL },
      data: {
        points: {
          [faps]: {
            units: NO_UNITS,
            ref: 'BV11.present_value',
          },
          [fdpsp]: {
            units: 'Bars',
          },
          [fdps]: {
            units: 'Degrees-Celsius',
            ref: 'AV12.present_value',
          },
        },
      },
    };

    const expectedPoints: Point[] = [
      { name: faps, id: faps, units: NO_UNITS, meta: { code: faps, units: NO_UNITS }, state },
      { name: fdpsp, id: fdpsp, units: 'Bars', meta: { code: fdpsp, units: 'Bars' }, state },
      { name: fdps, id: fdps, units: 'Degrees-Celsius', meta: { code: fdps, units: 'Degrees-Celsius' }, state },
    ];

    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with pointset state', () => {
    // arrange
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: STATE },
      data: {
        points: {
          [faps]: {},
          [fdpsp]: {},
          [fdps]: {},
        },
        timestamp: '2022-04-25T17:00:26Z',
      },
    };

    const expectedPoints: Point[] = [
      { name: faps, id: faps, meta: { code: faps }, state },
      { name: fdpsp, id: fdpsp, meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, meta: { code: fdps }, state },
    ];
    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with pointset config', () => {
    // arrange
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: CONFIG },
      data: {
        points: {
          [faps]: { ref: 'BV11.present_value' },
          [fdpsp]: {},
          [fdps]: { ref: 'AV12.present_value' },
        },
      },
    };

    const expectedPoints: Point[] = [
      { name: faps, id: faps, meta: { code: faps }, state },
      { name: fdpsp, id: fdpsp, meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, meta: { code: fdps }, state },
    ];
    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

  test('merges a device document with pointset', () => {
    // existing point has units and a value
    existingPoints.push({ name: faps, id: faps, value: '70', units: 'Bars', meta: { code: faps, units: 'Bars' } });

    // arrange
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp: '2022-04-25T17:00:26Z',
        points: {
          [faps]: { present_value: 78 },
          [fdpsp]: { present_value: 71 },
          [fdps]: { present_value: 82 },
        },
      },
    };

    const expectedPoints: Point[] = [
      { name: faps, id: faps, value: '78', units: 'Bars', meta: { code: faps, units: 'Bars' }, state },
      { name: fdpsp, id: fdpsp, value: '71', meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, value: '82', meta: { code: fdps }, state },
    ];

    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.createDeviceDocument.validation', () => {
  const inputMessage: UdmiMessage = {
    attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subFolder: VALIDATION_SUB_FOLDER, subType: EVENT },
    data: {
      timestamp: '2022-08-03T17:28:49Z',
      version: '1.3.14',
      status: {
        message: 'Multiple validation errors',
        detail:
          'While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found',
        category: 'category-x',
      },
      errors: [
        {
          message: 'While converting to json node: 2 schema violations found',
          level: 500,
          category: 'category-x',
        },
        {
          message: 'While converting to json node: 1 schema violations found',
          level: 500,
          category: 'category-x',
        },
      ],
    },
  };

  test('a device document with the validation is populated', () => {
    const expectedValidations: Validation = {
      category: 'category-x',
      message: 'Multiple validation errors',
      timestamp: '2022-08-03T17:28:49Z',
      detail:
        'While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found',
      errors: [
        {
          message: 'While converting to json node: 2 schema violations found',
          level: 500,
          category: 'category-x',
        },
        {
          message: 'While converting to json node: 1 schema violations found',
          level: 500,
          category: 'category-x',
        },
      ],
    };

    const expectedDeviceDocument: Device = { name, site, tags: [], validation: expectedValidations };

    // act and assert
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });
});
