import type { EventFunction } from '@google-cloud/functions-framework/build/src/functions';
import UdmiEventHandler from './udmi/UdmiEventHandler';
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
import { getDeviceDAO } from './device/DeviceDAO';
import { getSiteDAO } from './site/SiteDAO';
import { getDeviceValidationDAO } from './device/DeviceValidationDAO';
import { getSiteValidationDAO } from './site/SiteValidationDAO';
import { isConnectedToPostgreSQL } from './dao/postgresql/PgDaoProvider';

let eventHandler: UdmiEventHandler;

/**
 * Triggered from a event on a Cloud Pub/Sub topic.
 *
 * @param {!Object} event Event payload.
 */
export const handleUdmiEvent: EventFunction = async (event: any) => {
  try {
    if (!eventHandler) {
      const connectedToPostgreSQL: boolean = await isConnectedToPostgreSQL();
      if (!connectedToPostgreSQL) console.log('Skipping all PostgreSQL operations.');

      const siteHandler: Handler = new SiteHandler(
        await getMongoSiteDao(),
        connectedToPostgreSQL ? await getSiteDAO() : null,
        await getMongoSiteValidationDao(),
        connectedToPostgreSQL ? await getSiteValidationDAO() : null
      );

      const deviceHandler: Handler = new DeviceHandler(
        await getMongoDeviceDao(),
        connectedToPostgreSQL ? await getDeviceDAO() : null,
        await getMongoDeviceValidationDao(),
        connectedToPostgreSQL ? await getDeviceValidationDAO() : null
      );

      console.log('Creating Event Handler');
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
