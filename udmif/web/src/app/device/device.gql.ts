import { gql } from 'apollo-angular';

export const fragments = {
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
      firmware
      serialNumber
      tags
    }
  `,
};

export const GET_DEVICE = gql`
  query GetDevice($id: ID!) {
    device(id: $id) {
      ...Device
    }
  }
  ${fragments.device}
`;
