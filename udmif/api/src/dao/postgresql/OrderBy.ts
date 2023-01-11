import { SortOptions } from '../../common/model';

export interface Order {
  column: string;
  order: string;
}

export function getOrderByOptions(sortOptions: SortOptions): Order[] {
  let orderOptions: any[] = [];

  if (sortOptions) {
    let order = sortOptions.direction.valueOf() === 1 ? 'desc' : 'asc';
    orderOptions.push({ column: sortOptions.field, order });
  }

  return orderOptions;
}
