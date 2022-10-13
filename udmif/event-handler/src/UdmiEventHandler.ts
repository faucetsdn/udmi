import { UdmiEvent } from './model/UdmiEvent';
import { isPointsetSubType, isSystemSubType, isValidationSubType } from './EventUtils';
import { Handler } from './Handler';

export const VALIDATOR_ID: string = '_validator';

/**
 * This will attempt to handle all incoming messages.  Some messages may be filtered out if we do not know how to handle them.
 */
export default class UdmiEventHandler {
  constructor(private deviceHandler: Handler, private siteHandler: Handler) {}

  handleUdmiEvent(udmiEvent: UdmiEvent): void {
    if (this.messageCanBeHandled(udmiEvent)) {
      if (this.isDeviceEvent(udmiEvent)) {
        console.log('Processing Device UDMI message: ' + JSON.stringify(udmiEvent));
        this.deviceHandler.handle(udmiEvent);
      } else if (this.isSiteValidationEvent(udmiEvent)) {
        console.log('Processing Site UDMI message: ' + JSON.stringify(udmiEvent));
        this.siteHandler.handle(udmiEvent);
      }
    } else {
      console.warn('Skipping UDMI message: ' + JSON.stringify(udmiEvent));
    }
  }

  private messageCanBeHandled(event: UdmiEvent): boolean {
    return (
      isPointsetSubType(event) ||
      isSystemSubType(event) ||
      isValidationSubType(event) ||
      this.isSiteValidationEvent(event)
    );
  }

  private isDeviceEvent(udmiEvent: UdmiEvent): boolean {
    return !this.isSiteValidationEvent(udmiEvent);
  }

  private isSiteValidationEvent(udmiEvent: UdmiEvent): boolean {
    return udmiEvent.attributes.deviceId === VALIDATOR_ID;
  }
}
