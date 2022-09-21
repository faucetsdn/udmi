import { gql } from 'apollo-angular';

export const GET_SITE = gql`
  query GetSite($name: String!) {
    site(name: $name) {
      name
      totalDevicesCount
      correctDevicesCount
      missingDevicesCount
      errorDevicesCount
      extraDevicesCount
      lastValidated
      percentValidated
      totalDeviceErrorsCount
      validation
    }
  }
`;
