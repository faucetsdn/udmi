import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_AUTOCOMPLETE_SUGGESTIONS } from './search-filter.gql';
import { QueryRef } from 'apollo-angular';
import { Observable } from 'rxjs';
import { ApolloQueryResult } from '@apollo/client/core';
import { AutocompleteSuggestionsQueryResponse, AutocompleteSuggestionsQueryVariables } from './search-filter';

@Injectable({
  providedIn: 'root',
})
export class SearchFilterService {
  private _autocompleteSuggestionsQuery!: QueryRef<
    AutocompleteSuggestionsQueryResponse,
    AutocompleteSuggestionsQueryVariables
  >;

  constructor(private apollo: Apollo) {}

  getAutocompleteSuggestions(
    entity: string,
    field: string,
    term?: string,
    limit?: number
  ): Observable<ApolloQueryResult<AutocompleteSuggestionsQueryResponse>> {
    this._autocompleteSuggestionsQuery = this.apollo.watchQuery<
      AutocompleteSuggestionsQueryResponse,
      AutocompleteSuggestionsQueryVariables
    >({
      fetchPolicy: 'network-only',
      query: GET_AUTOCOMPLETE_SUGGESTIONS,
      variables: {
        autocompleteOptions: {
          entity,
          field,
          term,
          limit,
        },
      },
    });

    return this._autocompleteSuggestionsQuery.valueChanges;
  }
}
