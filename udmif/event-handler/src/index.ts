import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import UdmiEventHandler from './UdmiEventHandler';
import { UdmiEvent } from './model/UdmiEvent';
import { InvalidEventError } from './InvalidEventError';
import { SiteHandler } from './site/SiteHandler';
import { Handler } from './Handler';
import { DeviceHandler } from './device/DeviceHandler';
import {
  getDeviceDAO as getMongoDeviceDao,
  getSiteDAO as getMongoSiteDao,
  getDeviceValidationDAO as getMongoDeviceValidationDao,
  getSiteValidationDAO as getMongoSiteValidationDao,
} from './dao/mongo/MongoDAO';
import { getDeviceDAO } from './dao/postgresql/DeviceDAO';
import { getSiteDAO } from './dao/postgresql/SiteDAO';
import { getDeviceValidationDAO } from './dao/postgresql/DeviceValidationDAO';
import { getSiteValidationDAO } from './dao/postgresql/SiteValidationDAO';

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
      const siteHandler: Handler = new SiteHandler(
        await getMongoSiteDao(),
        await getSiteDAO(),
        await getMongoSiteValidationDao(),
        await getSiteValidationDAO()
      );
      const deviceHandler: Handler = new DeviceHandler(
        await getMongoDeviceDao(),
        await getDeviceDAO(),
        await getMongoDeviceValidationDao(),
        await getDeviceValidationDAO()
      );
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
