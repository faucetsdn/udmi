import { DataSources } from 'apollo-server-core/dist/graphqlOptions';
import { DAO } from '../dao/DAO';
import { Device } from '../device/model';
import { Site } from '../site/model';
import { DeviceDataSource } from '../device/DeviceDataSource';
import { SiteDataSource } from '../site/SiteDataSource';

interface DS {
  deviceDS: DeviceDataSource;
  siteDS: SiteDataSource;
}

export interface ApolloContext {
  dataSources: DS;
}

export default function dataSources(deviceDAO: DAO<Device>, siteDAO: DAO<Site>): () => DataSources<DS> {
  return () => ({
    deviceDS: new DeviceDataSource(deviceDAO),
    siteDS: new SiteDataSource(siteDAO),
  });
}
