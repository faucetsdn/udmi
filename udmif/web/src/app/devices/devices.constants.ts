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
      value: 'lastSeen',
      label: 'Last Seen',
      isSortable: false,
    },
    {
      value: 'errorsCount',
      label: 'Errors',
      isSortable: false,
    },
  ];
}
