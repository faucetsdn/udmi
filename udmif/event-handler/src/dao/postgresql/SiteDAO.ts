import { Site } from '../../site/model/Site';
import { DAO } from '../DAO';
import { knexDb } from './PgDaoProvider';
import { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from './AbstracyPostgreSQLDAO';

const TABLE_NAME: string = 'sites';

export async function getSiteDAO(): Promise<DAO<Site>> {
  return new PostgreSQLDAO<Site>(knexDb);
}

export class PostgreSQLDAO<Site> extends AbstractPostgreSQLDAO<Site> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }
}
