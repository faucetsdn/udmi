import { Device } from '../../device/model';
import { DeviceDataSource } from '../../device/DeviceDataSource';

describe('DeviceDataSource.getDevice()', () => {
  const deviceDS = new DeviceDataSource();

  test('returns array of devices', async () => {
    const devices = createDevices(3);
    await expect(deviceDS.getDevices()).resolves.toEqual(devices);
  });

  function createDevices(count: number): Device[] {
    const devices = [];
    let n = 1;
    while (n <= count) {
      devices.push({ id: `id${n}`, name: `name${n}` });
      n++;
    }

    return devices;
  }
});
