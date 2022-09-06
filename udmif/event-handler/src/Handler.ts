import { UdmiEvent } from './model/UdmiEvent';

/**
 * Interface for handlers
 */
export interface Handler {
  handle(udmiEvent: UdmiEvent);
}
