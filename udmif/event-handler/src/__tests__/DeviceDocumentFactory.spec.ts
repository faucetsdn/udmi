import { createDeviceDocument } from '../DeviceDocumentFactory';
import { DeviceDocument } from '../DeviceDocument';
import {
  CONFIG,
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

  const points: Point[] = [];
  const tags: string[] = [];

  test('creates a default device document', () => {
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: {} };
    const expectedDeviceDocument: DeviceDocument = { name: 'name', id: 'id', tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });

  test('creates a default device document with a timestamp', () => {
    const timestamp: string = '2022-04-25T17:06:12.454Z';
    const inputMessage: UdmiMessage = { attributes: { ...BASIC_SYSTEM_ATTRIBUTES }, data: { timestamp } };
    const expectedDeviceDocument: DeviceDocument = { name: 'name', id: 'id', lastPayload: timestamp, tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentFactory.createDeviceDocument.system', () => {
  const tags: string[] = [];
  const points: Point[] = [];

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
    const expectedDeviceDocument: DeviceDocument = { name, id, make, model, operational, serialNumber, firmware, tags };
    expect(createDeviceDocument(inputMessage, [])).toEqual(expectedDeviceDocument);
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
    const expectedDeviceDocument: DeviceDocument = { name, id, section, site, tags };
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
  })

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

    const expectedDeviceDocument: DeviceDocument = { name, id, tags: [], points: expectedPoints };

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

    const expectedDeviceDocument: DeviceDocument = { name, id, tags: [], points: expectedPoints };

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
    const expectedDeviceDocument: DeviceDocument = { name, id, tags: [], points: expectedPoints };

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
    const expectedDeviceDocument: DeviceDocument = { name, id, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

  test('merges a device document with pointset', () => {

    // existing point has units and a value
    existingPoints.push(
      { name: faps, id: faps, value: '70', units: 'Bars', meta: { code: faps, units: 'Bars' } }
    )

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

    const expectedDeviceDocument: DeviceDocument = { name, id, tags: [], points: expectedPoints };

    // act and assert
    expect(createDeviceDocument(inputMessage, existingPoints)).toEqual(expectedDeviceDocument);
  });

});

