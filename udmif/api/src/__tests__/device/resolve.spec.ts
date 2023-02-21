import { ApolloServer, gql } from 'apollo-server';
import { GraphQLResponse } from 'apollo-server-types';
import { DocumentNode } from 'graphql';
import MockDeviceDataSource from './MockDeviceDataSource';
import { typeDefs } from '../../server/schema';
import { resolvers } from '../../server/resolvers';
import { getDefaultContextProcessor } from '../../server/context';
import MockSiteDataSource from '../site/MockSiteDataSource';

let testServer: ApolloServer;
const clientIds: string[] = ['', ''];
const context = getDefaultContextProcessor(clientIds);

const QUERY_DEVICES = gql`
  query {
    devices(searchOptions: { batchSize: 10, offset: 10, sortOptions: { direction: DESC, field: "name" }, filter: "" }) {
      totalCount
      totalFilteredCount
      devices {
        uuid
        id
        name
        make
        model
        site
        section
        lastPayload
        operational
      }
    }
  }
`;

const QUERY_DEVICE = gql`
  query {
    device(id: "some-id") {
      uuid
      id
      name
      make
      model
      site
      section
      lastPayload
      operational
      firmware
      serialNumber
      points {
        id
        name
        value
        units
        state
      }
      validation
      level
      message
      details
      state
      errorsCount
      lastStateUpdated
      lastStateSaved
      lastTelemetryUpdated
      lastTelemetrySaved
    }
  }
`;

const QUERY_POINTS = gql`
  query {
    points(deviceId: "some-id") {
      id
      name
      value
      units
      state
    }
  }
`;

const QUERY_DEVICE_NAMES = gql`
  query {
    deviceNames
  }
`;

const QUERY_DEVICE_MAKES = gql`
  query {
    deviceMakes
  }
`;

const QUERY_DEVICE_MODELS = gql`
  query {
    deviceModels
  }
`;

const QUERY_SECTIONS = gql`
  query {
    sections
  }
`;

beforeAll(async () => {
  const dataSources = () => {
    return {
      deviceDS: new MockDeviceDataSource(),
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

describe('Devices', () => {
  test('devices', async () => {
    const result = await runQuery(QUERY_DEVICES);
    expect(result).toMatchSnapshot();
  });

  test('device', async () => {
    const result = await runQuery(QUERY_DEVICE);
    expect(result).toMatchSnapshot();
  });

  test('points', async () => {
    const result = await runQuery(QUERY_POINTS);
    expect(result).toMatchSnapshot();
  });

  test('getDeviceNames', async () => {
    const result = await runQuery(QUERY_DEVICE_NAMES);
    expect(result).toMatchSnapshot();
  });

  test('getDeviceMakes', async () => {
    const result = await runQuery(QUERY_DEVICE_MAKES);
    expect(result).toMatchSnapshot();
  });

  test('getDeviceModels', async () => {
    const result = await runQuery(QUERY_DEVICE_MODELS);
    expect(result).toMatchSnapshot();
  });

  test('getSections', async () => {
    const result = await runQuery(QUERY_SECTIONS);
    expect(result).toMatchSnapshot();
  });
});

async function runQuery(gql: DocumentNode, variables: object = {}): Promise<GraphQLResponse> {
  return await testServer.executeOperation({ query: gql, variables });
}
