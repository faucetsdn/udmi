import { Device, DevicesResponse, Point, SearchOptions, SitesResponse } from '../../device/model';
import { DeviceDataSource } from '../../device/DeviceDataSource';
import { createSearchOptions } from './data';
import { DeviceDAO } from '../../device/dao/DeviceDAO';
import { StaticDeviceDAO } from '../../device/dao/static/StaticDeviceDAO';

const deviceDAO: DeviceDAO = new StaticDeviceDAO();
const deviceDS = new DeviceDataSource(deviceDAO);

describe('DeviceDataSource.getDevices()', () => {
  const searchOptions: SearchOptions = createSearchOptions();

  test('returns a set of devices', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    expect(result.devices).not.toBe([]);
  });

  test('returns a total count', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    expect(result.totalCount).not.toBe(0);
  });

  test('returns a total filtered count', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    expect(result.totalFilteredCount).not.toBe(0);
  });
});

describe('DeviceDataSource.getSites()', () => {
  const searchOptions: SearchOptions = createSearchOptions();

  test('returns a set of sites', async () => {
    const result: SitesResponse = await deviceDS.getSites(searchOptions);
    expect(result.sites).not.toBe([]);
  });

  test('returns a total count', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    expect(result.totalCount).not.toBe(0);
  });

  test('returns a total filtered count', async () => {
    const result: DevicesResponse = await deviceDS.getDevices(searchOptions);
    expect(result.totalFilteredCount).not.toBe(0);
  });
});

describe('DeviceDataSource.getDevice()', () => {
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

describe('DeviceDataSource.getPoints()', () => {
  test('returns a points when queried by device id', async () => {
    const devices: Device[] = await deviceDAO.getDevices({ batchSize: 10, offset: 0 });

    devices.forEach(async (device) => {
      const points: Point[] = await deviceDS.getPoints(device.id);
      expect(points).toEqual(device.points);
    });
  });

  test('returns [] if the device could not be found', async () => {
    const points: Point[] = await deviceDS.getPoints('random-id');
    expect(points).toEqual([]);
  });
});

describe('DeviceDataSource.getDeviceNames()', () => {
  test('returns device names', async () => {
    const deviceNames: string[] = await deviceDS.getDeviceNames();
    expect(deviceNames.length).toBeGreaterThanOrEqual(1);
  });

  test('returns [] if there are no matches', async () => {
    const deviceNames: string[] = await deviceDS.getDeviceNames({ search: 'lkjd' });
    expect(deviceNames).toEqual([]);
  });
});

describe('DeviceDataSource.getDeviceMakes()', () => {
  test('returns device makes', async () => {
    const deviceMakes: string[] = await deviceDS.getDeviceMakes();
    expect(deviceMakes.length).toBeGreaterThanOrEqual(1);
  });

  test('returns [] if there are no matches', async () => {
    const deviceMakes: string[] = await deviceDS.getDeviceMakes({ search: 'lkjd' });
    expect(deviceMakes).toEqual([]);
  });
});

describe('DeviceDataSource.getDeviceModels()', () => {
  test('returns device models', async () => {
    const deviceModels: string[] = await deviceDS.getDeviceModels();
    expect(deviceModels.length).toBeGreaterThanOrEqual(1);
  });

  test('returns [] if there are no matches', async () => {
    const deviceModels: string[] = await deviceDS.getDeviceModels({ search: 'lkjd' });
    expect(deviceModels).toEqual([]);
  });
});

describe('DeviceDataSource.getSiteNames()', () => {
  test('returns sites', async () => {
    const sites: string[] = await deviceDS.getSiteNames();
    expect(sites.length).toBeGreaterThanOrEqual(1);
  });

  test('returns [] if there are no matches', async () => {
    const sites: string[] = await deviceDS.getSiteNames({ search: 'lkjd' });
    expect(sites).toEqual([]);
  });
});

describe('DeviceDataSource.getSections()', () => {
  test('returns sections', async () => {
    const sections: string[] = await deviceDS.getSections();
    expect(sections.length).toBeGreaterThanOrEqual(1);
  });

  test('returns [] if there are no matches', async () => {
    const sections: string[] = await deviceDS.getSections({ search: 'lkjd' });
    expect(sections).toEqual([]);
  });
});
