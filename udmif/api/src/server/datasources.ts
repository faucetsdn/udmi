import { DataSources } from 'apollo-server-core/dist/graphqlOptions';
import { DAO } from '../dao/DAO';
import { Device } from '../device/model';
import { Site } from '../site/model';
import { DeviceDataSource } from '../device/DeviceDataSource';
import { SiteDataSource } from '../site/SiteDataSource';

export default function dataSources(deviceDAO: DAO<Device>, siteDAO: DAO<Site>): () => DataSources<object> {
  return () => ({
    deviceDS: new DeviceDataSource(deviceDAO),
    siteDS: new SiteDataSource(siteDAO),
  });
}
