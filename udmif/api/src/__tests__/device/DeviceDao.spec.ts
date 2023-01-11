import { DeviceDAO } from '../../device/DeviceDao';

describe('Device DAO', () => {
  let deviceDAO: DeviceDAO;

  beforeEach(() => {
    deviceDAO = new DeviceDAO();
  });

  test('the default sort order for a device query is by name', () => {
    expect(deviceDAO.defaultOrder).toEqual({ column: 'name', order: 'asc' });
  });
});
