import { gql } from 'apollo-angular';

export const GET_SITE = gql`
  query GetSite($id: ID!) {
    site(id: $id) {
      id
      name
      totalDevicesCount
      correctDevicesCount
      missingDevicesCount
      errorDevicesCount
      extraDevicesCount
      lastValidated
      percentValidated
      totalDeviceErrorsCount
    }
  }
`;
