import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { DevicesQueryResponse, DevicesQueryVariables, GET_DEVICES } from './device.gql';
import { QueryRef } from 'apollo-angular';

@Injectable({
  providedIn: 'root',
})
export class DevicesService {
  devicesQuery!: QueryRef<DevicesQueryResponse, DevicesQueryVariables>;

  constructor(private apollo: Apollo) {}

  getDevices(offset: number = 0, batchSize: number = 10) {
    this.devicesQuery = this.apollo.watchQuery({
      notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
      query: GET_DEVICES,
      fetchPolicy: 'network-only',
      variables: {
        searchOptions: {
          offset,
          batchSize,
        },
      },
    });

    return this.devicesQuery.valueChanges;
  }

  fetchMore(offset: number = 0, batchSize: number = 10) {
    this.devicesQuery.refetch({
      searchOptions: {
        offset,
        batchSize,
      },
    });
  }
}
