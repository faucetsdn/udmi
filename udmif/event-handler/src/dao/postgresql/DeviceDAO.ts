import { Device } from '../../device/model/Device';
import { DAO } from '../DAO';
import { knexDb } from './PgDaoProvider';
import { Knex } from 'knex';
import { Point } from '../../device/model/Point';
import { AbstractPostgreSQLDAO } from './AbstracyPostgreSQLDAO';

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
    const points: any = JSON.stringify(device.points);
    const deviceForPG = { ...device, points };
    await super.upsert(deviceForPG, primaryKeyFields);
  }

  async get(filterQuery: any): Promise<Device> {
    console.log('Getting device from PostgreSQL');

    const deviceFromPg: any = await super.get(filterQuery);
    if (!deviceFromPg) {
      console.log('Could not find device from PostgreSQL');
      return null;
    }
    console.log('Device from PG without Transformation: \n' + JSON.stringify(deviceFromPg));

    const pointsAsString = JSON.stringify(deviceFromPg?.points);
    const points: Point[] = JSON.parse(pointsAsString);

    const validationAsString = JSON.stringify(deviceFromPg?.validation);
    const validation: Point[] = JSON.parse(validationAsString);

    return { ...deviceFromPg, points, validation };
  }
}
