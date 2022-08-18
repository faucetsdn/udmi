import { UdmiMessage } from './model/UdmiMessage';

/**
 * Interface for handlers
 */
export interface Handler {
  handle(udmiMessage: UdmiMessage);
}
