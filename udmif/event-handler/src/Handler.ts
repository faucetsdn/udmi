import { UdmiEvent } from './udmi/UdmiEvent';

/**
 * Interface for handlers
 */
export interface Handler {
  handle(udmiEvent: UdmiEvent);
}
