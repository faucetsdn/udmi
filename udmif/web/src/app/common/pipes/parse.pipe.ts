import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'parse',
})
export class ParsePipe implements PipeTransform {
  transform(value?: string | null): any {
    if (value) return JSON.parse(value);
  }
}
