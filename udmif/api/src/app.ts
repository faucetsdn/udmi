import { ApolloServer } from 'apollo-server';
import dataSources from './server/datasources';
import { typeDefs } from './server/schema';
import { logger } from './common/logger';
import { getDefaultContextProcessor } from './server/context';
import { Configuration, loadConfig } from './server/config';
import { DAO } from './dao/DAO';
// import { getDeviceDAO, getSiteDAO } from './dao/MongoDAO';
import { DeviceDAO } from './device/DeviceDao';
import { SiteDAO } from './site/SiteDao';
import { Device } from './device/model';
import { Site } from './site/model';
import { resolvers } from './server/resolvers';

(async () => {
  // load the configuration from the .env
  const config: Configuration = loadConfig();

  // required context processor
  const context = await getDefaultContextProcessor(config.clientIds);

  const deviceDAO: DAO<Device> = new DeviceDAO();
  const siteDAO: DAO<Site> = new SiteDAO();

  // server initialization
  const server: ApolloServer = new ApolloServer({
    typeDefs,
    resolvers,
    context,
    dataSources: dataSources(deviceDAO, siteDAO),
  });

  // start our server
  server.listen(4300).then(({ url }) => {
    logger.info(`🎙  Universal Device Management Dashboard API is ready to talk: ${url}`);
  });
})().catch((err) => logger.error(err.stack));
