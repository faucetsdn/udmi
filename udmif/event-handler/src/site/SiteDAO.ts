import { Site } from './model/Site';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';
import { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';

const TABLE_NAME: string = 'sites';

export async function getSiteDAO(): Promise<DAO<Site>> {
  return new PostgreSQLDAO(knexDb);
}

export class PostgreSQLDAO extends AbstractPostgreSQLDAO<Site> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }
}
