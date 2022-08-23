import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { SearchOptions } from '../../common/model';
import { Site, SitesResponse } from '../../site/model';
import { createSites } from './data';

export default class MockSiteDataSource extends GraphQLDataSource<object> {
  constructor() {
    super();
  }

  public initialize(config) {
    super.initialize(config);
  }

  async getSites(searchOptions: SearchOptions): Promise<SitesResponse> {
    const sites: Site[] = createSites(30);
    return { sites, totalCount: 30, totalFilteredCount: 10 };
  }

  async getSiteNames(): Promise<string[]> {
    return createSites(10)
      .map((d) => d.name)
      .sort();
  }
}
