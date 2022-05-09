import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_DEVICES } from './devices.gql';
import { QueryRef } from 'apollo-angular';
import { DevicesQueryResponse, DevicesQueryVariables, SortOptions } from './devices';
import { Observable } from 'rxjs';
import { ApolloQueryResult } from '@apollo/client/core';

@Injectable({
  providedIn: 'root',
})
export class DevicesService {
  devicesQuery!: QueryRef<DevicesQueryResponse, DevicesQueryVariables>;

  constructor(private apollo: Apollo) {}

  getDevices(
    offset?: number,
    batchSize: number = 10,
    sortOptions?: SortOptions,
    filter?: string,
    uniqueBy?: string
  ): Observable<ApolloQueryResult<DevicesQueryResponse>> {
    this.devicesQuery = this.apollo.watchQuery<DevicesQueryResponse, DevicesQueryVariables>({
      notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
      query: GET_DEVICES,
      fetchPolicy: 'network-only',
      variables: {
        searchOptions: {
          offset,
          batchSize,
          sortOptions,
          filter,
          uniqueBy,
        },
      },
    });

    return this.devicesQuery.valueChanges;
  }

  fetchMore(offset?: number, batchSize: number = 10, sortOptions?: SortOptions, filter?: string): void {
    this.devicesQuery.refetch({
      searchOptions: {
        offset,
        batchSize,
        sortOptions,
        filter,
      },
    });
  }
}
