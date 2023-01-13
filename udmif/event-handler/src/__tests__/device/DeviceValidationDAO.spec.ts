import { AbstractPostgreSQLDAO } from '../../dao/postgresql/AbstracyPostgreSQLDAO';
import { DAO } from '../../dao/DAO';
import { Device, DeviceKey, DeviceValidation } from '../../device/model/Device';
import { Point } from '../../device/model/Point';
import { getDeviceValidationDAO } from '../../device/DeviceValidationDAO';

jest.mock('../../dao/postgresql/AbstracyPostgreSQLDAO');

describe('Device Validation DAO', () => {
  const name: string = 'name';
  const id: string = 'id';
  const site: string = 'site';

  let deviceValidationDAO: DAO<DeviceValidation>;

  beforeEach(async () => {
    jest.resetAllMocks();
    deviceValidationDAO = await getDeviceValidationDAO();
  });

  test('it calls the abstract insert and strips out any added fields', async () => {
    // arrange
    const insertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'insert');

    const deviceKey: DeviceKey = { name: '', site: '' };
    const timestamp: Date = null;
    const message: any = null;
    const deviceValidationDocument = { deviceKey, timestamp, message, _id: '' };
    const cleanedDocument = { deviceKey, timestamp, message };

    // act
    await deviceValidationDAO.insert(deviceValidationDocument);

    // assert
    expect(insertSpy).toHaveBeenCalledWith(cleanedDocument);
  });

  test('it calls the abstract upsert and strips out any added fields', async () => {
    // arrange
    const upsertSpy = jest.spyOn(AbstractPostgreSQLDAO.prototype, 'upsert');
    const deviceKey: DeviceKey = { name: '', site: '' };
    const timestamp: Date = null;
    const message: any = null;
    const deviceValidationDocument = { deviceKey, timestamp, message, _id: '' };
    const cleanedDocument = { deviceKey, timestamp, message };

    // act
    await deviceValidationDAO.upsert(deviceValidationDocument, []);

    // assert
    expect(upsertSpy).toHaveBeenCalledWith(cleanedDocument, []);
  });
});
