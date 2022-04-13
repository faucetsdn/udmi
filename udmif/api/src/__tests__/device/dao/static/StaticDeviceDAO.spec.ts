import { Device, SearchOptions } from '../../../../device/model';
import { DeviceDAO } from '../../../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../../../device/dao/static/StaticDeviceDAO';
import { createSearchOptions } from '../../data';

const deviceDAO: DeviceDAO = new StaticDeviceDAO();

describe('StaticDeviceDAO.getDevices', () => {
  test('the number of Devices returned matches the batchSize submitted', async () => {
    const expectedDeviceCount = 10;
    const searchOptions: SearchOptions = createSearchOptions();

    const length: number = (await deviceDAO.getDevices(searchOptions)).length;
    await expect(length).toEqual(expectedDeviceCount);
  });

  test('the matching Devices start at the offset specified', async () => {
    const batchSize = 20;
    const offset = 20;
    let searchOptions: SearchOptions = createSearchOptions(200, 0);
    const allDevices: Device[] = await deviceDAO.getDevices(searchOptions);
    searchOptions = createSearchOptions(batchSize, offset);
    await expect(deviceDAO.getDevices(searchOptions)).resolves.toEqual(allDevices.slice(offset, offset + batchSize));
  });
});

describe('StaticDeviceDAO.getDeviceCount', () => {
  test('the total number of Devices is 100', async () => {
    await expect(deviceDAO.getDeviceCount()).resolves.toEqual(100);
  });
});
