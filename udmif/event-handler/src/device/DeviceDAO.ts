import { Device } from './model/Device';
import { Validation } from '../model/Validation';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';
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
    // replace the incoming points with the convert to json version
    const deviceForPG = { ...device, points: JSON.stringify(device.points) };
    await super.upsert(deviceForPG, primaryKeyFields);
  }

  async get(filterQuery: Device): Promise<Device> {
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
