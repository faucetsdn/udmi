import { StaticDeviceDAO } from '../../device/dao/StaticDeviceDAO';
import dataSources from '../../server/datasources';

describe('datasource.datasource', () => {
  test('returns a datasource function which can be called at a later time', () => {
    expect(dataSources(new StaticDeviceDAO())).toBeTruthy();
  });

  test('calling the datasource function returns an object that has a DeviceDataSource', () => {
    const ds = dataSources(new StaticDeviceDAO());
    expect(ds().deviceDS).toBeTruthy();
  });
});
