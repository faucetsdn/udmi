import { Injectable } from '@angular/core';
import { intersectionBy } from 'lodash-es';
import { DevicesConstants } from '../devices/devices.constants';
import { DeviceDetail, DeviceModel } from './device';

@Injectable({
  providedIn: 'root',
})
export class DeviceConstants {
  public deviceDetails: DeviceDetail[] = intersectionBy(
    this.devicesConstants.deviceColumns,
    (<(keyof DeviceModel)[]>[
      'make',
      'model',
      'site',
      'section',
      'lastPayload',
      'operational',
      'serialNumber',
      'firmware',
      'state',
      'lastSeen',
      'errorsCount',
    ]).map((value) => ({ value })),
    'value'
  );

  constructor(private devicesConstants: DevicesConstants) {}
}
