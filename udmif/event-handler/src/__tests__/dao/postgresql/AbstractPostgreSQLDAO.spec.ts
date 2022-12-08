import knex, { Knex } from 'knex';
import { AbstractPostgreSQLDAO } from '../../../dao/postgresql/AbstracyPostgreSQLDAO';

class TestClass extends AbstractPostgreSQLDAO<any> {
  constructor(db: Knex) {
    super(db, 'any');
  }
}

describe('AbstractPostgreSQLDAO.insert', () => {
  let testClass: TestClass;

  beforeEach(async () => {
    const db = knex({
      client: 'sqlite3',
      connection: {
        filename: ':memory:',
      },
    });

    await db.schema.createTable('any', function (table) {
      table.string('id', 255);
      table.string('name', 255);
      table.string('value', 255);
      table.unique(['id']);
    });
    testClass = new TestClass(db);
  });

  test('we can get an inserted value', async () => {
    // arrange
    const object = { id: 'some-id', name: 'name1' };
    await testClass.insert(object);

    // act
    const result = await testClass.get({ id: 'some-id' });

    // assert
    expect(result).toEqual({ ...object, value: null });
  });

  test('we can get an upserted value', async () => {
    // arrange
    const object1 = { id: 'some-id', name: 'name1' };
    const object2 = { id: 'some-id', name: 'name1', value: 'some-value' };
    await testClass.insert(object1);

    // act
    await testClass.upsert(object2, ['id']);

    // assert
    const result = await testClass.get({ id: 'some-id' });
    expect(result).toEqual(object2);
  });

  test('get with value that does not exist returns null', async () => {
    // arramge
    const object = { id: 'some-id', name: 'name1' };
    await testClass.insert(object);

    // act/assert
    await expect(testClass.get({ id: 'random-id' })).resolves.toEqual(undefined);
  });
});
