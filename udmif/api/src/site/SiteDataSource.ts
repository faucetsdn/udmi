import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { SearchOptions, Site, SitesResponse, SiteNamesSearchOptions, ValidatedSiteNamesSearchOptions } from './model';
import { validate, validateSiteNamesSearchOptions } from './SearchOptionsValidator';
import { DAO } from '../dao/DAO';

export class SiteDataSource extends GraphQLDataSource {
  constructor(private siteDAO: DAO<Site>) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getSiteNames(searchOptions?: SiteNamesSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedSiteNamesSearchOptions = validateSiteNamesSearchOptions(searchOptions);
    return this.siteDAO.getDistinct('name', validatedSearchOptions);
  }

  async getSites(searchOptions: SearchOptions): Promise<SitesResponse> {
    const validatedSearchOptions: SearchOptions = validate(searchOptions);

    const sites: Site[] = await this.siteDAO.getAll(validatedSearchOptions);
    const totalCount = await this.siteDAO.getCount();
    const totalFilteredCount: number = await this.siteDAO.getFilteredCount(validatedSearchOptions);

    return { sites, totalCount, totalFilteredCount };
  }
}
