import { sum } from 'lodash';
import { ApolloContext } from '../server/datasources';
import { Site, SiteNamesArgs, SitesArgs } from './model';

export const resolvers = {
  Query: {
    siteNames: (_query, { searchOptions }: SiteNamesArgs, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSiteNames(searchOptions);
    },
    sites: (_query, { searchOptions }: SitesArgs, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSites(searchOptions);
    },
  },
  Site: {
    totalDevicesCount: async (site: Site, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return (await deviceDS.getDevicesBySite(site.name)).totalFilteredCount;
    },
    correctDevicesCount: (site: Site) => {
      return site.validation?.summary.correct_devices.length;
    },
    missingDevicesCount: (site: Site) => {
      return site.validation?.summary.missing_devices.length;
    },
    errorDevicesCount: (site: Site) => {
      return site.validation?.summary.error_devices.length;
    },
    extraDevicesCount: (site: Site) => {
      return site.validation?.summary.extra_devices.length;
    },
    lastValidated: (site: Site) => {
      return site.validation?.last_updated;
    },
    percentValidated: async (site: Site, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return (
        sum([
          0,
          site.validation?.summary.correct_devices.length,
          site.validation?.summary.missing_devices.length,
          site.validation?.summary.error_devices.length,
          site.validation?.summary.extra_devices.length,
        ]) / (await deviceDS.getDevicesBySite(site.name)).totalFilteredCount
      );
    },
    totalDeviceErrorsCount: (site: Site, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDeviceErrorsCountBySite(site.name);
    },
  },
};
