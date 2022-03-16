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
  query GetDevicePoints($id: ID!) {
    device(id: $id) {
      id
      points {
        ...Point
      }
    }
  }
  ${fragments.point}
`;
