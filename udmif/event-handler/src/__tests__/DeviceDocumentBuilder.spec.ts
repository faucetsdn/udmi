import { buildDeviceDocument, isSystemModel, isSystemState } from '../DeviceDocumentBuilder';
import { DeviceDocument } from '../model';

const SYSTEM_SUB_FOLDER = 'system';
const MODEL_SUB_TYPE = 'model';
const STATE_SUB_TYPE = 'state';

describe('DeviceDocumentBuilder.buildDeviceDocument.default', () => {
  test('creates a default device document', () => {
    const inputMessage = { attributes: { deviceId: 'name', deviceNumId: 'id' }, data: {} };
    const expectedDeviceDocument: DeviceDocument = { name: 'name', id: 'id' };
    expect(buildDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a default device document with a timestamp', () => {
    const timestamp: string = '2022-04-25T17:06:12.454Z';
    const inputMessage = { attributes: { deviceId: 'name', deviceNumId: 'id' }, data: { timestamp } };
    const expectedDeviceDocument: DeviceDocument = { name: 'name', id: 'id', lastPayload: timestamp };
    expect(buildDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentBuilder.buildDeviceDocument.system', () => {
  test('creates a device document with system state', () => {
    const name: string = 'name';
    const id: string = 'id';
    const make: string = 'make-a';
    const model: string = 'model-a';
    const operational: string = 'true';
    const serialNumber: string = 'serial-no';
    const firmware: string = 'v1';

    const inputMessage = {
      attributes: { deviceId: name, deviceNumId: id, subFolder: SYSTEM_SUB_FOLDER, subType: STATE_SUB_TYPE },
      data: {
        software: { firmware },
        hardware: { make, model },
        operational,
        serial_no: serialNumber,
      },
    };
    const expectedDeviceDocument: DeviceDocument = { name, id, make, model, operational, serialNumber, firmware };
    expect(buildDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });

  test('creates a device document with system model', () => {
    const name: string = 'name';
    const id: string = 'id';
    const section: string = 'section-a';
    const site: string = 'site-a';

    const inputMessage = {
      attributes: { deviceId: name, deviceNumId: id, subFolder: SYSTEM_SUB_FOLDER, subType: MODEL_SUB_TYPE },
      data: {
        location: { section, site },
      },
    };
    const expectedDeviceDocument: DeviceDocument = { name, id, section, site };
    expect(buildDeviceDocument(inputMessage)).toEqual(expectedDeviceDocument);
  });
});

describe('DeviceDocumentBuilder.isSystemState', () => {
  test('creates a default device document', () => {
    const inputMessage = { attributes: { subFolder: SYSTEM_SUB_FOLDER, subType: STATE_SUB_TYPE }, data: {} };
    expect(isSystemState(inputMessage)).toEqual(true);
  });

  test('is not a system state', () => {
    const inputMessage = { attributes: { subFolder: SYSTEM_SUB_FOLDER, subType: 'garbage' }, data: {} };
    expect(isSystemState(inputMessage)).toEqual(false);
  });
});

describe('DeviceDocumentBuilder.isSystemModel', () => {
  test('is a system model', () => {
    const inputMessage = { attributes: { subFolder: SYSTEM_SUB_FOLDER, subType: MODEL_SUB_TYPE }, data: {} };
    expect(isSystemModel(inputMessage)).toEqual(true);
  });

  test('is not a system model', () => {
    const inputMessage = { attributes: { subFolder: SYSTEM_SUB_FOLDER, subType: 'garbage' }, data: {} };
    expect(isSystemModel(inputMessage)).toEqual(false);
  });
});
