import { buildPoint, createDevice, getDeviceKey, getDeviceValidation } from '../../device/DeviceDocumentUtils';
import { Device, DeviceValidation } from '../../device/model/Device';
import { CONFIG, MODEL, POINTSET_SUB_FOLDER, STATE, SYSTEM_SUB_FOLDER } from '../../EventUtils';
import { UdmiEvent } from '../../model/UdmiEvent';
import { Point } from '../../device/model/Point';
import { Validation } from '../../model/Validation';
import { createEvent, DEVICE_VALIDATION_EVENT } from '../dataUtils';

const today: string = '2022-11-01T09:09:09Z';

jest.useFakeTimers().setSystemTime(new Date(today));

const name: string = 'name';
const site: string = 'site-1';
const id: string = 'num1';
const BASIC_SYSTEM_ATTRIBUTES = {
  deviceId: name,
  deviceRegistryId: site,
  deviceNumId: id,
  subFolder: SYSTEM_SUB_FOLDER,
};
const BASIC_POINTSET_ATTRIBUTES = {
  deviceId: name,
  deviceRegistryId: site,
  deviceNumId: id,
  subFolder: POINTSET_SUB_FOLDER,
};
const AHU_ID: string = 'AHU-1';
const AHU_REGISTRY_ID: string = 'reg-1';

describe('DeviceDocumentUtils.createDevice.default', () => {
  const tags: string[] = [];

  test('creates a default device document', () => {
    const inputEvent: UdmiEvent = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: {} };
    const expectedDevice: Device = { name, site, id, tags };
    expect(createDevice(inputEvent, [])).toEqual(expectedDevice);
  });

  test('creates a default device document with a timestamp', () => {
    const timestamp: string = '2022-04-25T17:06:12.454Z';
    const inputEvent: UdmiEvent = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: { timestamp } };
    const expectedDevice: Device = { name, site, id, lastPayload: timestamp, tags };
    expect(createDevice(inputEvent, [])).toEqual(expectedDevice);
  });
});

describe('DeviceDocumentUtils.createDevice.system', () => {
  const tags: string[] = [];

  test('creates a device document with system state', () => {
    const make: string = 'make-a';
    const model: string = 'model-a';
    const operational: string = 'true';
    const serialNumber: string = 'serial-no';
    const firmware: string = 'v1';
    const lastPayload: string = '2022-03-21T13:19:32Z';
    const lastStateSaved: string = today;
    const lastStateUpdated: string = '2022-03-21T13:19:32Z';

    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: STATE },
      data: {
        software: { firmware },
        hardware: { make, model },
        operational,
        serial_no: serialNumber,
        timestamp: '2022-03-21T13:19:32Z',
      },
    };
    const expectedDevice: Device = {
      name,
      site,
      id,
      make,
      model,
      operational,
      serialNumber,
      firmware,
      tags,
      lastPayload,
      lastStateSaved,
      lastStateUpdated,
    };
    expect(createDevice(inputEvent, [])).toEqual(expectedDevice);
  });

  test('creates a device document with system model', () => {
    const section: string = 'section-a';
    const lastPayload: string = '2022-03-21T13:19:32Z';

    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: MODEL },
      data: {
        location: { section, site },
        timestamp: '2022-03-21T13:19:32Z',
      },
    };
    const expectedDevice: Device = { name, section, site, id, tags, lastPayload };
    expect(createDevice(inputEvent, [])).toEqual(expectedDevice);
  });
});

