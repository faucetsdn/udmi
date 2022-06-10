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
  DeviceMakesQueryResponse,
  DeviceMakesQueryVariables,
  DeviceModelsQueryResponse,
  DeviceModelsQueryVariables,
  DeviceNamesQueryResponse,
  DeviceNamesQueryVariables,
  DeviceSectionsQueryResponse,
  DeviceSectionsQueryVariables,
  DeviceSitesQueryResponse,
  DeviceSitesQueryVariables,
  DevicesQueryResponse,
  DevicesQueryVariables,
  SortOptions,
} from './devices';
import { map, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class DevicesService {
  constructor(private apollo: Apollo) {}

  getDevices(
    offset?: number,
    batchSize: number = 10,
    sortOptions?: SortOptions,
    filter?: string
  ): QueryRef<DevicesQueryResponse, DevicesQueryVariables> {
    return this.apollo.watchQuery<DevicesQueryResponse, DevicesQueryVariables>({
      query: GET_DEVICES,
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

  getDeviceNames(search?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceNamesQueryResponse, DeviceNamesQueryVariables>({
        query: GET_DEVICE_NAMES,
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
          return { values: data.deviceNames };
        })
      );
  }

  getDeviceMakes(search?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceMakesQueryResponse, DeviceMakesQueryVariables>({
        query: GET_DEVICE_MAKES,
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
          return { values: data.deviceMakes };
        })
      );
  }

  getDeviceModels(search?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceModelsQueryResponse, DeviceModelsQueryVariables>({
        query: GET_DEVICE_MODELS,
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
          return { values: data.deviceModels };
        })
      );
  }

  getDeviceSites(search?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceSitesQueryResponse, DeviceSitesQueryVariables>({
        query: GET_DEVICE_SITES,
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
          return { values: data.sites };
        })
      );
  }

  getDeviceSections(search?: string, limit?: number): Observable<DeviceDistinctQueryResult> {
    return this.apollo
      .watchQuery<DeviceSectionsQueryResponse, DeviceSectionsQueryVariables>({
        query: GET_DEVICE_SECTIONS,
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
          return { values: data.sections };
        })
      );
  }
}
