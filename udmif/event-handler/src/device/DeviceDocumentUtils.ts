import { DeviceBuilder, Device, DeviceKey, DeviceValidation } from './model/Device';
import { isPointsetSubType, isSubType, isSystemSubType, isValidationSubType, STATE } from '../EventUtils';
import { PointsetEvent, SystemEvent, UdmiEvent, ValidationEvent } from '../model/UdmiEvent';
import { PointBuilder, Point } from './model/Point';
import { Validation, ValidationBuilder } from '../model/Validation';
import { InvalidEventError } from '../InvalidEventError';

export function getDeviceKey(message: UdmiEvent): DeviceKey {
  if (!message.attributes.deviceId) {
    throw new InvalidEventError('An invalid device id was submitted');
  }

  if (!message.attributes.deviceRegistryId) {
    throw new InvalidEventError('An invalid site was submitted');
  }

  return { name: message.attributes.deviceId, site: message.attributes.deviceRegistryId };
}

export function createDevice(udmiEvent: UdmiEvent, existingPoints: Point[]): Device {
  console.log('createDevice: ' + existingPoints);
  const builder: DeviceBuilder = new DeviceBuilder();
  builder
    .site(udmiEvent.attributes.deviceRegistryId)
    .name(udmiEvent.attributes.deviceId)
    .id(udmiEvent.attributes.deviceNumId);

  if (isSystemSubType(udmiEvent)) {
    return buildDeviceDocumentFromSystem(udmiEvent, builder, existingPoints);
  } else if (isPointsetSubType(udmiEvent)) {
    return buildDeviceDocumentFromPointset(udmiEvent, builder, existingPoints);
  } else if (isValidationSubType(udmiEvent)) {
    return buildDeviceDocumentFromValidation(udmiEvent, builder);
  }
}

export function getDeviceValidation(udmiEvent: ValidationEvent, deviceKey: DeviceKey): DeviceValidation {
  return { timestamp: new Date(udmiEvent.data.timestamp), deviceKey, message: udmiEvent.data };
}

/**
 * https://faucetsdn.github.io/udmi/gencode/docs/event_system.html describes the incoming schema for an event system message
 */
function buildDeviceDocumentFromSystem(
  udmiEvent: SystemEvent,
  builder: DeviceBuilder,
  existingPoints: Point[]
): Device {
  return builder
    .lastPayload(udmiEvent.data.timestamp)
    .operational(udmiEvent.data.operational)
    .serialNumber(udmiEvent.data.serial_no)
    .make(udmiEvent.data.hardware?.make)
    .model(udmiEvent.data.hardware?.model)
    .firmware(udmiEvent.data.software?.firmware)
    .section(udmiEvent.data.location?.section)
    .points(existingPoints)
    .id(udmiEvent.attributes.deviceNumId)
    .lastStateUpdated(isSubType(udmiEvent, STATE) ? udmiEvent.data.timestamp : null)
    .lastStateSaved(isSubType(udmiEvent, STATE) ? getNow() : null)
    .build();
}

/**
 * https://faucetsdn.github.io/udmi/gencode/docs/event_validation.html  describes the incoming schema for an event validation message
 */
function buildDeviceDocumentFromValidation(udmiEvent: ValidationEvent, builder: DeviceBuilder): Device {
  const validation: Validation = new ValidationBuilder()
    .timestamp(udmiEvent.data.timestamp)
    .version(udmiEvent.data.version)
    .status(udmiEvent.data.status)
    .category(udmiEvent.data.status.category)
    .message(udmiEvent.data.status.message)
    .detail(udmiEvent.data.status.detail)
    .errors(udmiEvent.data.errors)
    .build();

  return builder.validation(validation).build();
}

/**
 * https://faucetsdn.github.io/udmi/gencode/docs/event_pointset.html describes the incoming schema for an event pointset message
 */
function buildDeviceDocumentFromPointset(
  udmiEvent: PointsetEvent,
  deviceBuilder: DeviceBuilder,
  existingPoints: Point[] = []
): Device {
  const points: Point[] = [];

  if (!existingPoints) {
    existingPoints = [];
  }
  console.log('Device Document Existing Pointset: ' + existingPoints);
  for (let pointCode in udmiEvent.data.points) {
    const existingPoint = existingPoints.find((candidatePoint) => candidatePoint.name === pointCode);
    const point: Point = buildPoint(udmiEvent, existingPoint, pointCode);
    points.push(point);
  }

  return deviceBuilder
    .points(points)
    .lastPayload(udmiEvent.data.timestamp)
    .lastTelemetryUpdated(isSubType(udmiEvent, STATE) ? udmiEvent.data.timestamp : null)
    .lastTelemetrySaved(isSubType(udmiEvent, STATE) ? getNow() : null)
    .build();
}

export function buildPoint(udmiEvent: UdmiEvent, existingPoint: Point, pointCode: string): Point {
  const pointValue = udmiEvent.data.points[pointCode];

  // we get the value from either the message or the existing point
  const value: number = pointValue.present_value ?? existingPoint?.value;

  // we get the units from either the message or the existing point
  const units: string = pointValue.units ?? existingPoint?.units;

  return new PointBuilder()
    .id(pointCode)
    .name(pointCode)
    .units(units)
    .value(value?.toString())
    .metaUnit(units)
    .metaCode(pointCode)
    .build();
}

function getNow() {
  return new Date().toISOString().split('.')[0] + 'Z';
}
