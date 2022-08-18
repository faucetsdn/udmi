import { UdmiMessage } from './model/UdmiMessage';
import { isPointsetSubType, isSystemSubType, isValidationSubType } from './MessageUtils';
import { Handler } from './Handler';

export const VALIDATOR_ID: string = '_validator';

/**
 * This will attempt to handle all incoming messages.  Some messages may be filtered out if we do not know how to handle them.
 */
export default class UdmiMessageHandler {
  constructor(private deviceHandler: Handler, private siteHandler: Handler) {}

  handleUdmiEvent(udmiMessage: UdmiMessage): void {
    if (this.messageCanBeHandled(udmiMessage)) {
      if (this.isDeviceMessage(udmiMessage)) {
        console.log('Processing Device UDMI message: ' + JSON.stringify(udmiMessage));
        this.deviceHandler.handle(udmiMessage);
      } else if (this.isSiteMessage(udmiMessage)) {
        console.log('Processing Site UDMI message: ' + JSON.stringify(udmiMessage));
        this.siteHandler.handle(udmiMessage);
      }
    } else {
      console.warn('Skipping UDMI message: ' + JSON.stringify(udmiMessage));
    }
  }

  private messageCanBeHandled(message: UdmiMessage): boolean {
    return isPointsetSubType(message) || isSystemSubType(message) || isValidationSubType(message);
  }

  private isDeviceMessage(udmiMessage: UdmiMessage): boolean {
    return !this.isSiteMessage(udmiMessage);
  }

  private isSiteMessage(udmiMessage: UdmiMessage): boolean {
    return udmiMessage.attributes.deviceId === VALIDATOR_ID;
  }
}
