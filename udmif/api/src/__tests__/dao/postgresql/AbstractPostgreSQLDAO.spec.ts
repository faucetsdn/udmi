import knex, { Knex } from 'knex';
import { Order } from '../../../dao/postgresql/OrderBy';
import { ValidatedSearchOptions, SORT_DIRECTION, Filter, ValidatedDistinctSearchOptions } from '../../../common/model';
import { AbstractPostgreSQLDAO } from '../../../dao/postgresql/AbstracyPostgreSQLDAO';

class TestClass extends AbstractPostgreSQLDAO<any> {
  constructor(db: Knex) {
    super(db, 'any');
  }
}

class TestClassWithDefaultOrder extends AbstractPostgreSQLDAO<any> {
  defaultOrder: Order;

  constructor(db: Knex) {
    super(db, 'any');
    this.defaultOrder = {
      column: 'name',
      order: 'desc',
    };
  }
}

let testClass: TestClass;

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

    testClass = new TestClass(db);
  });
});

afterEach(() => {
  db.destroy();
});

describe('getAll', () => {
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

  test('we can get pages with an offset', async () => {
    const records = createRecords(100);
    await db('any').insert(records);

    const offset = 15;
    const batchSize = 10;

    const searchOptions: ValidatedSearchOptions = {
      offset,
      batchSize,
    };

    const response = await testClass.getAll(searchOptions);

    // make this an array of id, where the id's increment numerically
    let responseIndexes = response.map((value) => value.id);

    // create an array of expected indexes using the offset
    let expectedIndexes = [...Array(10)].map((_, i) => i + 1 + offset);

    expect(responseIndexes).toEqual(expectedIndexes);
  });

  test('a default order is applied', async () => {
    const records = createRecords(3);

    await db('any').insert(records);
    const daoWithDefaultOrder = new TestClassWithDefaultOrder(db);

    const result = await daoWithDefaultOrder.getAll({ batchSize: 10, offset: 0 });

    expect(result).toEqual([records[2], records[1], records[0]]);
  });
});

describe('getOne', () => {
  test('returns null if record not found', async () => {
    // arrange
    const records = createRecords(6);
    await db('any').insert(records);

    // act/assert
    await expect(testClass.getOne({ name: 'name1' })).resolves.toEqual(undefined);
  });

  test('we can retrieve a single record', async () => {
    // arrange
    const records = createRecords(6);
    await db('any').insert(records);

    // act/assert
    await expect(testClass.getOne({ name: 'name-1' })).resolves.toEqual(records[0]);
  });
});

describe('count', () => {
  test('returns count of records', async () => {
    // arrange
    const records = createRecords(55);
    await db('any').insert(records);

    // act/assert
    await expect(testClass.getCount()).resolves.toEqual(55);
  });
});

describe('getFilteredCount', () => {
  test('returns count of filtered records', async () => {
    // arrange
    const records = createRecords(60);
    await db('any').insert(records);

    const filter: string = JSON.stringify(<Filter[]>[{ field: 'site', operator: '=', value: 'site-1' }]);

    const searchOptions: ValidatedSearchOptions = {
      offset: 0,
      filter,
    };

    // act/assert
    await expect(testClass.getFilteredCount(searchOptions)).resolves.toEqual(20);
  });
});

describe('getDistinct', () => {
  test('returns count of distinct records by field with no filter', async () => {
    // arrange
    const records = createRecords(60);
    await db('any').insert(records);

    const searchOptions: ValidatedDistinctSearchOptions = {
      filter: '',
      limit: 20,
    };

    // act/assert
    await expect(testClass.getDistinct('site', searchOptions)).resolves.toEqual(
      expect.arrayContaining(['site-1', 'site-2', 'site-3'])
    );
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
