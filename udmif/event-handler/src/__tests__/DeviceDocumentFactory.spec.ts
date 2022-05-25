import { createDeviceDocument } from '../DeviceDocumentFactory';
import { DeviceDocument } from '../DeviceDocument';
import {
  CONFIG,
  isPointset,
  isPointsetConfig,
  isPointsetModel,
  isPointsetState,
  isSubFolder,
  isSystemModel,
  isSystemState,
  MODEL,
  POINTSET_SUB_FOLDER,
  STATE,
  SYSTEM_SUB_FOLDER,
} from '../DocumentTypeUtil';
import { UdmiMessage } from '../UdmiMessage';
import { Point } from '../Point';

const name: string = 'name';
const id: string = 'id';
const BASIC_SYSTEM_ATTRIBUTES = { deviceId: name, deviceNumId: id, subFolder: SYSTEM_SUB_FOLDER };
const BASIC_POINTSET_ATTRIBUTES = { deviceId: name, deviceNumId: id, subFolder: POINTSET_SUB_FOLDER };

describe('DeviceDocumentFactory.createDeviceDocument.default', () => {
  test('creates a default device document', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: {} };
    const expectedDeviceDocument: DeviceDocument = { name: 'name', id: 'id' };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a default device document with a timestamp', () => {
    const timestamp: string = '2022-04-25T17:06:12.454Z';
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: { timestamp } };
    const expectedDeviceDocument: DeviceDocument = { name: 'name', id: 'id', lastPayload: timestamp };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.createDeviceDocument.system', () => {
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
    const expectedDeviceDocument: DeviceDocument = { name, id, make, model, operational, serialNumber, firmware };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with system model', () => {
    const section: string = 'section-a';
    const site: string = 'site-a';

    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: MODEL },
      data: {
        location: { section, site },
      },
    };
    const expectedDeviceDocument: DeviceDocument = { name, id, section, site };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.createDeviceDocument.pointset', () => {
  const faps = 'filter_alarm_pressure_status';
  const fdpsp = 'filter_differential_pressure_setpoint';
  const fdps = 'filter_differential_pressure_sensor';

  test('creates a device document with pointset', () => {
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

    const points: Point[] = [
      { name: faps, id: faps, value: '78', meta: { code: faps } },
      { name: fdpsp, id: fdpsp, value: '71', meta: { code: fdpsp } },
      { name: fdps, id: fdps, value: '82', meta: { code: fdps } },
    ];

    const expectedDeviceDocument: DeviceDocument = { name, id, points };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with pointset model', () => {
    const inputMessage: UdmiMessage = {
      attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: MODEL },
      data: {
        points: {
          [faps]: {
            units: 'No-units',
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

    const points: Point[] = [
      { name: faps, id: faps, units: 'No-units', meta: { code: faps, units: 'No-units' } },
      { name: fdpsp, id: fdpsp, units: 'Bars', meta: { code: fdpsp, units: 'Bars' } },
      { name: fdps, id: fdps, units: 'Degrees-Celsius', meta: { code: fdps, units: 'Degrees-Celsius' } },
    ];

    const expectedDeviceDocument: DeviceDocument = { name, id, points };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with pointset state', () => {
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

    const points: Point[] = [
      { name: faps, id: faps, meta: { code: faps } },
      { name: fdpsp, id: fdpsp, meta: { code: fdpsp } },
      { name: fdps, id: fdps, meta: { code: fdps } },
    ];
    const expectedDeviceDocument: DeviceDocument = { name, id, points };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with pointset config', () => {
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

    const points: Point[] = [
      { name: faps, id: faps, meta: { code: faps } },
      { name: fdpsp, id: fdpsp, meta: { code: fdpsp } },
      { name: fdps, id: fdps, meta: { code: fdps } },
    ];
    const expectedDeviceDocument: DeviceDocument = { name, id, points };
    expect(createDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.isSystemState', () => {
  test('is a system state', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: STATE }, data: {} };
    expect(isSystemState(inputMessage)).toEqual(true);
  });

  test('is not a system state', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: 'garbage' }, data: {} };
    expect(isSystemState(inputMessage)).toEqual(false);
  });
});

describe('DeviceDocumentFactory.isSystemModel', () => {
  test('is a system model', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: MODEL }, data: {} };
    expect(isSystemModel(inputMessage)).toEqual(true);
  });

  test('is not a system model', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES, subType: 'garbage' }, data: {} };
    expect(isSystemModel(inputMessage)).toEqual(false);
  });
});

describe('DeviceDocumentFactory.isPointset...', () => {
  test('is a pointset sub folder', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_POINTSET_ATTRIBUTES }, data: {} };
    expect(isSubFolder(inputMessage, POINTSET_SUB_FOLDER)).toEqual(true);
  });

  test('is a pointset message', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_POINTSET_ATTRIBUTES }, data: {} };
    expect(isPointset(inputMessage)).toEqual(true);
  });

  test('is a pointset model message', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: MODEL }, data: {} };
    expect(isPointsetModel(inputMessage)).toEqual(true);
  });

  test('is a pointset state message', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: STATE }, data: {} };
    expect(isPointsetState(inputMessage)).toEqual(true);
  });

  test('is a pointset config message', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_POINTSET_ATTRIBUTES, subType: CONFIG }, data: {} };
    expect(isPointsetConfig(inputMessage)).toEqual(true);
  });
});
