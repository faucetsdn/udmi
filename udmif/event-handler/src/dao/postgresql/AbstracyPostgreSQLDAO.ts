import { Knex } from 'knex';
import { DAO } from '../DAO';

export abstract class AbstractPostgreSQLDAO<Type> implements DAO<Type> {
  constructor(private db: Knex, private tableName: string) {}

  async insert(document: Type): Promise<void> {
    await this.getTable()
      .insert(document)
      .then((result) => {
        console.log('PostgreSQL Insert result: ' + JSON.stringify(result));
      });
  }

  async upsert(document: Type, primaryKeyFields: string[]): Promise<void> {
    await this.getTable()
      .insert(document)
      .onConflict(primaryKeyFields)
      .merge()
      .returning('*')
      .then((result) => {
        console.log('PostgreSQL Upsert result: ' + JSON.stringify(result));
      });
  }

  async get(filterQuery: Type): Promise<Type> {
    return this.getTable()
      .where(filterQuery)
      .first()
      .then((row) => row);
  }

  private getTable(): Knex.QueryBuilder {
    return this.db(this.tableName);
  }
}
