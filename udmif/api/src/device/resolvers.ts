import { DistinctArgs } from '../common/model';
import { ApolloContext } from '../server/datasources';
import { Device, DeviceArgs, DevicesArgs, PointsArgs } from './model';

export const resolvers = {
  Query: {
    devices: (_query, { searchOptions }: DevicesArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDevices(searchOptions);
    },
    device: (_query, { id }: DeviceArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDevice(id);
    },
    points: (_query, { deviceId }: PointsArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getPoints(deviceId);
    },
    deviceNames: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDeviceNames(searchOptions);
    },
    deviceMakes: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDeviceMakes(searchOptions);
    },
    deviceModels: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDeviceModels(searchOptions);
    },
    sections: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getSections(searchOptions);
    },
  },
  Device: {
    validation: (device: Device) => {
      return JSON.stringify(device.validation);
    },
    level: (device: Device) => {
      return device.validation?.status?.level || null;
    },
    message: (device: Device) => {
      return device.validation?.status?.message || null;
    },
    details: (device: Device) => {
      return device.validation?.status?.detail || null;
    },
    points: async (device: Device, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return await deviceDS.getPoints(device.uuid);
    },
    state: async (device: Device, _args, { dataSources: { siteDS } }: ApolloContext) => {
      const siteValidationSummary = (await siteDS.getSiteValidation(device.site))?.summary || [];

      for (const category in siteValidationSummary) {
        if (Object.prototype.hasOwnProperty.call(siteValidationSummary, category)) {
          const devices = siteValidationSummary[category];

          if (devices.includes(device.name)) {
            return category.replace('_devices', '').toUpperCase();
          }
        }
      }
    },
    errorsCount: (device: Device) => {
      return device.validation?.errors.length ?? 0;
    },
  },
};
