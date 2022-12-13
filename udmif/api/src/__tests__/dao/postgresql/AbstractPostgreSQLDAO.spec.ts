import knex, { Knex } from 'knex';
import { ValidatedSearchOptions, SORT_DIRECTION, Filter } from '../../../common/model';
import { AbstractPostgreSQLDAO } from '../../../dao/postgresql/AbstracyPostgreSQLDAO';

class TestClass extends AbstractPostgreSQLDAO<any> {
  constructor(db: Knex) {
    super(db, 'any');
  }
}

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

afterEach(() => {
  db.destroy();
});

describe('AbstractPostgreSQLDAO.insert', () => {
  let testClass: TestClass;

  beforeEach(async () => {
    testClass = new TestClass(db);
  });

  test('we can limit the number of records retrieved', async () => {
    await db('any').insert(createRecords(3));

    const result = await testClass.getAll({ batchSize: 1, offset: 0 });

    expect(result).toEqual([getRecord(1)]);
  });

  test('we can sort the records retrieved', async () => {
    // arrange
    const records = createRecords(3);
    await db('any').insert(records);

    const searchOptions: ValidatedSearchOptions = {
      batchSize: 3,
      offset: 0,
      sortOptions: {
        direction: SORT_DIRECTION.ASC,
        field: 'name',
      },
    };

    // act/assert
    await expect(testClass.getAll(searchOptions)).resolves.toEqual(records);
  });

  test('we can filter records using like', async () => {
    // arrange
    const records = createRecords(6);
    await db('any').insert(records);

    const filter: string = JSON.stringify(<Filter[]>[{ field: 'make', operator: '~', value: 'x' }]);

    const searchOptions: ValidatedSearchOptions = {
      offset: 0,
      filter,
    };

    // act/assert
    await expect(testClass.getAll(searchOptions)).resolves.toEqual([records[1], records[3], records[5]]);
  });

  test('we can filter records using in', async () => {
    // arrange
    const records = createRecords(6);
    await db('any').insert(records);

    const filter: string = JSON.stringify(<Filter[]>[{ field: 'site', operator: '=', value: 'site-2' }]);

    const searchOptions: ValidatedSearchOptions = {
      offset: 0,
      filter,
    };

    // act/assert
    await expect(testClass.getAll(searchOptions)).resolves.toEqual([records[0], records[3]]);
  });

  test('there is no default sort', async () => {
    // act/assert
    expect(testClass.defaultOrder).toEqual(undefined);
  });
});

function createRecords(numberOfRecords: number) {
  return [...Array(numberOfRecords)].map((_, i) => getRecord(i + 1));
}

function getRecord(number: number): any {
  const section = number % 2 === 0 ? 'upper-section' : 'lower-section';
  const make = number % 2 === 0 ? 'make-x' : 'make-y';
  const model = number % 2 === 0 ? 'model-x' : 'model-y';
  const site = `site-${(number % 3) + 1}`;
  return { id: number, name: `name-${number}`, site, model, section, make };
}
