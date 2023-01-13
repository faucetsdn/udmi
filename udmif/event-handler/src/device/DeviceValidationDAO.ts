import { DeviceValidation } from './model/Device';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';
import { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';

const TABLE_NAME = 'device_validations';

export async function getDeviceValidationDAO(): Promise<DAO<DeviceValidation>> {
  return new PostgreSQLDAO(knexDb);
}

export class PostgreSQLDAO extends AbstractPostgreSQLDAO<DeviceValidation> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }

  async insert(document: any): Promise<void> {
    delete document._id;
    await super.insert(document);
  }
  async upsert(document: any, primaryKeyFields: string[]): Promise<void> {
    delete document._id;
    await super.upsert(document, primaryKeyFields);
  }
}
