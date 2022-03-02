import { gql } from 'apollo-angular';
import { Device, SearchOptions } from './device.interface';

export type DevicesResponse = {
  devices: Device[];
  totalCount: number;
};

export type DevicesQueryResponse = {
  devices: DevicesResponse;
};

export type DevicesQueryVariables = {
  searchOptions: SearchOptions;
};

const fragments = {
  device: gql`
    fragment Device on Device {
      id
      name
      make
      model
      site
      section
      lastPayload
      operational
      tags
    }
  `,
};

export const GET_DEVICES = gql<DevicesQueryResponse, DevicesQueryVariables>`
  query GetDevices($searchOptions: SearchOptions!) {
    devices(searchOptions: $searchOptions) {
      devices {
        ...Device
      }
      totalCount
    }
  }
  ${fragments.device}
`;
