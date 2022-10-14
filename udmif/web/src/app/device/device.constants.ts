import { Injectable } from '@angular/core';
import { DeviceDetail } from './device';

@Injectable({
  providedIn: 'root',
})
export class DeviceConstants {
  public deviceDetails: DeviceDetail[] = [
    {
      value: 'make',
      label: 'Make',
    },
    {
      value: 'model',
      label: 'Model',
    },
    {
      value: 'site',
      label: 'Site',
    },
    {
      value: 'section',
      label: 'Section',
    },
    {
      value: 'lastPayload',
      label: 'Last Payload',
    },
    {
      value: 'operational',
      label: 'Operational',
    },
    {
      value: 'serialNumber',
      label: 'Serial Number',
    },
    {
      value: 'firmware',
      label: 'Firmware',
    },
    {
      value: 'state',
      label: 'State',
    },
    {
      value: 'lastSeen',
      label: 'Last Seen',
    },
    {
      value: 'errorsCount',
      label: 'Errors',
    },
  ];
}