describe('DeviceDocumentUtils.createDevice.pointset', () => {
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
    const timestamp: string = '2022-04-25T17:00:26Z';
    // arrange
    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp,
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

    const expectedDevice: Device = { name, site, id, tags: [], points: expectedPoints, lastPayload: timestamp };

    // act and assert
    expect(createDevice(inputEvent, undefined)).toEqual(expectedDevice);
  });

  test('creates a device document with pointset', () => {
    const timestamp: string = '2022-04-25T17:00:26Z';
    // arrange
    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp,
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

    const expectedDevice: Device = { name, site, id, tags: [], points: expectedPoints, lastPayload: timestamp };

    // act and assert
    expect(createDevice(inputEvent, existingPoints)).toEqual(expectedDevice);
  });

  test('creates a device document with pointset model', () => {
    const inputEvent: UdmiEvent = {
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

    const expectedDevice: Device = { name, site, id, tags: [], points: expectedPoints };

    // act and assert
    expect(createDevice(inputEvent, existingPoints)).toEqual(expectedDevice);
  });

  test('creates a device document with pointset state', () => {
    const timestamp: string = '2022-04-25T17:00:26Z';
    // arrange
    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: STATE },
      data: {
        points: {
          [faps]: {},
          [fdpsp]: {},
          [fdps]: {},
        },
        timestamp,
      },
    };

    const expectedPoints: Point[] = [
      { name: faps, id: faps, meta: { code: faps }, state },
      { name: fdpsp, id: fdpsp, meta: { code: fdpsp }, state },
      { name: fdps, id: fdps, meta: { code: fdps }, state },
    ];
    const expectedDevice: Device = {
      name,
      site,
      id,
      tags: [],
      points: expectedPoints,
      lastPayload: timestamp,
      lastTelemetrySaved: today,
      lastTelemetryUpdated: timestamp,
    };

    // act and assert
    expect(createDevice(inputEvent, existingPoints)).toEqual(expectedDevice);
  });

  test('creates a device document with pointset config', () => {
    // arrange
    const inputEvent: UdmiEvent = {
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
    const expectedDevice: Device = { name, site, id, tags: [], points: expectedPoints };

    // act and assert
    expect(createDevice(inputEvent, existingPoints)).toEqual(expectedDevice);
  });

  test('merges a device document with pointset', () => {
    const timestamp: string = '2022-04-25T17:00:26Z';
    // existing point has units and a value
    existingPoints.push(getFapsPoint('70', 'Bars'));

    // arrange
    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp,
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

    const expectedDevice: Device = { name, site, id, tags: [], points: expectedPoints, lastPayload: timestamp };

    // act and assert
    expect(createDevice(inputEvent, existingPoints)).toEqual(expectedDevice);
  });

  test('uses existing point values if none are passed in', () => {
    // existing point has units and a value
    const point: Point = getFapsPoint('70', 'Bars');

    // arrange
    const inputEvent: UdmiEvent = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES },
      data: {
        timestamp: '2022-04-25T17:00:26Z',
        points: {
          [faps]: {},
        },
      },
    };

    const resultingPoint = buildPoint(inputEvent, point, faps);
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
    const inputEvent: UdmiEvent = DEVICE_VALIDATION_EVENT;
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

    const expectedDevice: Device = { name, site, id, tags: [], validation: expectedValidations };

    // act and assert
    expect(createDevice(inputEvent, [])).toEqual(expectedDevice);
  });
});

describe('getDeviceValidationDocument', () => {
  const inputEvent: UdmiEvent = DEVICE_VALIDATION_EVENT;

  test('returns a device validation document', () => {
    // arrange and act
    const validation: DeviceValidation = getDeviceValidation(inputEvent, {
      name: 'name',
      site: 'string',
    });
    const expectedValidation = {
      timestamp: new Date(inputEvent.data.timestamp),
      deviceKey: { name: 'name', site: 'string' },
      data: inputEvent.data,
    };
    // assert
    expect(validation).toEqual(expectedValidation);
  });

  test('throws an exception if a mandatory field deviceId is null', () => {
    // arrange
    const event = createEvent({
      deviceId: null,
      deviceRegistryId: AHU_REGISTRY_ID,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    });
    // act and assert
    expect(() => {
      getDeviceKey(event);
    }).toThrowError('An invalid device id was submitted');
  });

  test('throws an exception if a mandatory field deviceRegistryId) is null', () => {
    // arrange
    const event = createEvent({
      deviceId: AHU_ID,
      deviceRegistryId: null,
      subFolder: SYSTEM_SUB_FOLDER,
      subType: MODEL,
    });
    // act and assert
    expect(() => {
      getDeviceKey(event);
    }).toThrowError('An invalid site was submitted');
  });
});
