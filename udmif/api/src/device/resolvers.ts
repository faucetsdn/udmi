import { Status } from '../site/model';
import { DistinctArgs } from '../common/model';
import { ApolloContext } from '../server/datasources';
import { Device, DeviceArgs, DevicesArgs, PointsArgs } from './model';

export const resolvers = {
  Query: {
    devices: (_query, { searchOptions }: DevicesArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> devices');
      return deviceDS.getDevices(searchOptions);
    },
    device: (_query, { id }: DeviceArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> device');
      return deviceDS.getDevice(id);
    },
    points: (_query, { deviceId }: PointsArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> Main points');
      return deviceDS.getPoints(deviceId);
    },
    deviceNames: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> deviceNames');
      return deviceDS.getDeviceNames(searchOptions);
    },
    deviceMakes: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> deviceMakes');
      return deviceDS.getDeviceMakes(searchOptions);
    },
    deviceModels: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> deviceModels');
      return deviceDS.getDeviceModels(searchOptions);
    },
    sections: (_query, { searchOptions }: DistinctArgs, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL -> sections');
      return deviceDS.getSections(searchOptions);
    },
  },
  Device: {
    validation: (device: Device) => {
      console.log('GQL Sub Query -> validation');
      return JSON.stringify(device.validation);
    },
    level: async (device: Device, _args, { dataSources: { siteDS } }: ApolloContext) => {
      console.log('GQL Sub Query -> level');
      return (await siteDS.getSiteDeviceStatus(device.site, device.name))?.level || null;
    },
    message: async (device: Device, _args, { dataSources: { siteDS } }: ApolloContext) => {
      console.log('GQL Sub Query -> message');
      return (await siteDS.getSiteDeviceStatus(device.site, device.name))?.message || null;
    },
    details: async (device: Device, _args, { dataSources: { siteDS } }: ApolloContext) => {
      console.log('GQL Sub Query -> details');
      return (await siteDS.getSiteDeviceStatus(device.site, device.name))?.detail || null;
    },
    points: async (device: Device, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      console.log('GQL Sub Query -> points');
      return await deviceDS.getPoints(device.uuid);
    },
    state: async (device: Device, _args, { dataSources: { siteDS } }: ApolloContext) => {
      console.log('GQL Sub Query -> state');
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
