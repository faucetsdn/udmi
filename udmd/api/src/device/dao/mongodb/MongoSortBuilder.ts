import { SortOptions, SORT_DIRECTION } from '../../../device/model';

export function getSort(sortOptions: SortOptions): any {
  // default the mongo filters to an empty object, this can be passed into the db.collection(...).find() method without issues
  const mongoSort: any = {};

  if (sortOptions.direction.toString() === SORT_DIRECTION.ASC.toString()) {
    mongoSort[sortOptions.field] = 1;
    mongoSort['name'] = 1;
  } else {
    mongoSort[sortOptions.field] = -1;
    mongoSort['name'] = -1;
  }

  return mongoSort;
}
