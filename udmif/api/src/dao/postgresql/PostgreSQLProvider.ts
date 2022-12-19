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
  debug: true,
};

export const knexDb: Knex = knex(dbConfig);
