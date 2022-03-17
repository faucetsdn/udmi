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
  pointsQuery!: QueryRef<PointsQueryResponse, PointsQueryVariables>;

  constructor(private apollo: Apollo) {}

  getPoints(deviceId: string): Observable<ApolloQueryResult<PointsQueryResponse>> {
    this.pointsQuery = this.apollo.watchQuery<PointsQueryResponse, PointsQueryVariables>({
      query: GET_POINTS,
      variables: {
        deviceId,
      },
    });

    return this.pointsQuery.valueChanges;
  }
}
