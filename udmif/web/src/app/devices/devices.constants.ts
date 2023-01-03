import { Injectable } from '@angular/core';
import { DeviceColumn } from './devices';

@Injectable({
  providedIn: 'root',
})
export class DevicesConstants {
  public deviceColumns: DeviceColumn[] = [
    {
      value: 'name',
      label: 'Name',
      isSortable: true,
    },
    {
      value: 'make',
      label: 'Make',
      isSortable: true,
    },
    {
      value: 'model',
      label: 'Model',
      isSortable: true,
    },
    {
      value: 'site',
      label: 'Site',
      isSortable: true,
    },
    {
      value: 'section',
      label: 'Section',
      isSortable: true,
    },
    {
      value: 'lastPayload',
      label: 'Last Payload',
      isSortable: true,
    },
    {
      value: 'operational',
      label: 'Operational',
      isSortable: true,
    },
    {
      value: 'serialNumber',
      label: 'Serial Number',
      isSortable: true,
    },
    {
      value: 'firmware',
      label: 'Firmware',
      isSortable: true,
    },
    {
      value: 'message',
      label: 'Message',
      isSortable: false,
    },
    {
      value: 'details',
      label: 'Details',
      isSortable: false,
    },
    {
      value: 'level',
      label: 'Level',
      isSortable: false,
    },
    {
      value: 'state',
      label: 'State',
      isSortable: false,
    },
    {
      value: 'lastStateUpdated',
      label: 'Last State Updated',
      isSortable: true,
    },
    {
      value: 'lastStateSaved',
      label: 'Last State Saved',
      isSortable: true,
    },
    {
      value: 'lastTelemetryUpdated',
      label: 'Last Telemetry Updated',
      isSortable: true,
    },
    {
      value: 'lastTelemetrySaved',
      label: 'Last Telemetry Saved',
      isSortable: true,
    },
    {
      value: 'errorsCount',
      label: 'Errors',
      isSortable: false,
    },
  ];
}
