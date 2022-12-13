import { isConnectedToPostgreSQL, knexDb } from '../../../dao/postgresql/PostgreSQLProvider';

// hack to make raw a writeable property/function
Object.defineProperty(knexDb, 'raw', {
  value: jest.fn(),
  configurable: true,
  writable: true,
});

describe('PostgreSQLProvider.isConnectedToPostgreSQL', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  test('returns true if connection is up', async () => {
    jest.spyOn(knexDb, 'raw').mockResolvedValueOnce(true);
    await expect(isConnectedToPostgreSQL()).resolves.toEqual(true);
  });

  test('returns false if connection could not be made', async () => {
    jest.spyOn(knexDb, 'raw').mockRejectedValue('some error');
    await expect(isConnectedToPostgreSQL()).resolves.toEqual(false);
  });
});
