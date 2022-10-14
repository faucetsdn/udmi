import { gql } from 'apollo-angular';

export const GET_DEVICE = gql`
  query GetDevice($id: ID!) {
    device(id: $id) {
      id
      name
      make
      model
      site
      section
      lastPayload
      operational
      firmware
      serialNumber
      level
      lastSeen
      state
      errorsCount
      validation
    }
  }
`;
