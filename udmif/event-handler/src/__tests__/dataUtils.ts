import { EVENT, POINTSET_SUB_FOLDER, STATE, VALIDATION_SUB_FOLDER } from '../EventUtils';
import { UdmiEvent } from '../udmi/UdmiEvent';

export function createEvent(attributes: any, data: object = {}): UdmiEvent {
  return { attributes, data };
}

export function createEventFromTypes(subFolder: string, subType: string, deviceId: string = 'AHU-1'): UdmiEvent {
  const defaultAttributes = {
    deviceId,
    deviceRegistryId: 'reg-1',
    deviceNumId: 'num1',
    subFolder,
    subType,
  };
  return { attributes: { ...defaultAttributes }, data: {} };
}

export const SYSTEM_MODEL_EVENT = {
  attributes: {
    deviceId: 'AHU-1',
    deviceNumId: '2625324262579600',
    deviceRegistryId: 'ZZ-TRI-FECTA',
    projectId: 'labs-333619',
    subFolder: 'system',
    subType: 'model',
  },
  data: 'ewogICJsb2NhdGlvbiIgOiB7CiAgICAic2l0ZSIgOiAiWlotVFJJLUZFQ1RBIiwKICAgICJzZWN0aW9uIiA6ICIyLTNOOEMiLAogICAgInBvc2l0aW9uIiA6IHsKICAgICAgIngiIDogMTExLjAsCiAgICAgICJ5IiA6IDEwMi4zCiAgICB9CiAgfSwKICAicGh5c2ljYWxfdGFnIiA6IHsKICAgICJhc3NldCIgOiB7CiAgICAgICJndWlkIiA6ICJkcnc6Ly9UQkMiLAogICAgICAic2l0ZSIgOiAiWlotVFJJLUZFQ1RBIiwKICAgICAgIm5hbWUiIDogIkFIVS0xIgogICAgfQogIH0KfQ==',
  messageId: '4498812851299125',
  publishTime: '2022-04-25T17:05:33.162Z',
};

export const SITE_VALIDATION_EVENT: UdmiEvent = createEvent(
  {
    deviceId: '_validator',
    deviceRegistryId: 'reg-1',
    subFolder: VALIDATION_SUB_FOLDER,
  },
  {
    version: '1.3.14',
    timestamp: '2018-08-26T21:39:29.364Z',
    last_updated: '2022-07-16T18:27:19Z',
    summary: {
      correct_devices: ['AHU-22'],
      extra_devices: [],
      missing_devices: ['GAT-123', 'SNS-4'],
      error_devices: ['AHU-1'],
    },
    devices: {
      'AHU-1': {
        last_seen: '2022-07-16T18:27:19Z',
        oldest_mark: '2022-07-16T18:27:19Z',
        status: {
          message: 'Tickity Boo',
          category: 'system.config.apply',
          timestamp: '2018-08-26T21:39:30.364Z',
          level: 600,
        },
      },
    },
  }
);

export const POINTSET_STATE_EVENT = createEventFromTypes(POINTSET_SUB_FOLDER, STATE, 'AHU-1');

export const DEVICE_VALIDATION_EVENT: UdmiEvent = {
  attributes: {
    deviceId: 'name',
    deviceRegistryId: 'site-1',
    deviceNumId: 'num1',
    subFolder: VALIDATION_SUB_FOLDER,
    subType: EVENT,
  },
  data: {
    timestamp: '2022-08-03T17:28:49Z',
    version: '1.3.14',
    status: {
      timestamp: '2022-08-03T17:28:49Z',
      message: 'Multiple validation errors',
      detail:
        'While converting to json node: 2 schema violations found; While converting to json node: 1 schema violations found',
      category: 'category-x',
      level: 600,
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
