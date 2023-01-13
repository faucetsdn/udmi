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
  pool: {
    min: 0,
    max: 50,
    createTimeoutMillis: 20000,
    acquireTimeoutMillis: 20000,
    idleTimeoutMillis: 3000,
    reapIntervalMillis: 1000,
    createRetryIntervalMillis: 100,
  },
};

export const knexDb: Knex = knex(dbConfig);
