export const resolvers = {
  Query: {
    devices: (_, {}, { dataSources: { deviceDS } }) => {
      return deviceDS.getDevices();
    },
  },
};
