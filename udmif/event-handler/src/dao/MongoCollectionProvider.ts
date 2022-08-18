import { Collection, Db, MongoClient } from 'mongodb';

let mongoClient: MongoClient;

export async function getMongoCollection<Type>(collectionName: string): Promise<Collection<Type>> {
  console.log(`Getting the Mongo Collection collection: ${collectionName}`);
  const db: Db = await getMongoDb();
  return db.collection<Type>(collectionName);
}

export async function getMongoDb(): Promise<Db> {
  if (!mongoClient) {
    mongoClient = await MongoClient.connect(getUri(), {});
  }
  return mongoClient.db(process.env.MONGO_DATABASE);
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
