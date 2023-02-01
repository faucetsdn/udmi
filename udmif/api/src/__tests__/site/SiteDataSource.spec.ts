import { Site } from '../../site/model';
import { SiteDataSource } from '../../site/SiteDataSource';
import { DAO } from '../../dao/DAO';
import { ValidatedDistinctSearchOptions, ValidatedSearchOptions } from '../../common/model';

const getOne = jest.fn();
const getAllIn = jest.fn();

class TestDao implements DAO<Site> {
  async getAll(searchOptions: ValidatedSearchOptions): Promise<Site[]> {
    return [];
  }
  async getAllIn(field: string, value: readonly string[]): Promise<Site[]> {
    return getAllIn();
  }
  async getOne(filterQuery: any): Promise<Site> {
    return getOne();
  }
  async getFilteredCount(searchOptions: ValidatedSearchOptions): Promise<number> {
    return 1;
  }
  async getCount(): Promise<number> {
    return 1;
  }
  async getDistinct(field: string, searchOptions: ValidatedDistinctSearchOptions): Promise<string[]> {
    return [];
  }
}

describe('SiteDataSource', () => {
  let siteDataSource: SiteDataSource;

  beforeEach(() => {
    jest.restoreAllMocks();
    const siteDAO: DAO<Site> = new TestDao();
    siteDataSource = new SiteDataSource(siteDAO);
  });

  test('getSiteValidation returns null if site is not found', async () => {
    getAllIn.mockReturnValueOnce([]);
    await expect(siteDataSource.getSiteValidation('random')).resolves.toBe(null);
  });

  test('getSiteValidation returns validation object if site is found', async () => {
    const site: Site = {
      name: 'site1',
      validation: {
        last_updated: '',
        summary: {},
        devices: [],
      },
    };
    getAllIn.mockReturnValueOnce([site]);

    await expect(siteDataSource.getSiteValidation('site1')).resolves.toBe(site.validation);
  });
});
