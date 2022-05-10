import { gql } from 'apollo-angular';
import { fragments } from '../device/device.gql';

export const GET_DEVICES = gql`
  query GetDevices($searchOptions: SearchOptions!) {
    devices(searchOptions: $searchOptions) {
      devices {
        ...Device
      }
      totalCount
      totalFilteredCount
    }
  }
  ${fragments.device}
`;

export const GET_DEVICE_NAMES = gql`
  query GetDeviceNames($term: String, $limit: Int) {
    deviceNames(term: $term, limit: $limit)
  }
`;

export const GET_DEVICE_MAKES = gql`
  query GetDeviceMakes($term: String, $limit: Int) {
    deviceMakes(term: $term, limit: $limit)
  }
`;

export const GET_DEVICE_MODELS = gql`
  query GetDeviceModels($term: String, $limit: Int) {
    deviceModels(term: $term, limit: $limit)
  }
`;

export const GET_DEVICE_SITES = gql`
  query GetDeviceSites($term: String, $limit: Int) {
    deviceSites(term: $term, limit: $limit)
  }
`;

export const GET_DEVICE_SECTIONS = gql`
  query GetDeviceSections($term: String, $limit: Int) {
    deviceSections(term: $term, limit: $limit)
  }
`;
