import { SiteValidation } from '../../site/model/Site';
import { DAO } from '../DAO';
import { knexDb } from './PgDaoProvider';
import { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from './AbstracyPostgreSQLDAO';

const TABLE_NAME: string = 'site_validations';

export async function getSiteValidationDAO(): Promise<DAO<SiteValidation>> {
  return new PostgreSQLDAO<SiteValidation>(knexDb);
}

export class PostgreSQLDAO<SiteValidation> extends AbstractPostgreSQLDAO<SiteValidation> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }
}
