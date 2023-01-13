import { AbstractPostgreSQLDAO } from '../../dao/postgresql/AbstracyPostgreSQLDAO';
import { DAO } from '../../dao/DAO';
import { getSiteDAO } from '../../site/SiteDAO';
import { Site } from '../../site/model/Site';

jest.mock('../../dao/postgresql/AbstracyPostgreSQLDAO');

describe('Site DAO', () => {
  const name: string = 'name';

  let siteDAO: DAO<Site>;

  beforeEach(async () => {
    jest.resetAllMocks();
    siteDAO = await getSiteDAO();
  });

  test('it calls the abstract insert', async () => {
    // arrange
    const insertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'insert');
    const deviceDoc: Site = { name };

    // act
    await siteDAO.insert(deviceDoc);

    // assert
    expect(insertSpy).toHaveBeenCalledWith(deviceDoc);
  });

  test('it calls the abstract upsert', async () => {
    // arrange
    const upsertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'upsert');
    const deviceDoc: Site = { name };
    const filter = { name };

    // act
    await siteDAO.upsert(deviceDoc, filter);

    // assert
    expect(upsertSpy).toHaveBeenCalledWith(deviceDoc, filter);
  });

  test('it calls the abstract get and returns undefined if a device is not found', async () => {
    // arrange
    const getSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'get');
    const filter: Site = { name };

    // act
    const result = await siteDAO.get(filter);

    // assert
    expect(getSpy).toHaveBeenCalledWith(filter);
    expect(result).toBe(undefined);
  });

  test('it calls the abstract get', async () => {
    // arrange
    const returnedDeviceDoc = { name, validation: null };
    const getSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'get').mockResolvedValueOnce(returnedDeviceDoc);
    const filter: Site = { name };

    // act
    const result = await siteDAO.get(filter);

    // assert
    expect(getSpy).toHaveBeenCalledWith(filter);
    expect(result).toEqual(returnedDeviceDoc);
  });
});
