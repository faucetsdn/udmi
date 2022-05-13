import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import { getDeviceDAO } from './DeviceDaoFactory';
import UdmiMessageHandler from './UdmiMessageHandler';

let messageHandler: UdmiMessageHandler;

/**
 * Triggered from a message on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 * @param {!Object} context Metadata for the event.
 */
export const handleUdmiEvent: EventFunction = async (event, context) => {
  try {
    if (!messageHandler) {
      console.log('Creating Message Handler');
      messageHandler = new UdmiMessageHandler(await getDeviceDAO());
    }
    messageHandler.handleUdmiEvent(event);
  } catch (e) {
    console.error('An unexpected error occurred: ', e);
  }
};
