import knex, { Knex } from 'knex';

const dbConfig: Knex.Config = {
  client: 'pg',
  connection: {
    host: process.env.POSTGRESQL_INSTANCE_HOST,
    port: parseInt(process.env.POSTGRESQL_PORT),
    user: process.env.POSTGRESQL_USER,
    password: process.env.POSTGRESQL_PASSWORD,
    database: process.env.POSTGRESQL_DATABASE,
  },
};

export const knexDb: Knex = knex(dbConfig);

export async function isConnectedToPostgreSQL(): Promise<boolean> {
  console.log('Testing connection to PostgreSQL...');

  return knexDb
    .raw('select 1 as result from devices')
    .then(() => {
      console.info('Connected to PostgreSQL');
      return true;
    })
    .catch((err) => {
      console.error(`Could not connect to PosgreSQL for the following reason: ${err}.`);
      return false;
    });
}
