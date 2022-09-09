import { Site } from './model';

export const resolvers = {
  Query: {
    siteNames: (_, { searchOptions }, { dataSources: { siteDS } }) => {
      return siteDS.getSiteNames(searchOptions);
    },
    sites: (_, { searchOptions }, { dataSources: { siteDS } }) => {
      return siteDS.getSites(searchOptions);
    },
  },
  Site: {
    totalDevicesCount: (site: Site, _args: any, { dataSources: { deviceDS } }) => {
      return deviceDS.getSiteDevicesCount(site.name);
    },
    correctDevicesCount: (site: Site, _args: any, { dataSources: { siteDS } }) => {
      return siteDS.getCorrectDevicesCount(site.id);
    },
    missingDevicesCount: (site: Site, _args: any, { dataSources: { siteDS } }) => {
      return siteDS.getMissingDevicesCount(site.id);
    },
    errorDevicesCount: (site: Site, _args: any, { dataSources: { siteDS } }) => {
      return siteDS.getErrorDevicesCount(site.id);
    },
    extraDevicesCount: (site: Site, _args: any, { dataSources: { siteDS } }) => {
      return siteDS.getExtraDevicesCount(site.id);
    },
    lastValidated: (site: Site, _args: any, { dataSources: { siteDS } }) => {
      return siteDS.getLastValidated(site.id);
    },
    percentValidated: async (site: Site, _args: any, { dataSources: { deviceDS, siteDS } }) => {
      return siteDS.getPercentValidated(site.id, await deviceDS.getSiteDevicesCount(site.name));
    },
    totalDeviceErrorsCount: async (site: Site, _args: any, { dataSources: { deviceDS, siteDS } }) => {
      return siteDS.getTotalDeviceErrorsCount((await deviceDS.getSiteDevices(site.name)).devices);
    },
  },
};
