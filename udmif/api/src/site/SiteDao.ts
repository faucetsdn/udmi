import { AbstractPostgreSQLDAO } from '../dao/postgresql/AbstracyPostgreSQLDAO';
import { knexDb } from '../dao/postgresql/PostgreSQLProvider';
import { Site } from './model';

export class SiteDAO extends AbstractPostgreSQLDAO<Site> {
  constructor() {
    super(knexDb, 'sites');
    this.defaultOrder = { column: 'name', order: 'asc' };
  }
}
