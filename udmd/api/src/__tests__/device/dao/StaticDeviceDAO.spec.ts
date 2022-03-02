import { Device, SearchOptions } from '../../../device/model';
import { DeviceDAO } from '../../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../../device/dao/StaticDeviceDAO';
import { createSearchOptions } from '../data';

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

  test('that providing an offset greater than the number of devices throws an error', async () => {
    const batchSize = 20;
    const offset = 200;

    const searchOptions: SearchOptions = createSearchOptions(batchSize, offset);

    await expect(deviceDAO.getDevices(searchOptions)).rejects.toThrowError(
      'An invalid offset that is greater than the total number of devices was provided.'
    );
  });

  test('that if a batch size of 0 is provided, an error is thrown', async () => {
    const batchSize = 0;

    const searchOptions: SearchOptions = createSearchOptions(batchSize);

    await expect(deviceDAO.getDevices(searchOptions)).rejects.toThrowError(
      'A batch size greater than zero must be provided'
    );
  });
});

describe('StaticDeviceDAO.getDeviceCount', () => {
  test('the total number of Devices is 100', async () => {
    await expect(deviceDAO.getDeviceCount()).resolves.toEqual(100);
  });
});
