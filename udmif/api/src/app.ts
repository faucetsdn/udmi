import { ApolloServer } from 'apollo-server';
import dataSources from './server/datasources';
import { typeDefs } from './server/schema';
import { resolvers as commonResolvers } from './common/resolvers';
import { resolvers as deviceResolvers } from './device/resolvers';
import { resolvers as siteResolvers } from './site/resolvers';
import { logger } from './common/logger';
import { getDefaultContextProcessor } from './server/context';
import { Configuration, loadConfig } from './server/config';
import { DAO, getDeviceDAO, getSiteDAO } from './dao/DAO';
import { Device } from './device/model';
import { Site } from './site/model';
import { merge } from 'lodash';

(async () => {
  // load the configuration from the .env
  const config: Configuration = loadConfig();

  // required context processor
  const context = await getDefaultContextProcessor(config.clientIds);

  const deviceDAO: DAO<Device> = await getDeviceDAO(config);
  const siteDAO: DAO<Site> = await getSiteDAO(config);

  // server initialization
  const server: ApolloServer = new ApolloServer({
    typeDefs,
    resolvers: merge(commonResolvers, deviceResolvers, siteResolvers),
    context,
    dataSources: dataSources(deviceDAO, siteDAO),
  });

  // start our server
  server.listen(4300).then(({ url }) => {
    logger.info(`🎙  Universal Device Management Dashboard API is ready to talk: ${url}`);
  });
})().catch((err) => logger.error(err.stack));
