import { Pipe, PipeTransform } from '@angular/core';
import { orderBy } from 'lodash-es';

@Pipe({
  name: 'orderBy',
})
export class OrderByPipe implements PipeTransform {
  transform(value?: any[] | null, sortBy?: string | null, order?: 'asc' | 'desc' | null): any[] {
    const sortOrder = order ? order : 'asc'; // setting default ascending order

    return orderBy(value, [sortBy], [sortOrder]);
  }
}
