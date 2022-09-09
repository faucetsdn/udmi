import { ApolloContext } from '../server/datasources';
import { Site } from './model';

export const resolvers = {
  Query: {
    siteNames: (_: any, { searchOptions }, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSiteNames(searchOptions);
    },
    sites: (_: any, { searchOptions }, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSites(searchOptions);
    },
  },
  Site: {
    totalDevicesCount: ({ name }: Site, _args: any, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getSiteDevicesCount(name);
    },
    correctDevicesCount: ({ id }: Site, _args: any, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getCorrectDevicesCount(id);
    },
    missingDevicesCount: ({ id }: Site, _args: any, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getMissingDevicesCount(id);
    },
    errorDevicesCount: ({ id }: Site, _args: any, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getErrorDevicesCount(id);
    },
    extraDevicesCount: ({ id }: Site, _args: any, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getExtraDevicesCount(id);
    },
    lastValidated: ({ id }: Site, _args: any, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getLastValidated(id);
    },
    percentValidated: async ({ id, name }: Site, _args: any, { dataSources: { deviceDS, siteDS } }: ApolloContext) => {
      return siteDS.getPercentValidated(id, await deviceDS.getSiteDevicesCount(name));
    },
    totalDeviceErrorsCount: async (
      { name }: Site,
      _args: any,
      { dataSources: { deviceDS, siteDS } }: ApolloContext
    ) => {
      return siteDS.getTotalDeviceErrorsCount((await deviceDS.getSiteDevices(name)).devices);
    },
  },
};
