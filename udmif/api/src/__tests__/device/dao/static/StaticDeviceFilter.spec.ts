import { filterDevices } from '../../../../device/dao/static/StaticDeviceFilter';
import { createDevices } from '../../data';

describe('StaticDeviceFilter.filterDevices', () => {
  const devices = createDevices(100);
  const AHU_NAME_FILTER = { field: 'name', operator: '=', value: 'AHU' };
  const AAA_MODEL_FILTER = { field: 'model', operator: '=', value: 'AAA' };

  test('returns all the records if no filter specified', () => {
    expect(filterDevices([], devices)).toEqual(devices);
  });

  test('returns only device records matching the filter', () => {
    const filters = [AHU_NAME_FILTER];
    const filteredDevices = filterDevices(filters, devices);

    expect(filteredDevices.length).toEqual(devices.length / 2);
    filteredDevices.forEach((device) => {
      expect(device.name.includes('AHU')).toEqual(true);
    });
  });

  test('handles multiple filters', () => {
    const filters = [AHU_NAME_FILTER, AAA_MODEL_FILTER];
    const filteredDevices = filterDevices(filters, devices);

    filteredDevices.forEach((device) => {
      expect(device.name.includes('AHU')).toEqual(true);
      expect(device.model.includes('AAA')).toEqual(true);
    });
  });
});
