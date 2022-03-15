import { Device, DevicesResponse, SearchOptions } from '../../device/model';
import { DeviceDataSource } from '../../device/DeviceDataSource';
import { createSearchOptions } from './data';
import { DeviceDAO } from '../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../device/dao/static/StaticDeviceDAO';

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

  test('returns a total filtered count', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    await expect(result.totalFilteredCount).not.toBe(0);
  });

  test('returns a device when queried by id', async () => {
    const devices: Device[] = await deviceDAO.getDevices({ batchSize: 10, offset: 0 });

    devices.forEach(async (device) => {
      const result: Device = await deviceDS.getDevice(device.id);
      expect(result.id).toBe(device.id);
    });
  });

  test('returns null if the device could not be found', async () => {
    const result: Device = await deviceDS.getDevice('random-id');
    expect(result).toBe(null);
  });
});
