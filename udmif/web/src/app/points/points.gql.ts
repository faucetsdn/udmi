import { gql } from 'apollo-angular';

export const GET_POINTS = gql`
  query GetPoints($deviceId: ID!) {
    points(deviceId: $deviceId) {
      id
      name
      value
      units
      state
    }
  }
`;
