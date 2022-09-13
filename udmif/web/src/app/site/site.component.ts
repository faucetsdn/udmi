import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Site, SiteModel } from './site';
import { SiteService } from './site.service';

@Component({
  templateUrl: './site.component.html',
  styleUrls: ['./site.component.scss'],
})
export class SiteComponent implements OnInit {
  fields: (keyof SiteModel)[] = [
    'name',
    'totalDevicesCount',
    'correctDevicesCount',
    'missingDevicesCount',
    'errorDevicesCount',
    'extraDevicesCount',
    'lastValidated',
    'percentValidated',
    'totalDeviceErrorsCount',
  ];
  site?: Site;
  loading: boolean = true;

  constructor(private route: ActivatedRoute, private siteService: SiteService) {}

  ngOnInit(): void {
    const siteId: string = this.route.snapshot.params['id'];

    this.siteService.getSite(siteId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.site = data.site;
    });
  }
}
