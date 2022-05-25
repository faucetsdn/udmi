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

const name: string = 'name';
const id: string = 'id';
const BASIC_SYSTEM_ATTRIBUTES = { deviceId: name, deviceNumId: id, subFolder: SYSTEM_SUB_FOLDER };
const BASIC_POINTSET_ATTRIBUTES = { deviceId: name, deviceNumId: id, subFolder: POINTSET_SUB_FOLDER };

describe('DeviceTypeUtils.isSystemState', () => {
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
