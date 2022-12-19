import { Filter } from '../../../common/model';
import { getWhereOption, getWhereOptions } from '../../../dao/postgresql/Where';
import knex, { Knex } from 'knex';

let db: Knex;
beforeEach(async () => {
  db = knex({
    client: 'sqlite3',
    connection: {
      filename: ':memory:',
    },
  });

  await db.schema.createTable('any', function (table) {
    table.integer('id');
    table.string('name', 255).nullable();
    table.string('make', 255).nullable();
    table.string('model', 255).nullable();
    table.string('site', 255).nullable();
    table.string('section', 255).nullable();
    table.unique(['id']);
  });
});

describe('p', () => {
  test('is creates an empty where statement', () => {
    const result = getWhereOptions(null);
    expect(result).toEqual([]);
  });

  test('is creates an empty where statement', () => {
    const filter: Filter[] = [];
    const result = getWhereOptions(JSON.stringify(filter));
    expect(result).toEqual([]);
  });

  test('it creates a like statement with a single match', () => {
    const filter: Filter[] = [{ field: 'name', operator: '~', value: 'test1' }];
    const result = getWhereOptions(JSON.stringify(filter));
    expect(result).toEqual([{ field: 'name', operator: 'like', values: '%test1%' }]);
  });

  test('it creates an equals statement with a single match', () => {
    const filter: Filter[] = [{ field: 'site', operator: '=', value: 'site1' }];
    const result = getWhereOptions(JSON.stringify(filter));
    expect(result).toEqual([{ field: 'site', operator: '=', values: 'site1' }]);
  });

  test('is creates an populated where statement', () => {
    const filter: Filter[] = [
      { field: 'name', operator: '~', value: 'test1' },
      { field: 'site', operator: '~', value: 'test2' },
      { field: 'site', operator: '=', value: 'site1' },
      { field: 'site', operator: '=', value: 'site2' },
    ];

    console.log(JSON.stringify(filter));
    const result = getWhereOptions(JSON.stringify(filter));
    expect(result).toEqual([
      { field: 'name', operator: 'like', values: '%test1%' },
      { field: 'site', operator: 'like', values: '%test2%' },
      { field: 'site', operator: 'in', values: ['site1', 'site2'] },
    ]);
  });

  test('is creates a complex where statement that includes AND and ORs', () => {
    const filter: Filter[] = [
      { field: 'name', operator: '~', value: 'test1' },
      { field: 'site', operator: '~', value: 'test2' },
      { field: 'site', operator: '=', value: 'site1' },
      { field: 'site', operator: '=', value: 'site2' },
    ];

    const result = getWhereOption(JSON.stringify(filter), db('some_table'));
    expect(result.toString()).toEqual(
      "select * from `some_table` where (`name` like '%test1%') and (`site` in ('site1', 'site2') or `site` like '%test2%')"
    );
  });

  test('is creates a complex where statement that includes ORs for the same site', () => {
    const filter: Filter[] = [
      { field: 'site', operator: '~', value: 'test2' },
      { field: 'site', operator: '~', value: 'test3' },
      { field: 'site', operator: '=', value: 'site1' },
      { field: 'site', operator: '=', value: 'site2' },
    ];

    const result = getWhereOption(JSON.stringify(filter), db('some_table'));
    expect(result.toString()).toEqual(
      "select * from `some_table` where (`site` in ('site1', 'site2') or `site` like '%test2%' or `site` like '%test3%')"
    );
  });

  test('is creates a complex where statement that includes AND and ORs', () => {
    const filter: Filter[] = [
      { field: 'site', operator: '~', value: 'test2' },
      { field: 'site', operator: '~', value: 'test3' },
      { field: 'site', operator: '~', value: 'site1' },
    ];

    const result = getWhereOption(JSON.stringify(filter), db('some_table'));
    expect(result.toString()).toEqual(
      "select * from `some_table` where (`site` like '%test2%' or `site` like '%test3%' or `site` like '%site1%')"
    );
  });
});
