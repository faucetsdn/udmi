import { gql } from 'apollo-angular';

export const GET_DEVICES = gql`
  query GetDevices($searchOptions: SearchOptions) {
    devices(searchOptions: $searchOptions) {
      devices {
        uuid
        id
        name
        make
        model
        site
        section
        lastPayload
        operational
        level
        message
        details
        state
        errorsCount
        validation
      }
      totalCount
      totalFilteredCount
    }
  }
`;

export const GET_DEVICE_NAMES = gql`
  query GetDeviceNames($searchOptions: DistinctSearchOptions) {
    deviceNames(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_MAKES = gql`
  query GetDeviceMakes($searchOptions: DistinctSearchOptions) {
    deviceMakes(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_MODELS = gql`
  query GetDeviceModels($searchOptions: DistinctSearchOptions) {
    deviceModels(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_SITES = gql`
  query GetDeviceSites($searchOptions: DistinctSearchOptions) {
    siteNames(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_SECTIONS = gql`
  query GetDeviceSections($searchOptions: DistinctSearchOptions) {
    sections(searchOptions: $searchOptions)
  }
`;
