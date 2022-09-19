import { Component, OnDestroy, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SearchFilterItem } from '../search-filter/search-filter';
import { Site, SiteModel } from '../site/site';
import { SitesQueryResponse, SitesQueryVariables, SortOptions } from './sites';
import { SitesService } from './sites.service';
import { QueryRef } from 'apollo-angular';
import { Subscription } from 'rxjs';

@Component({
  templateUrl: './sites.component.html',
  styleUrls: ['./sites.component.scss'],
})
export class SitesComponent implements OnInit, OnDestroy {
  sitesSubscription!: Subscription;
  sitesQuery!: QueryRef<SitesQueryResponse, SitesQueryVariables>;
  displayedColumns: (keyof SiteModel)[] = [
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
  sites: Site[] = [];
  totalCount: number = 0;
  totalFilteredCount: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  pageSizeOptions: number[] = [10, 25, 50, 100];
  sortOptions?: SortOptions;
  filter?: string;
  searchFields: Record<string, string> = {
    name: 'getSiteNames',
  };

  constructor(private sitesService: SitesService) {}

  ngOnInit(): void {
    this.sitesQuery = this.sitesService.getSites(0, this.pageSize); // start off on first page, i.e. offset 0

    this.sitesSubscription = this.sitesQuery.valueChanges.subscribe(({ data }) => {
      this.sites = data.sites?.sites ?? [];
      this.totalCount = data.sites?.totalCount ?? 0;
      this.totalFilteredCount = data.sites?.totalFilteredCount ?? 0;
    });
  }

  ngOnDestroy(): void {
    this.sitesSubscription.unsubscribe(); // cleanup
  }

  pageChanged(e: PageEvent): void {
    this.pageSize = e.pageSize;
    this.currentPage = e.pageIndex;

    this._refetch(this.currentPage * this.pageSize);
  }

  sortData(e: Sort): void {
    this.sortOptions = e.direction
      ? {
          field: e.active,
          direction: e.direction === 'asc' ? 'ASC' : 'DESC',
        }
      : undefined; // don't send sortOptions field if no direction

    this._refetch();
  }

  filterData = (filters: SearchFilterItem[]): void => {
    // arrow to hold onto this
    this.filter = filters.length ? JSON.stringify(filters) : undefined; // don't send filter field if no filter

    this._refetch();
  };

  private _refetch(offset: number = 0): void {
    this.sitesQuery.refetch({
      searchOptions: {
        offset,
        batchSize: this.pageSize,
        sortOptions: this.sortOptions,
        filter: this.filter,
      },
    });
  }
}
