import { ApolloServer } from 'apollo-server';
import dataSources from './server/datasources';
import { typeDefs } from './server/schema';
import { resolvers } from './device/resolvers';
import { logger } from './common/logger';
import { getDefaultContextProcessor } from './common/context';

(async () => {
  // required context processor
  const context = await getDefaultContextProcessor();

  // server initialization
  const server: ApolloServer = new ApolloServer({
    typeDefs,
    resolvers,
    context,
    dataSources: dataSources(),
  });

  // start our server
  server.listen(4300).then(({ url }) => {
    logger.info(`🎙  Universal Device Management Dashboard API is ready to talk: ${url}`);
  });
})().catch((err) => logger.error(err.stack));
