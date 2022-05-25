import { DeviceDocumentBuilder, DeviceDocument } from './DeviceDocument';
import {
  isPointset,
  isPointsetConfig,
  isPointsetModel,
  isPointsetState,
  isSystemModel,
  isSystemState,
} from './DocumentTypeUtil';
import { UdmiMessage } from './UdmiMessage';
import { PointBuilder, Point } from './Point';

export function createDeviceDocument(message: UdmiMessage): DeviceDocument {
  const builder: DeviceDocumentBuilder = new DeviceDocumentBuilder();

  builder.id(message.attributes.deviceNumId).name(message.attributes.deviceId);

  if (isSystemState(message)) {
    return createDeviceDocumentFromSystemStateDocument(message, builder);
  } else if (isSystemModel(message)) {
    return createDeviceDocumentFromSystemModelDocument(message, builder);
  } else if (isPointset(message)) {
    return createDeviceDocumentFromPointset(message, builder);
  } else if (isPointsetModel(message)) {
    return createDeviceDocumentFromPointsetModel(message, builder);
  } else if (isPointsetState(message)) {
    return createDeviceDocumentFromPointsetState(message, builder);
  } else if (isPointsetConfig(message)) {
    return createDeviceDocumentFromPointsetConfig(message, builder);
  } else {
    return createDeviceDocumentFromDefaultDeviceDocument(message, builder);
  }
}

function createDeviceDocumentFromSystemModelDocument(
  message: UdmiMessage,
  builder: DeviceDocumentBuilder
): DeviceDocument {
  return builder.section(message.data.location.section).site(message.data.location.site).build();
}

function createDeviceDocumentFromSystemStateDocument(
  message: UdmiMessage,
  builder: DeviceDocumentBuilder
): DeviceDocument {
  return builder
    .lastPayload(message.data.timestamp)
    .operational(message.data.operational)
    .serialNumber(message.data.serial_no)
    .make(message.data.hardware.make)
    .model(message.data.hardware.model)
    .firmware(message.data.software.firmware)
    .build();
}

function createDeviceDocumentFromDefaultDeviceDocument(
  message: UdmiMessage,
  builder: DeviceDocumentBuilder
): DeviceDocument {
  return builder.lastPayload(message.data.timestamp).build();
}

function createDeviceDocumentFromPointset(message: UdmiMessage, deviceBuilder: DeviceDocumentBuilder): DeviceDocument {
  const points: Point[] = [];

  for (var pointObject in message.data.points) {
    const pointValue = message.data.points[pointObject];
    const value: number = pointValue['present_value'];
    const pointSetBuilder: PointBuilder = new PointBuilder();
    const point: Point = pointSetBuilder
      .id(pointObject)
      .name(pointObject)
      .value(value.toString())
      .metaCode(pointObject)
      .build();
    points.push(point);
  }

  return deviceBuilder.points(points).build();
}

function createDeviceDocumentFromPointsetModel(
  message: UdmiMessage,
  deviceBuilder: DeviceDocumentBuilder
): DeviceDocument {
  const points: Point[] = [];

  for (var pointObject in message.data.points) {
    const pointValue = message.data.points[pointObject];
    const units: string = pointValue['units'];
    const pointSetBuilder: PointBuilder = new PointBuilder();
    const point: Point = pointSetBuilder
      .id(pointObject)
      .name(pointObject)
      .units(units)
      .metaUnit(units)
      .metaCode(pointObject)
      .build();
    points.push(point);
  }

  return deviceBuilder.points(points).build();
}

function createDeviceDocumentFromPointsetState(
  message: UdmiMessage,
  deviceBuilder: DeviceDocumentBuilder
): DeviceDocument {
  const points: Point[] = [];

  for (var pointObject in message.data.points) {
    const pointSetBuilder: PointBuilder = new PointBuilder();
    const point: Point = pointSetBuilder.id(pointObject).name(pointObject).metaCode(pointObject).build();
    points.push(point);
  }

  return deviceBuilder.points(points).build();
}

function createDeviceDocumentFromPointsetConfig(
  message: UdmiMessage,
  deviceBuilder: DeviceDocumentBuilder
): DeviceDocument {
  const points: Point[] = [];

  for (var pointObject in message.data.points) {
    const pointSetBuilder: PointBuilder = new PointBuilder();
    const point: Point = pointSetBuilder.id(pointObject).name(pointObject).metaCode(pointObject).build();
    points.push(point);
  }

  return deviceBuilder.points(points).build();
}
