import { gql } from 'apollo-angular';

const fragments = {
  device: gql`
    fragment Device on Device {
      id
      name
      # make
      # model
      # site
      # section
      # lastPayload
      # operational
      # tags
    }
  `,
};

export const GET_DEVICES = gql`
  query GetDevices {
    devices {
      ...Device
    }
  }
  ${fragments.device}
`;
