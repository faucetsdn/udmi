import { Knex } from 'knex';
import { DAO } from '../DAO';

export abstract class AbstractPostgreSQLDAO<TYPE> implements DAO<TYPE> {
  constructor(private db: Knex, private tableName: string) {}

  async insert(document: TYPE): Promise<void> {
    await this.getTable()
      .insert(document)
      .then((result) => {
        console.log('PostgreSQL Insert result: ' + JSON.stringify(result));
      });
  }

  async upsert(document: TYPE, primaryKeyFields: string[]): Promise<void> {
    await this.getTable()
      .insert(document)
      .onConflict(primaryKeyFields)
      .merge()
      .returning('*')
      .then((result) => {
        console.log('PostgreSQL Upsert result: ' + JSON.stringify(result));
      });
  }

  async get(filterQuery: any): Promise<TYPE> {
    return this.getTable()
      .where(filterQuery)
      .first()
      .then((row) => row);
  }

  private getTable() {
    return this.db(this.tableName);
  }
}
