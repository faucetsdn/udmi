import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import { getDeviceDAO, getDeviceValidationDAO, getSiteDAO, getSiteValidationDAO } from './dao/mongo/MongoDAO';
import UdmiEventHandler from './UdmiEventHandler';
import { UdmiEvent } from './model/UdmiEvent';
import { InvalidEventError } from './InvalidEventError';
import { SiteHandler } from './site/SiteHandler';
import { Handler } from './Handler';
import { DeviceHandler } from './device/DeviceHandler';

let eventHandler: UdmiEventHandler;

/**
 * Triggered from a event on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 */
export const handleUdmiEvent: EventFunction = async (event: any) => {
  try {
    if (!eventHandler) {
      console.log('Creating Event Handler');
      const siteHandler: Handler = new SiteHandler(await getSiteDAO(), await getSiteValidationDAO());
      const deviceHandler: Handler = new DeviceHandler(await getDeviceDAO(), await getDeviceValidationDAO());
      eventHandler = new UdmiEventHandler(deviceHandler, siteHandler);
    }
    const udmiEvent: UdmiEvent = decodeEventData(event);
    eventHandler.handleUdmiEvent(udmiEvent);
  } catch (e) {
    if (e instanceof InvalidEventError) {
      console.error(e.message);
    } else {
      console.error('An unexpected error occurred: ', e);
    }
  }
};

/**
 * Decode the event data by replacing the base64 encoded data with a decoded version of the data
 * @param {any} event the message containing a base64 coded data
 * @returns {!UdmiEvent} that has decoded data
 */
export function decodeEventData(event: any): UdmiEvent {
  const stringData = Buffer.from(event.data, 'base64').toString();
  return { ...event, data: JSON.parse(stringData) };
}
