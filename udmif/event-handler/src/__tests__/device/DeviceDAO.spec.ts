import { getDeviceDAO } from '../../device/DeviceDAO';
import { AbstractPostgreSQLDAO } from '../../dao/postgresql/AbstracyPostgreSQLDAO';
import { DAO } from '../../dao/DAO';
import { Device } from '../../device/model/Device';
import { Point } from '../../device/model/Point';

jest.mock('../../dao/postgresql/AbstracyPostgreSQLDAO');

describe('Device DAO', () => {
  const name: string = 'name';
  const id: string = 'id';
  const site: string = 'site';

  let deviceDAO: DAO<Device>;

  beforeEach(async () => {
    jest.resetAllMocks();
    deviceDAO = await getDeviceDAO();
  });

  test('it passes through the arguments to the abstract insert implementation', async () => {
    // arrange
    const insertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'insert');
    const deviceDoc: Device = { name, id, site };

    // act
    await deviceDAO.insert(deviceDoc);

    // assert
    expect(insertSpy).toHaveBeenCalledWith(deviceDoc);
  });

  test('it passes through the arguments to the abstract upsert implementation', async () => {
    // arrange
    const upsertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'upsert');
    const deviceDoc: Device = { name, id, site };
    const filter = { id, name };

    // act
    await deviceDAO.upsert(deviceDoc, filter);

    // assert
    expect(upsertSpy).toHaveBeenCalledWith(deviceDoc, filter);
  });

  test('it passes through the arguments to the abstract upsert implementation with the points converted to a json string', async () => {
    // arrange
    const upsertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'upsert');
    const points: Point[] = [];
    const deviceDoc: Device = { name, id, site, points };
    const filter = { id, name };

    const convertedDeviceDoc = { ...deviceDoc, points: '[]' };

    // act
    await deviceDAO.upsert(deviceDoc, filter);

    // assert
    expect(upsertSpy).toHaveBeenCalledWith(convertedDeviceDoc, filter);
  });

  test('it passes through the arguments to the abstract get and returs null if a device is not found', async () => {
    // arrange
    const getSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'get');
    const deviceDoc: Device = { name, id, site };

    // act
    const result = await deviceDAO.get(deviceDoc);

    // assert
    expect(getSpy).toHaveBeenCalledWith(deviceDoc);
    expect(result).toBe(null);
  });

  test('it passes through the arguments to the abstract and returns a Device object', async () => {
    // arrange
    const returnedDeviceDoc = { name, id, site, points: '[]', validation: '' };
    const getSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'get').mockResolvedValueOnce(returnedDeviceDoc);
    const deviceDoc: Device = { name, id, site };

    // act
    const result: Device = await deviceDAO.get(deviceDoc);

    // assert
    expect(getSpy).toHaveBeenCalledWith(deviceDoc);
    expect(result).toEqual({ ...deviceDoc, points: '[]', validation: '' });
  });
});
