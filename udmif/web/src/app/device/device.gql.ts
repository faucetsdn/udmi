import { gql } from 'apollo-angular';

export const GET_DEVICE = gql`
  query GetDevice($id: ID!) {
    device(id: $id) {
      uuid
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
      level
      state
      errorsCount
      validation
      lastStateUpdated
      lastStateSaved
      lastTelemetryUpdated
      lastTelemetrySaved
    }
  }
`;
