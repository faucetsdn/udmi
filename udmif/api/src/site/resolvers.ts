import { SORT_DIRECTION } from './model';

export const resolvers = {
  SORT_DIRECTION: {
    DESC: SORT_DIRECTION.DESC,
    ASC: SORT_DIRECTION.ASC,
  },
  Query: {
    siteNames: (_, { searchOptions }, { dataSources: { siteDS } }) => {
      return siteDS.getSiteNames(searchOptions);
    },
    sites: (_, { searchOptions }, { dataSources: { siteDS } }) => {
      return siteDS.getSites(searchOptions);
    },
  },
};
