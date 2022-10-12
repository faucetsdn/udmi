import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Site, SitesResponse } from './model';
import { DAO } from '../dao/DAO';
import {
  ValidatedDistinctSearchOptions,
  DistinctSearchOptions,
  ValidatedSearchOptions,
  SearchOptions,
} from '../common/model';
import { validateSearchOptions, validateDistinctSearchOptions } from '../common/SearchOptionsValidator';

export class SiteDataSource extends GraphQLDataSource {
  constructor(private siteDAO: DAO<Site>) {
    super();
  }

  public initialize(config): void {
    super.initialize(config);
  }

  async getSiteNames(searchOptions?: DistinctSearchOptions): Promise<string[]> {
    const validatedSearchOptions: ValidatedDistinctSearchOptions = validateDistinctSearchOptions(searchOptions);
    return this.siteDAO.getDistinct('name', validatedSearchOptions);
  }

  async getSites(searchOptions?: SearchOptions): Promise<SitesResponse> {
    const validatedSearchOptions: ValidatedSearchOptions = validateSearchOptions(searchOptions);

    const sites: Site[] = await this.siteDAO.getAll(validatedSearchOptions);
    const totalCount = await this.siteDAO.getCount();
    const totalFilteredCount: number = await this.siteDAO.getFilteredCount(validatedSearchOptions);

    return { sites, totalCount, totalFilteredCount };
  }

  async getSite(name: string): Promise<Site> {
    return this.siteDAO.getOne({ name });
  }
}
