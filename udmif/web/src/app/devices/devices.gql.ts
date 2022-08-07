import { gql } from 'apollo-angular';

export const GET_DEVICES = gql`
  query GetDevices($searchOptions: SearchOptions!) {
    devices(searchOptions: $searchOptions) {
      devices {
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
        tags
      }
      totalCount
      totalFilteredCount
    }
  }
`;

export const GET_DEVICE_NAMES = gql`
  query GetDeviceNames($searchOptions: DeviceNamesSearchOptions) {
    deviceNames(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_MAKES = gql`
  query GetDeviceMakes($searchOptions: DeviceMakesSearchOptions) {
    deviceMakes(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_MODELS = gql`
  query GetDeviceModels($searchOptions: DeviceModelsSearchOptions) {
    deviceModels(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_SITES = gql`
  query GetDeviceSites($searchOptions: SitesSearchOptions) {
    sites(searchOptions: $searchOptions)
  }
`;

export const GET_DEVICE_SECTIONS = gql`
  query GetDeviceSections($searchOptions: SectionsSearchOptions) {
    sections(searchOptions: $searchOptions)
  }
`;
