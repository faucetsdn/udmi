import { Device } from './model/Device';
import { Validation } from '../model/Validation';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PgDaoProvider';
import { Knex } from 'knex';
import { Point } from './model/Point';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';

const TABLE_NAME = 'devices';

export async function getDeviceDAO(): Promise<DAO<Device>> {
  return new PostgreSQLDAO<Device>(knexDb);
}

export class PostgreSQLDAO<Device> extends AbstractPostgreSQLDAO<Device> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }

  async insert(device: Device): Promise<void> {
    await super.insert(device);
  }

  async upsert(device: any, primaryKeyFields: string[]): Promise<void> {
    // converting an array of points to a proper json object.
    const points: any = JSON.stringify(device.points);
    // replace the incoming points with the convererted version
    const deviceForPG = { ...device, points };
    await super.upsert(deviceForPG, primaryKeyFields);
  }

  async get(filterQuery: any): Promise<Device> {
    const deviceFromPg: any = await super.get(filterQuery);
    if (!deviceFromPg) {
      return null;
    }

    const pointsAsString = JSON.stringify(deviceFromPg.points);
    const points: Point[] = JSON.parse(pointsAsString);

    const validationAsString = JSON.stringify(deviceFromPg.validation);
    const validation: Validation = JSON.parse(validationAsString);

    return { ...deviceFromPg, points, validation };
  }
}
