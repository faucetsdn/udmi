import { gql } from 'apollo-angular';

export const fragments = {
  point: gql`
    fragment Point on Point {
      id
      name
      value
      units
      state
    }
  `,
};

export const GET_POINTS = gql`
  query GetPoints($deviceId: ID!) {
    points(deviceId: $deviceId) {
      ...Point
    }
  }
  ${fragments.point}
`;
