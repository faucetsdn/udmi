import { gql } from 'apollo-angular';

export const GET_SITES = gql`
  query GetSites($searchOptions: SearchOptions) {
    sites(searchOptions: $searchOptions) {
      sites {
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
        deviceErrors {
          message
          timestamp
        }
        totalDeviceErrorsCount
      }
      totalCount
      totalFilteredCount
    }
  }
`;

export const GET_SITE_NAMES = gql`
  query GetSiteNames($searchOptions: DistinctSearchOptions) {
    siteNames(searchOptions: $searchOptions)
  }
`;
