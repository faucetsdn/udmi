import { Filter } from './model';

export function fromString(filter: string): Filter[] {
  return JSON.parse(filter);
}
