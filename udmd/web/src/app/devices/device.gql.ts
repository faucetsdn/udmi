import { gql } from 'apollo-angular';
import { Device, SearchOptions } from './device.interface';

export type DevicesResponse = {
  devices: Device[];
  totalCount: number;
};

type Response = {
  devices: DevicesResponse;
};

type Variables = {
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

export const GET_DEVICES = gql<Response, Variables>`
  query GetDevices($searchOptions: SearchOptions) {
    devices(searchOptions: $searchOptions) {
      devices {
        ...Device
      }
      totalCount
    }
  }
  ${fragments.device}
`;
