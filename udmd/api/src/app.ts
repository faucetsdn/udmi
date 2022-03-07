import { ApolloServer } from 'apollo-server';
import dataSources from './server/datasources';
import { typeDefs } from './server/schema';
import { resolvers } from './device/resolvers';
import { logger } from './common/logger';
import { getDefaultContextProcessor } from './server/context';
import { DeviceDAO } from './device/dao/DeviceDAO';
import { Configuration, loadConfig, logConfig } from './server/config';
import { getDeviceDAO } from './device/dao/DeviceDAOFactory';

(async () => {
  // load the configuration from the .env
  const config: Configuration = loadConfig();
  logConfig();

  // required context processor
  const context = await getDefaultContextProcessor();

  const deviceDAO: DeviceDAO = await getDeviceDAO(config);

  // server initialization
  const server: ApolloServer = new ApolloServer({
    typeDefs,
    resolvers,
    context,
    dataSources: dataSources(deviceDAO),
  });

  // start our server
  server.listen(4300).then(({ url }) => {
    logger.info(`🎙  Universal Device Management Dashboard API is ready to talk: ${url}`);
  });
})().catch((err) => logger.error(err.stack));
