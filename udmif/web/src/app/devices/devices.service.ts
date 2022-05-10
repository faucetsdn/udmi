import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import {
  GET_DEVICES,
  GET_DEVICE_MAKES,
  GET_DEVICE_MODELS,
  GET_DEVICE_NAMES,
  GET_DEVICE_SECTIONS,
  GET_DEVICE_SITES,
} from './devices.gql';
import { QueryRef } from 'apollo-angular';
import {
  DeviceDistinctQueryResult,
  DeviceDistinctQueryVariables,
  DeviceMakesQueryResponse,
  DeviceModelsQueryResponse,
  DeviceNamesQueryResponse,
  DeviceSectionsQueryResponse,
  DeviceSitesQueryResponse,
  DevicesQueryResponse,
  DevicesQueryVariables,
  SortOptions,
} from './devices';
import { map, Observable } from 'rxjs';
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
    filter?: string
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

  getDeviceNames(term?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceNamesQueryResponse, DeviceDistinctQueryVariables>({
        notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
        query: GET_DEVICE_NAMES,
        fetchPolicy: 'network-only',
        variables: {
          term,
          limit,
        },
      })
      .valueChanges.pipe(
        map(({ data }) => {
          return { values: data.deviceNames };
        })
      );
  }

  getDeviceMakes(term?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceMakesQueryResponse, DeviceDistinctQueryVariables>({
        notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
        query: GET_DEVICE_MAKES,
        fetchPolicy: 'network-only',
        variables: {
          term,
          limit,
        },
      })
      .valueChanges.pipe(
        map(({ data }) => {
          return { values: data.deviceMakes };
        })
      );
  }

  getDeviceModels(term?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceModelsQueryResponse, DeviceDistinctQueryVariables>({
        notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
        query: GET_DEVICE_MODELS,
        fetchPolicy: 'network-only',
        variables: {
          term,
          limit,
        },
      })
      .valueChanges.pipe(
        map(({ data }) => {
          return { values: data.deviceModels };
        })
      );
  }

  getDeviceSites(term?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceSitesQueryResponse, DeviceDistinctQueryVariables>({
        notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
        query: GET_DEVICE_SITES,
        fetchPolicy: 'network-only',
        variables: {
          term,
          limit,
        },
      })
      .valueChanges.pipe(
        map(({ data }) => {
          return { values: data.deviceSites };
        })
      );
  }

  getDeviceSections(term?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceSectionsQueryResponse, DeviceDistinctQueryVariables>({
        notifyOnNetworkStatusChange: true, // to update the loading flag on next batch fetched
        query: GET_DEVICE_SECTIONS,
        fetchPolicy: 'network-only',
        variables: {
          term,
          limit,
        },
      })
      .valueChanges.pipe(
        map(({ data }) => {
          return { values: data.deviceSections };
        })
      );
  }
}
