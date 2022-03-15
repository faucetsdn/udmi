import { ApolloServer, gql } from 'apollo-server';
import { GraphQLResponse } from 'apollo-server-types';
import { DocumentNode } from 'graphql';
import MockDeviceDataSource from './MockDeviceDataSource';
import { typeDefs } from '../../server/schema';
import { resolvers } from '../../device/resolvers';
import { getDefaultContextProcessor } from '../../server/context';

let testServer: ApolloServer;
const context = getDefaultContextProcessor();

const QUERY_DEVICES = gql`
  query {
    devices(searchOptions: { batchSize: 10, offset: 10, sortOptions: { direction: DESC, field: "name" }, filter: "" }) {
      totalCount
      totalFilteredCount
      devices {
        id
        name
        make
        model
        site
        section
        lastPayload
        operational
        tags
      }
    }
  }
`;

const QUERY_DEVICE = gql`
  query {
    device(id: "some-id") {
      id
      name
      make
      model
      site
      section
      lastPayload
      operational
      firmwareVersion
      serialNumber
      tags
    }
  }
`;

beforeAll(async () => {
  const dataSources = () => {
    return {
      deviceDS: new MockDeviceDataSource(),
    };
  };

  testServer = new ApolloServer({
    typeDefs,
    resolvers,
    context,
    dataSources: dataSources,
  });
});

describe('Devices', () => {
  test('devices', async () => {
    const result = await runQuery(QUERY_DEVICES, {});
    expect(result).toMatchSnapshot();
  });
  test('device', async () => {
    const result = await runQuery(QUERY_DEVICE, {});
    expect(result).toMatchSnapshot();
  });
});

async function runQuery(gql: DocumentNode, variables: object = {}): Promise<GraphQLResponse> {
  return await testServer.executeOperation({ query: gql, variables });
}
