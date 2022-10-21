import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NavigationService } from '../navigation/navigation.service';
import { Site, SiteDetail } from './site';
import { SiteConstants } from './site.constants';
import { SiteService } from './site.service';

@Component({
  templateUrl: './site.component.html',
  styleUrls: ['./site.component.scss'],
})
export class SiteComponent implements OnInit, OnDestroy {
  siteSubscription!: Subscription;
  fields: SiteDetail[] = this.siteConstants.siteDetails;
  site?: Site;
  loading: boolean = true;

  constructor(
    private route: ActivatedRoute,
    private siteService: SiteService,
    private navigationService: NavigationService,
    private siteConstants: SiteConstants
  ) {}

  ngOnInit(): void {
    const siteId: string = this.route.snapshot.params['siteId'];

    this.siteSubscription = this.siteService.getSite(siteId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.site = data.site;

      this.navigationService.setTitle(this.site?.name);
    });
  }

  ngOnDestroy(): void {
    this.siteSubscription.unsubscribe();
    this.navigationService.clearTitle();
  }
}
