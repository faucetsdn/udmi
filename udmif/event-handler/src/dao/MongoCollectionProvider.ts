import { Collection, Db, MongoClient, TimeSeriesCollectionOptions } from 'mongodb';

let mongoClient: MongoClient;

export async function getCollection<Type>(collectionName: string): Promise<Collection<Type>> {
  console.log(`Getting the Mongo Collection collection: ${collectionName}`);
  const db: Db = await getMongoDb();
  return db.collection<Type>(collectionName);
}

export async function getTimeSeriesCollection<Type>(
  collectionName: string,
  options: TimeSeriesCollectionOptions
): Promise<Collection<Type>> {
  const db: Db = await getMongoDb();
  await createTimeSeriesCollection<Type>(db, collectionName, options);
  console.log(`Getting the Mongo Collection collection: ${collectionName}`);
  return db.collection<Type>(collectionName);
}

async function createTimeSeriesCollection<Type>(
  db: Db,
  collectionName: string,
  options?: TimeSeriesCollectionOptions
): Promise<void> {
  const YEAR_IN_SECONDS: number = 31536000;
  const collections: Collection[] = await db.collections({ nameOnly: true });
  if (!collections.find((collection) => collection.collectionName === collectionName)) {
    console.log(`Creating the ${collectionName} collection.`);
    await db.createCollection<Type>(collectionName, { timeseries: options, expireAfterSeconds: YEAR_IN_SECONDS });
  }
}

async function getMongoDb(): Promise<Db> {
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
