import { logger } from '../common/logger';
import { Collection, Db, MongoClient, MongoClientOptions } from 'mongodb';
import { Configuration } from '../server/config';

let mongoClient: MongoClient;

export async function getMongoCollection<Type>(
  collectionName: string,
  systemConfiguration: Configuration
): Promise<Collection<Type>> {
  console.log(`Getting the Mongo Collection collection: ${collectionName}`);
  const db: Db = await getMongoDb(systemConfiguration);
  return db.collection<Type>(collectionName);
}

// export async function getMongoDb(): Promise<Db> {
//   if (!mongoClient) {
//     mongoClient = await MongoClient.connect(getUri(), {});
//   }
//   return mongoClient.db(process.env.MONGO_DATABASE);
// }

// export function getUri(): string {
//   const protocol = process.env.MONGO_PROTOCOL;
//   const host = process.env.MONGO_HOST;
//   const userName = process.env.MONGO_USER;
//   const password = process.env.MONGO_PWD;

//   // get the uri
//   const credentials = userName ? `${userName}:${password}@` : '';
//   console.log(`Building a new Mongo Client uri: ${protocol}://*****:*****@${host}`);
//   return `${protocol}://${credentials}${host}`;
// }

export async function getMongoDb(systemConfiguration: Configuration): Promise<Db> {
  const uri = getUri(systemConfiguration);
  const host = systemConfiguration.mongoHost;

  try {
    const client: MongoClient = await MongoClient.connect(uri, getClientOptions());
    logger.info(`Connected to mongo host ${host}.`);
    return client.db(systemConfiguration.mongoDatabase);
  } catch (e) {
    logger.error(`Error connecting to mongo host ${host} - ${e}`);
    throw e;
  }
}

function getUri(systemConfiguration: Configuration): string {
  // extracting for readability
  const userName = systemConfiguration.mongoUsername;
  const password = systemConfiguration.mongoPassword;
  const host = systemConfiguration.mongoHost;
  const protocol = systemConfiguration.mongoProtocol;

  logger.info(`Attempting to connect to mongo as user: ${userName}`);

  // set up the uri
  const credentials = userName ? `${userName}:${password}@` : '';
  return `${protocol}://${credentials}${host}`;
}

/**
 * Get the options for the Mongo Client. Connections are managed by specifying the min and max number of connections in the connection pool.
 * If we need more flexibility in controlling the client, we can add configuration values to the environment and set them here.
 * @returns the Mongo Client Options
 */
function getClientOptions(): MongoClientOptions {
  return { minPoolSize: 1, maxPoolSize: 10 };
}
