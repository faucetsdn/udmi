import { EmptyObject } from '../app';

export type SearchFilterItem =
  | {
      field: string;
      operator: string;
      value: string;
    }
  | EmptyObject;

export interface ChipItem {
  label: string;
  value: string;
}
