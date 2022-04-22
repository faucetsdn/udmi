import { Pipe, PipeTransform } from '@angular/core';
import { startCase } from 'lodash-es';

@Pipe({
  name: 'capitalize',
})
export class CapitalizePipe implements PipeTransform {
  transform(value: string): string {
    return startCase(value);
  }
}
