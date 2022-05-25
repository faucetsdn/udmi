import { DeviceDao, DefaultDeviceDao } from './DeviceDao';
import { Collection, MongoClient } from 'mongodb';

const COLLECTION_NAME: string = 'device';

export async function getDeviceDAO(): Promise<DeviceDao> {
  return new DefaultDeviceDao(await getMongoCollection());
}

async function getMongoCollection(): Promise<Collection> {
  const client = await MongoClient.connect(getUri(), {});

  // get the collection
  const db = process.env.MONGO_DATABASE;
  console.log(`Getting the Mongo Collection for db: ${db} collection: ${COLLECTION_NAME}`);
  return client.db(db).collection(COLLECTION_NAME);
}

export function getUri(): string {
  const protocol = process.env.MONGO_PROTOCOL;
  const host = process.env.MONGO_HOST;
  const userName = process.env.MONGO_USER;
  const password = process.env.MONGO_PWD;

  // get the uri
  const credentials = userName ? `${userName}:${password}@` : '';
  console.log(`Building a new Mongo Client uri: ${protocol}://*****:*****@${host}`);
  return `${protocol}://${credentials}${host}`;
}
