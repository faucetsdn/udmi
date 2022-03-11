import { SortOptions, SORT_DIRECTION } from '../../../device/model';

export function getSort(sortOptions: SortOptions): any {
  let direction = 1;

  if ('DESC' === sortOptions.direction.toString()) {
    direction = -1;
  }

  return { [sortOptions.field]: direction, name: direction };
}
