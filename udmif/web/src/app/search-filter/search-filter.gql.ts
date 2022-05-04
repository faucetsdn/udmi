import { gql } from 'apollo-angular';

export const GET_AUTOCOMPLETE_SUGGESTIONS = gql`
  query GetAutocompleteSuggestions($autocompleteOptions: AutocompleteOptions!) {
    autocompleteSuggestions(autocompleteOptions: $autocompleteOptions)
  }
`;
