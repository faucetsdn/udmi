import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_DEVICE } from './device.gql';
import { QueryRef } from 'apollo-angular';
import { Observable } from 'rxjs';
import { ApolloQueryResult } from '@apollo/client/core';
import { DeviceQueryResponse, DeviceQueryVariables } from './device';

@Injectable({
  providedIn: 'root',
})
export class DeviceService {
  devicesQuery!: QueryRef<DeviceQueryResponse, DeviceQueryVariables>;

  constructor(private apollo: Apollo) {}

  getDevice(id: string): Observable<ApolloQueryResult<DeviceQueryResponse>> {
    this.devicesQuery = this.apollo.watchQuery<DeviceQueryResponse, DeviceQueryVariables>({
      query: GET_DEVICE,
      variables: {
        id,
      },
    });

    return this.devicesQuery.valueChanges;
  }
}
