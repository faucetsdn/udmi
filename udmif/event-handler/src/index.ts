import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import { getDeviceDAO } from './DeviceDaoFactory';
import UdmiMessageHandler from './UdmiMessageHandler';
import { UdmiMessage } from './UdmiMessage';

let messageHandler: UdmiMessageHandler;

/**
 * Triggered from a message on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 * @param {!Object} context Metadata for the event.
 */
export const handleUdmiEvent: EventFunction = async (event: any, context: any) => {
  try {
    if (!messageHandler) {
      console.log('Creating Message Handler');
      messageHandler = new UdmiMessageHandler(await getDeviceDAO());
    }
    const udmiMessage: UdmiMessage = decodeEventData(event);
    await messageHandler.handleUdmiEvent(udmiMessage);
  } catch (e) {
    console.error('An unexpected error occurred: ', e);
  }
};

/**
 * Decode the event data by replacing the base64 encoded data with a decoded version of the data
 * @param {any} event the message containing a base64 coded data
 * @returns {!UdmiMessage} that has decoded data
 */
export function decodeEventData(event: any): UdmiMessage {
  const stringData = Buffer.from(event.data, 'base64').toString();
  return { ...event, data: JSON.parse(stringData) };
}
