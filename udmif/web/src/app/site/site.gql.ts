import { gql } from 'apollo-angular';

export const GET_SITE = gql`
  query GetSite($name: String!) {
    site(name: $name) {
      uuid
      name
      seenDevicesCount
      totalDevicesCount
      correctDevicesCount
      correctDevicesPercent
      missingDevicesCount
      missingDevicesPercent
      errorDevicesCount
      errorDevicesPercent
      extraDevicesCount
      lastValidated
      totalDeviceErrorsCount
      validation
    }
  }
`;
