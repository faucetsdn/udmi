import { DevicesResponse, SearchOptions } from '../../device/model';
import { DeviceDataSource } from '../../device/DeviceDataSource';
import { createSearchOptions } from './data';
import { DeviceDAO } from '../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../device/dao/StaticDeviceDAO';

const deviceDAO: DeviceDAO = new StaticDeviceDAO();

describe('DeviceDataSource.getDevice()', () => {
  const deviceDS = new DeviceDataSource(deviceDAO);
  const searchOptions: SearchOptions = createSearchOptions();

  test('returns a set of devices', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    await expect(result.devices).not.toBe([]);
  });

  test('returns a total count', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    await expect(result.totalCount).not.toBe(0);
  });
});
