import { SiteValidation } from './model/Site';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PgDaoProvider';
import { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';

const TABLE_NAME: string = 'site_validations';

export async function getSiteValidationDAO(): Promise<DAO<SiteValidation>> {
  return new PostgreSQLDAO<SiteValidation>(knexDb);
}

export class PostgreSQLDAO<SiteValidation> extends AbstractPostgreSQLDAO<SiteValidation> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }
}
