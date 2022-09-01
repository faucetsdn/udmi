import {
  buildPoint,
  createDeviceDocument,
  getDeviceKey,
  getDeviceValidationMessage,
} from '../../device/DeviceDocumentUtils';
import { Device, DeviceValidation } from '../../device/model/Device';
import { CONFIG, MODEL, POINTSET_SUB_FOLDER, STATE, SYSTEM_SUB_FOLDER } from '../../MessageUtils';
import { UdmiMessage } from '../../model/UdmiMessage';
import { Point } from '../../device/model/Point';
import { Validation } from '../../model/Validation';
import { createMessage, DEVICE_VALIDATION_EVENT } from '../dataUtils';

const name: string = 'name';
const site: string = 'site-1';
const BASIC_SYSTEM_ATTRIBUTES = { deviceId: name, deviceRegistryId: site, subFolder: SYSTEM_SUB_FOLDER };
const BASIC_POINTSET_ATTRIBUTES = { deviceId: name, deviceRegistryId: site, subFolder: POINTSET_SUB_FOLDER };
const AHU_ID: string = 'AHU-1';
const AHU_REGISTRY_ID: string = 'reg-1';

describe('DeviceDocumentUtils.createDeviceDocument.default', () => {
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

describe('DeviceDocumentUtils.createDeviceDocument.system', () => {
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

describe('DeviceDocumentUtils.createDeviceDocument.pointset', () => {
  const NO_UNITS = 'No-units';
  const faps = 'filter_alarm_pressure_status';
  const fdpsp = 'filter_differential_pressure_setpoint';
  const fdps = 'filter_differential_pressure_sensor';

  let existingPoints: Point[];
  const state: string = '';

  beforeEach(() => {
    existingPoints = [];
  });

  test('creates a device document when existing points are undefined', () => {
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
      getFapsPoint('78', null),
      { name: fdpsp, id: fdpsp, value: '71', meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, value: '82', meta: { code: fdps }, state },
    ];

    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, undefined)).toEqual(expectedDeviceDocument);
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
      getFapsPoint('78', null),
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
      getFapsPoint(null, NO_UNITS),
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
    existingPoints.push(getFapsPoint('70', 'Bars'));

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
      getFapsPoint('78', 'Bars'),
      { name: fdpsp, id: fdpsp, value: '71', meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, value: '82', meta: { code: fdps }, state },
    ];

    const expectedDeviceDocument: Device = { name, site, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

  test('uses existing point values if none are passed in', () => {
    // existing point has units and a value
    const point: Point = getFapsPoint('70', 'Bars');

    // arrange
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp: '2022-04-25T17:00:26Z',
        points: {
          [faps]: {},
        },
      },
    };

    const resultingPoint = buildPoint(inputMessage, point, faps);
    expect(resultingPoint).toEqual({
      id: 'filter_alarm_pressure_status',
      meta: {
        code: 'filter_alarm_pressure_status',
        units: 'Bars',
      },
      name: 'filter_alarm_pressure_status',
      state: '',
      units: 'Bars',
      value: '70',
    });
  });

  function getFapsPoint(value: string, units: string): Point {
    const point: Point = { name: faps, id: faps };
    point.state = state;
    point.meta = { code: faps };
    if (value) {
      point.value = value;
    }
    if (units) {
      point.units = units;
      point.meta.units = units;
    }
    return point;
  }
});

describe('DeviceDocumentUtils.createDeviceDocument.validation', () => {
  test('a device document with the validation is populated', () => {
    // arrange
    const inputMessage: UdmiMessage = DEVICE_VALIDATION_EVENT;
    const expectedValidations: Validation = {
      timestamp: '2022-08-03T17:28:49Z',
      version: '1.3.14',
      category: 'category-x',
      status: {
        timestamp: '2022-08-03T17:28:49Z',
        message: 'Multiple validation errors',
        detail:
          'While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found',
        category: 'category-x',
        level: 600,
      },
      message: 'Multiple validation errors',
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

describe('getDeviceValidationDocument', () => {
  const inputMessage: UdmiMessage = DEVICE_VALIDATION_EVENT;

  test('returns a device validation document', () => {
    // arrange and act
    const validationMessage: DeviceValidation = getDeviceValidationMessage(inputMessage, {
      name: 'name',
      site: 'string',
    });
    const expectedMessage = {
      timestamp: new Date(inputMessage.data.timestamp),
      deviceKey: { name: 'name', site: 'string' },
      message: inputMessage.data,
    };
    // assert
    expect(validationMessage).toEqual(expectedMessage);
  });

  test('throws an exception if a mandatory field deviceId is null', () => {
    // arrange
    const message = createMessage({
      deviceId: null,
      deviceRegistryId: AHU_REGISTRY_ID,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    });
    // act and assert
    expect(() => {
      getDeviceKey(message);
    }).toThrowError('An invalid device id was submitted');
  });

  test('throws an exception if a mandatory field deviceRegistryId) is null', () => {
    // arrange
    const message = createMessage({
      deviceId: AHU_ID,
      deviceRegistryId: null,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    });
    // act and assert
    expect(() => {
      getDeviceKey(message);
    }).toThrowError('An invalid site was submitted');
  });
});
