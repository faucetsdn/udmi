import { Device } from './model/Device';
import { DAO } from '../dao/DAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';
import { Knex } from 'knex';
import { Point } from './model/Point';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';

const TABLE_NAME = 'devices';

export async function getDeviceDAO(): Promise<DAO<Device>> {
  return new PostgreSQLDAO(knexDb);
}

export class PostgreSQLDAO extends AbstractPostgreSQLDAO<Device> {
  constructor(db: Knex) {
    super(db, TABLE_NAME);
  }

  async insert(device: Device): Promise<void> {
    await super.insert(device);
  }

  async upsert(device: Device, primaryKeyFields: string[]): Promise<void> {
    // postgresql complains about a array of objects as a json object
    // so we'll replace it with a string representation of json which
    // postgres is happy with
    const deviceForPG = { ...device, points: JSON.stringify(device.points) };
    await super.upsert(deviceForPG, primaryKeyFields);
  }

  async get(device: Device): Promise<Device> {
    const deviceFromPg: any = await super.get(device);
    if (!deviceFromPg) {
      return null;
    }

    // postgresql is returning the points as an object that needs to be converted into a Point[]
    // we'll stringify the the object and then parse it into a Point[]
    const pointsAsString = JSON.stringify(deviceFromPg.points);
    const points: Point[] = JSON.parse(pointsAsString);

    return { ...deviceFromPg, points };
  }
}
