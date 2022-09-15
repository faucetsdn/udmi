import { Device } from './model';

export const resolvers = {
  Query: {
    devices: (_, { searchOptions }, { dataSources: { deviceDS } }) => {
      return deviceDS.getDevices(searchOptions);
    },
    device: (_, { id }, { dataSources: { deviceDS } }) => {
      return deviceDS.getDevice(id);
    },
    points: (_, { deviceId }, { dataSources: { deviceDS } }) => {
      return deviceDS.getPoints(deviceId);
    },
    deviceNames: (_, { searchOptions }, { dataSources: { deviceDS } }) => {
      return deviceDS.getDeviceNames(searchOptions);
    },
    deviceMakes: (_, { searchOptions }, { dataSources: { deviceDS } }) => {
      return deviceDS.getDeviceMakes(searchOptions);
    },
    deviceModels: (_, { searchOptions }, { dataSources: { deviceDS } }) => {
      return deviceDS.getDeviceModels(searchOptions);
    },
    sections: (_, { searchOptions }, { dataSources: { deviceDS } }) => {
      return deviceDS.getSections(searchOptions);
    },
  },
  Device: {
    validation: (device: Device) => {
      return JSON.stringify(device.validation);
    },
  },
};
