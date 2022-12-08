import { AbstractPostgreSQLDAO } from '../../dao/postgresql/AbstracyPostgreSQLDAO';
import { DAO } from '../../dao/DAO';
import { getSiteDAO } from '../../site/SiteDAO';
import { Site, SiteValidation } from '../../site/model/Site';
import { getSiteValidationDAO } from '../../site/SiteValidationDAO';

jest.mock('../../dao/postgresql/AbstracyPostgreSQLDAO');

describe('Site DAO', () => {
  const siteName: string = 'site-name';

  let siteDAO: DAO<SiteValidation>;

  beforeEach(async () => {
    jest.resetAllMocks();
    siteDAO = await getSiteValidationDAO();
  });

  test('it calls the abstract insert', async () => {
    // arrange
    const siteValidationDoc: SiteValidation = {
      siteName,
      timestamp: null,
      data: null,
    };
    const insertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'insert');

    // act
    await siteDAO.insert(siteValidationDoc);

    // assert
    expect(insertSpy).toHaveBeenCalledWith(siteValidationDoc);
  });

  test('it calls the abstract upsert', async () => {
    // arrange
    const upsertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'upsert');
    const siteValidationDoc: SiteValidation = {
      siteName,
      timestamp: null,
      data: null,
    };
    const filter = { siteName };

    // act
    await siteDAO.upsert(siteValidationDoc, filter);

    // assert
    expect(upsertSpy).toHaveBeenCalledWith(siteValidationDoc, filter);
  });

  test('it calls the abstract get', async () => {
    // arrange
    const returnedDoc = { siteName, validation: null };
    const getSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'get').mockResolvedValueOnce(returnedDoc);
    const filter = { siteName };

    // act
    const result = await siteDAO.get(filter);

    // assert
    expect(getSpy).toHaveBeenCalledWith(filter);
    expect(result).toEqual(returnedDoc);
  });
});
