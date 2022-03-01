export const resolvers = {
  Query: {
    devices: (_, { searchOptions }, { dataSources: { deviceDS } }) => {
      return deviceDS.getDevices(searchOptions);
    },
  },
};
