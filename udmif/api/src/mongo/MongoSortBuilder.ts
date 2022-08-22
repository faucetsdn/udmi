import { SortOptions, SORT_DIRECTION } from '../device/model';

export function getSort(sortOptions: SortOptions): any {
  let direction = 1;

  if (SORT_DIRECTION.DESC === sortOptions.direction) {
    direction = -1;
  }

  return { [sortOptions.field]: direction, name: direction };
}
