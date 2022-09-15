import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_SITES, GET_SITE_NAMES } from './sites.gql';
import { QueryRef } from 'apollo-angular';
import {
  SiteDistinctQueryResult,
  SiteNamesQueryResponse,
  SiteNamesQueryVariables,
  SitesQueryResponse,
  SitesQueryVariables,
  SortOptions,
} from './sites';
import { map, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class SitesService {
  constructor(private apollo: Apollo) {}

  getSites(
    offset?: number,
    batchSize?: number,
    sortOptions?: SortOptions,
    filter?: string
  ): QueryRef<SitesQueryResponse, SitesQueryVariables> {
    return this.apollo.watchQuery<SitesQueryResponse, SitesQueryVariables>({
      query: GET_SITES,
      fetchPolicy: 'cache-and-network',
      variables: {
        searchOptions: {
          offset,
          batchSize,
          sortOptions,
          filter,
        },
      },
    });
  }

  getSiteNames(search?: string, limit?: number): Observable<SiteDistinctQueryResult> {
    return this.apollo
      .watchQuery<SiteNamesQueryResponse, SiteNamesQueryVariables>({
        query: GET_SITE_NAMES,
        fetchPolicy: 'network-only',
        variables: {
          searchOptions: {
            search,
            limit,
          },
        },
      })
      .valueChanges.pipe(
        map(({ data }) => {
          return { values: data.siteNames };
        })
      );
  }
}
