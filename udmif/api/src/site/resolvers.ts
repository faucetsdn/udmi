import {
  getCorrectDevicesCount,
  getErrorDevicesCount,
  getMissingDevicesCount,
  getTotalDevicesCount,
  getExtraDevicesCount,
} from './siteUtil';
import { DistinctArgs } from '../common/model';
import { ApolloContext } from '../server/datasources';
import { Site, SiteArgs, SitesArgs } from './model';

export const resolvers = {
  Query: {
    siteNames: (_query, { searchOptions }: DistinctArgs, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSiteNames(searchOptions);
    },
    sites: (_query, { searchOptions }: SitesArgs, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSites(searchOptions);
    },
    site: (_query, { name }: SiteArgs, { dataSources: { siteDS } }: ApolloContext) => {
      return siteDS.getSite(name);
    },
  },
  Site: {
    seenDevicesCount: async (site: Site, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return (await deviceDS.getDevicesBySite(site.name)).totalFilteredCount;
    },
    totalDevicesCount: async (site: Site) => {
      return getTotalDevicesCount(site);
    },
    correctDevicesCount: (site: Site) => {
      return getCorrectDevicesCount(site);
    },
    correctDevicesPercent: (site: Site) => {
      return getCorrectDevicesCount(site) / (getTotalDevicesCount(site) || 1);
    },
    missingDevicesCount: (site: Site) => {
      return getMissingDevicesCount(site);
    },
    missingDevicesPercent: (site: Site) => {
      return getMissingDevicesCount(site) / (getTotalDevicesCount(site) || 1);
    },
    errorDevicesCount: (site: Site) => {
      return getErrorDevicesCount(site);
    },
    errorDevicesPercent: (site: Site) => {
      return getErrorDevicesCount(site) / (getTotalDevicesCount(site) || 1);
    },
    extraDevicesCount: (site: Site) => {
      return getExtraDevicesCount(site);
    },
    lastValidated: (site: Site) => {
      return site.validation?.last_updated;
    },
    deviceErrors: (site: Site, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return deviceDS.getDeviceErrorsBySite(site.name);
    },
    totalDeviceErrorsCount: async (site: Site, _args, { dataSources: { deviceDS } }: ApolloContext) => {
      return (await deviceDS.getDeviceErrorsBySite(site.name)).length;
    },
    validation: (site: Site) => {
      return JSON.stringify(site.validation);
    },
  },
};
