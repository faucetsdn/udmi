import { Injectable } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_POINTS } from './points.gql';
import { QueryRef } from 'apollo-angular';
import { Observable } from 'rxjs';
import { ApolloQueryResult } from '@apollo/client/core';
import { PointsQueryResponse, PointsQueryVariables } from './points';

@Injectable({
  providedIn: 'root',
})
export class PointsService {
  devicesQuery!: QueryRef<PointsQueryResponse, PointsQueryVariables>;

  constructor(private apollo: Apollo) {}

  getPoints(deviceId: string): Observable<ApolloQueryResult<PointsQueryResponse>> {
    this.devicesQuery = this.apollo.watchQuery<PointsQueryResponse, PointsQueryVariables>({
      query: GET_POINTS,
      variables: {
        id: deviceId,
      },
    });

    return this.devicesQuery.valueChanges;
  }
}
