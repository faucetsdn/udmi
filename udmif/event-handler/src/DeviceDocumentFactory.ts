import { DeviceDocumentBuilder, DeviceDocument } from './DeviceDocument';
import {
  isPointsetSubType,
  isSystemSubType,
} from './MessageUtils';
import { UdmiMessage } from './UdmiMessage';
import { PointBuilder, Point } from './Point';

export function createDeviceDocument(udmiMessage: UdmiMessage, existingPoints: Point[]): DeviceDocument {
  const builder: DeviceDocumentBuilder = new DeviceDocumentBuilder();
  builder
    .id(udmiMessage.attributes.deviceNumId)
    .name(udmiMessage.attributes.deviceId);

  if (isSystemSubType(udmiMessage)) {
    return buildDeviceDocumentFromSystem(udmiMessage, builder);
  } else if (isPointsetSubType(udmiMessage)) {
    return buildDeviceDocumentFromPointset(udmiMessage, existingPoints, builder);
  }
}

function buildDeviceDocumentFromSystem(
  udmiMessage: UdmiMessage,
  builder: DeviceDocumentBuilder
): DeviceDocument {
  return builder
    .lastPayload(udmiMessage.data.timestamp)
    .operational(udmiMessage.data.operational)
    .serialNumber(udmiMessage.data.serial_no)
    .make(udmiMessage.data.hardware?.make)
    .model(udmiMessage.data.hardware?.model)
    .firmware(udmiMessage.data.software?.firmware)
    .section(udmiMessage.data.location?.section)
    .site(udmiMessage.data.location?.site)
    .build();
}

function buildDeviceDocumentFromPointset(
  udmiMessage: UdmiMessage,
  existingPoints: Point[],
  deviceBuilder: DeviceDocumentBuilder
): DeviceDocument {
  const points: Point[] = [];

  for (var pointCode in udmiMessage.data.points) {
    const existingPoint = existingPoints.find(point => point.name === pointCode);
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

