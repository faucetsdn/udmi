import { Device, DevicesResponse, SearchOptions, SORT_DIRECTION } from '../../device/model';
import { DeviceDataSource } from '../../device/DeviceDataSource';
import { createDevices, createSearchOptions } from './data';

describe('DeviceDataSource.getDevice()', () => {
  const deviceDS = new DeviceDataSource();
  const devices = createDevices(100);

  test('returns all devices batch of devices', async () => {
    const searchOptions: SearchOptions = createSearchOptions();
    const expectedResponse = getDevicesResponse(10, 10);
    await expect(deviceDS.getDevices(searchOptions)).resolves.toEqual(expectedResponse);
  });

  function getDevicesResponse(count: number, offset: number): DevicesResponse {
    const filterDevices: Device[] = devices.slice(count, count + offset);
    return { devices: filterDevices, totalCount: devices.length };
  }
});
