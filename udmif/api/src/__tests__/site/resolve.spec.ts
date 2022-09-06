import { ApolloServer, gql } from 'apollo-server';
import { GraphQLResponse } from 'apollo-server-types';
import { DocumentNode } from 'graphql';
import MockSiteDataSource from './MockSiteDataSource';
import { typeDefs } from '../../server/schema';
import { resolvers } from '../../server/resolvers';
import { getDefaultContextProcessor } from '../../server/context';

let testServer: ApolloServer;
const clientIds: string[] = ['', ''];
const context = getDefaultContextProcessor(clientIds);

const QUERY_SITES = gql`
  query {
    sites(searchOptions: { batchSize: 10, offset: 10, sortOptions: { direction: DESC, field: "name" }, filter: "" }) {
      totalCount
      totalFilteredCount
      sites {
        id
        name
      }
    }
  }
`;

const QUERY_SITE_NAMES = gql`
  query {
    siteNames
  }
`;

beforeAll(async () => {
  const dataSources = () => {
    return {
      siteDS: new MockSiteDataSource(),
    };
  };

  testServer = new ApolloServer({
    typeDefs,
    resolvers,
    context,
    dataSources: dataSources,
  });
});

describe('Sites', () => {
  test('sites', async () => {
    const result = await runQuery(QUERY_SITES, {});
    expect(result).toMatchSnapshot();
  });

  test('getSiteNames', async () => {
    const result = await runQuery(QUERY_SITE_NAMES);
    expect(result).toMatchSnapshot();
  });
});

async function runQuery(gql: DocumentNode, variables: object = {}): Promise<GraphQLResponse> {
  return await testServer.executeOperation({ query: gql, variables });
}
