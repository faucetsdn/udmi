import { GraphQLDataSource } from 'apollo-datasource-graphql/dist/GraphQLDataSource';
import { Site, SitesResponse } from './model';
import { DAO } from '../dao/DAO';
import { ValidatedDistinctSearchOptions, DistinctSearchOptions, ValidatedSearchOptions } from '../common/model';
import { validateSearchOptions, validateDistinctSearchOptions } from '../common/SearchOptionsValidator';
import { Device } from '../device/model';

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

  async getSites(searchOptions: ValidatedSearchOptions): Promise<SitesResponse> {
    const validatedSearchOptions: ValidatedSearchOptions = validateSearchOptions(searchOptions);

    const sites: Site[] = await this.siteDAO.getAll(validatedSearchOptions);
    const totalCount = await this.siteDAO.getCount();
    const totalFilteredCount: number = await this.siteDAO.getFilteredCount(validatedSearchOptions);

    return { sites, totalCount, totalFilteredCount };
  }

  async getSite(id: string): Promise<Site> {
    return this.siteDAO.getOne({ id });
  }

  async getCorrectDevicesCount(id: string): Promise<number> {
    return (await this.getSite(id)).validation.summary.correct_devices.length;
  }

  async getMissingDevicesCount(id: string): Promise<number> {
    return (await this.getSite(id)).validation.summary.missing_devices.length;
  }

  async getErrorDevicesCount(id: string): Promise<number> {
    return (await this.getSite(id)).validation.summary.error_devices.length;
  }

  async getExtraDevicesCount(id: string): Promise<number> {
    return (await this.getSite(id)).validation.summary.extra_devices.length;
  }

  async getLastValidated(id: string): Promise<string> {
    return (await this.getSite(id)).validation.last_updated;
  }

  async getPercentValidated(id: string, totalDevicesCount: number): Promise<number> {
    return (
      ((await this.getCorrectDevicesCount(id)) +
        (await this.getMissingDevicesCount(id)) +
        (await this.getErrorDevicesCount(id)) +
        (await this.getExtraDevicesCount(id))) /
      totalDevicesCount
    );
  }

  async getTotalDeviceErrorsCount(devices: Device[]): Promise<number> {
    return devices.reduce((n: number, device: Device) => {
      return n + (device.validation?.errors.length ?? 0);
    }, 0);
  }
}
