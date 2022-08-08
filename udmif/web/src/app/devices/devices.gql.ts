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
