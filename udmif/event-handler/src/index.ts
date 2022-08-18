import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import { getDeviceDAO, getSiteDAO } from './dao/DAO';
import UdmiMessageHandler from './UdmiMessageHandler';
import { UdmiMessage } from './model/UdmiMessage';
import { DeviceDocumentFactory } from './device/DeviceDocumentFactory';
import { InvalidMessageError } from './InvalidMessageError';
import { SiteHandler } from './site/SiteHandler';
import { Handler } from './Handler';
import { DeviceHandler } from './device/DeviceHandler';
import { SiteDocumentFactory } from './site/SiteDocumentFactory';

let messageHandler: UdmiMessageHandler;

/**
 * Triggered from a message on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 * @param {!Object} context Metadata for the event.
 */
export const handleUdmiEvent: EventFunction = async (event: any) => {
  try {
    if (!messageHandler) {
      console.log('Creating Message Handler');
      const siteHandler: Handler = new SiteHandler(await getSiteDAO(), new SiteDocumentFactory());
      const deviceHandler: Handler = new DeviceHandler(await getDeviceDAO(), new DeviceDocumentFactory());
      messageHandler = new UdmiMessageHandler(deviceHandler, siteHandler);
    }
    const udmiMessage: UdmiMessage = decodeEventData(event);
    await messageHandler.handleUdmiEvent(udmiMessage);
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
