import { gql } from 'apollo-angular';

export const GET_SITES = gql`
  query GetSites($searchOptions: SearchOptions) {
    sites(searchOptions: $searchOptions) {
      sites {
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
