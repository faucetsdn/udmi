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
  },
};
