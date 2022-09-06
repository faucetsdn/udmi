export const resolvers = {
  Query: {
    siteNames: (_, { searchOptions }, { dataSources: { siteDS } }) => {
      return siteDS.getSiteNames(searchOptions);
    },
    sites: (_, { searchOptions }, { dataSources: { siteDS } }) => {
      return siteDS.getSites(searchOptions);
    },
  },
};
