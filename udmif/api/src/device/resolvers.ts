import { SORT_DIRECTION } from './model';

export const resolvers = {
  SORT_DIRECTION: {
    DESC: SORT_DIRECTION.DESC,
    ASC: SORT_DIRECTION.ASC,
  },
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
  },
};
