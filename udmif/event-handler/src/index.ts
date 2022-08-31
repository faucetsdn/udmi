import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import { getDeviceDAO, getSiteDAO, getSiteValidationDAO } from './dao/DAO';
import UdmiMessageHandler from './UdmiMessageHandler';
import { UdmiMessage } from './model/UdmiMessage';
import { InvalidMessageError } from './InvalidMessageError';
import { SiteHandler } from './site/SiteHandler';
import { Handler } from './Handler';
import { DeviceHandler } from './device/DeviceHandler';

let messageHandler: UdmiMessageHandler;

/**
 * Triggered from a message on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 */
export const handleUdmiEvent: EventFunction = async (event: any) => {
  try {
    if (!messageHandler) {
      console.log('Creating Message Handler');
      const siteHandler: Handler = new SiteHandler(await getSiteDAO(), await getSiteValidationDAO());
      const deviceHandler: Handler = new DeviceHandler(await getDeviceDAO());
      messageHandler = new UdmiMessageHandler(deviceHandler, siteHandler);
    }
    const udmiMessage: UdmiMessage = decodeEventData(event);
    messageHandler.handleUdmiEvent(udmiMessage);
  } catch (e) {
    if (e instanceof InvalidMessageError) {
      console.error(e.message);
    } else {
      console.error('An unexpected error occurred: ', e);
    }
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
