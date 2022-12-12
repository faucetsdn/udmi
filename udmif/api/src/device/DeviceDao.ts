import { Device } from './model';
import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';

export class DeviceDAO extends AbstractPostgreSQLDAO<Device> {
  constructor() {
    super(knexDb, 'device');
    this.defaultOrder = { column: 'name', order: 'asc' };
  }
}
