import { Device, DevicesResponse, SearchOptions, SORT_DIRECTION } from '../../device/model';
import { DeviceDataSource } from '../../device/DeviceDataSource';
import { createDevices, createSearchOptions } from './data';

describe('DeviceDataSource.getDevice()', () => {
  const deviceDS = new DeviceDataSource();
  const devices = createDevices(100);

  test('returns all devices matching the searchOptions', async () => {
    const searchOptions: SearchOptions = createSearchOptions();
    const expectedResponse = getDevicesResponse(10, 10);
    await expect(deviceDS.getDevices(searchOptions)).resolves.toEqual(expectedResponse);
  });

  function getDevicesResponse(count: number, offset: number): DevicesResponse {
    let expectedDevices = devices.sort(compare('name', SORT_DIRECTION.DESC));
    const filterDevices: Device[] = expectedDevices.slice(offset, offset + count);
    return { devices: filterDevices, totalCount: devices.length };
  }
});

function compare(field: string, direction: SORT_DIRECTION) {
  return function (a, b) {
    if (direction.toString() === 'ASC') {
      return a[field].localeCompare(b[field]);
    } else {
      return b[field].localeCompare(a[field]);
    }
  };
}
