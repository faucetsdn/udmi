import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_SITE } from './site.gql';
import { QueryRef } from 'apollo-angular';
import { Observable } from 'rxjs';
import { ApolloQueryResult } from '@apollo/client/core';
import { SiteQueryResponse, SiteQueryVariables } from './site';

@Injectable({
  providedIn: 'root',
})
export class SiteService {
  siteQuery!: QueryRef<SiteQueryResponse, SiteQueryVariables>;

  constructor(private apollo: Apollo) {}

  getSite(id: string): Observable<ApolloQueryResult<SiteQueryResponse>> {
    this.siteQuery = this.apollo.watchQuery<SiteQueryResponse, SiteQueryVariables>({
      query: GET_SITE,
      variables: {
        id,
      },
    });

    return this.siteQuery.valueChanges;
  }
}
