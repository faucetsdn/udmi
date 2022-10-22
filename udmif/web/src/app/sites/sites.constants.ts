import { Injectable } from '@angular/core';
import { SiteColumn } from './sites';

@Injectable({
  providedIn: 'root',
})
export class SitesConstants {
  public siteColumns: SiteColumn[] = [
    {
      value: 'name',
      label: 'Name',
      isSortable: true,
    },
    {
      value: 'totalDevicesCount',
      label: 'Devices',
      isSortable: false,
    },
    {
      value: 'correctDevicesCount',
      label: 'Correct Devices',
      isSortable: false,
    },
    {
      value: 'missingDevicesCount',
      label: 'Missing Devices',
      isSortable: false,
    },
    {
      value: 'errorDevicesCount',
      label: 'Error Devices',
      isSortable: false,
    },
    {
      value: 'extraDevicesCount',
      label: 'Extra Devices',
      isSortable: false,
    },
    {
      value: 'lastValidated',
      label: 'Last Validated',
      isSortable: false,
    },
    {
      value: 'percentValidated',
      label: '% Validated',
      isSortable: false,
    },
    {
      value: 'totalDeviceErrorsCount',
      label: 'Device Errors',
      isSortable: false,
    },
  ];
}
