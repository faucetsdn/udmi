import { SiteValidation } from './model/Site';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';
import { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';

const TABLE_NAME: string = 'site_validations';

export async function getSiteValidationDAO(): Promise<DAO<SiteValidation>> {
  return new PostgreSQLDAO(knexDb);
}

export class PostgreSQLDAO extends AbstractPostgreSQLDAO<SiteValidation> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }
}
