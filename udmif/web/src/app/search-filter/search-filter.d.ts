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

export interface AutocompleteOptions {
  entity: string;
  field: string;
  term?: string;
  limit?: number;
}

export type AutocompleteSuggestionsQueryResponse = {
  autocompleteSuggestions: string[] | null;
};

export type AutocompleteSuggestionsQueryVariables = {
  autocompleteOptions: AutocompleteOptions;
};
