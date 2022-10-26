import { Injectable } from '@angular/core';
import { intersectionBy } from 'lodash-es';
import { SitesConstants } from '../sites/sites.constants';
import { SiteDetail, SiteModel } from './site';

@Injectable({
  providedIn: 'root',
})
export class SiteConstants {
  public siteDetails: SiteDetail[] = intersectionBy(
    this.sitesConstants.siteColumns,
    (<(keyof SiteModel)[]>[
      'totalDevicesCount',
      'correctDevicesCount',
      'missingDevicesCount',
      'errorDevicesCount',
      'extraDevicesCount',
      'lastValidated',
      'percentValidated',
      'totalDeviceErrorsCount',
    ]).map((value) => ({ value })),
    'value'
  );

  constructor(private sitesConstants: SitesConstants) {}
}
