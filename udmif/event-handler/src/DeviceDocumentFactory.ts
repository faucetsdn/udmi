import { DeviceBuilder, Device } from './model/Device';
import { isPointsetSubType, isSystemSubType, isValidationSubType } from './MessageUtils';
import { PointsetMessage, SystemMessage, UdmiMessage, ValidationMessage } from './model/UdmiMessage';
import { PointBuilder, Point } from './model/Point';
import { Validation, ValidationBuilder } from './model/Validation';

export class DeviceDocumentFactory {
  public createDeviceDocument(udmiMessage: UdmiMessage, existingPoints: Point[]): Device {
    return createDeviceDocument(udmiMessage, existingPoints);
  }
}

export function createDeviceDocument(udmiMessage: UdmiMessage, existingPoints: Point[]): Device {
  const builder: DeviceBuilder = new DeviceBuilder();
  builder.site(udmiMessage.attributes.deviceRegistryId).name(udmiMessage.attributes.deviceId);

  if (isSystemSubType(udmiMessage)) {
    return buildDeviceDocumentFromSystem(udmiMessage, builder);
  } else if (isPointsetSubType(udmiMessage)) {
    return buildDeviceDocumentFromPointset(udmiMessage, existingPoints, builder);
  } else if (isValidationSubType(udmiMessage)) {
    return buildDeviceDocumentFromValidation(udmiMessage, builder);
  }
}

/**
 * https://faucetsdn.github.io/udmi/gencode/docs/event_system.html describes the incoming schema for an event system message
 */
function buildDeviceDocumentFromSystem(udmiMessage: SystemMessage, builder: DeviceBuilder): Device {
  return builder
    .lastPayload(udmiMessage.data.timestamp)
    .operational(udmiMessage.data.operational)
    .serialNumber(udmiMessage.data.serial_no)
    .make(udmiMessage.data.hardware?.make)
    .model(udmiMessage.data.hardware?.model)
    .firmware(udmiMessage.data.software?.firmware)
    .section(udmiMessage.data.location?.section)
    .id(udmiMessage.attributes.deviceNumId)
    .build();
}

/**
 * https://faucetsdn.github.io/udmi/gencode/docs/event_validation.html  describes the incoming schema for an event validation message
 */
function buildDeviceDocumentFromValidation(udmiMessage: ValidationMessage, builder: DeviceBuilder): Device {
  const validation: Validation = new ValidationBuilder()
    .timestamp(udmiMessage.data.timestamp)
    .version(udmiMessage.data.version)
    .status(udmiMessage.data.status)
    .category(udmiMessage.data.status.category)
    .message(udmiMessage.data.status.message)
    .detail(udmiMessage.data.status.detail)
    .errors(udmiMessage.data.errors)
    .build();

  return builder.validation(validation).build();
}

/**
 * https://faucetsdn.github.io/udmi/gencode/docs/event_pointset.html describes the incoming schema for an event pointset message
 */
function buildDeviceDocumentFromPointset(
  udmiMessage: PointsetMessage,
  existingPoints: Point[],
  deviceBuilder: DeviceBuilder
): Device {
  const points: Point[] = [];

  for (let pointCode in udmiMessage.data.points) {
    const existingPoint = existingPoints.find((candidatePoint) => candidatePoint.name === pointCode);
    const point: Point = buildPoint(udmiMessage, existingPoint, pointCode);
    points.push(point);
  }

  return deviceBuilder.points(points).build();
}

function buildPoint(udmiMessage: UdmiMessage, existingPoint: Point, pointCode: string): Point {
  const pointValue = udmiMessage.data.points[pointCode];

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
