import { Component, OnDestroy, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SearchFilterItem } from '../search-filter/search-filter';
import { Device, DeviceModel } from '../device/device';
import { DeviceColumn, DevicesQueryResponse, DevicesQueryVariables, SortOptions } from './devices';
import { DevicesService } from './devices.service';
import { QueryRef } from 'apollo-angular';
import { Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { compact, union } from 'lodash-es';
import { animate, state, style, transition, trigger } from '@angular/animations';
import { DevicesConstants } from './devices.constants';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
  animations: [
    trigger('detailExpand', [
      state('collapsed', style({ height: '0', minHeight: '0' })),
      state('expanded', style({ height: '*', minHeight: '*' })),
      transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
    ]),
  ],
})
export class DevicesComponent implements OnInit, OnDestroy {
  devicesSubscription!: Subscription;
  devicesQuery!: QueryRef<DevicesQueryResponse, DevicesQueryVariables>;
  columns: DeviceColumn[] = this.devicesConstants.deviceColumns;
  displayedColumns: (keyof DeviceModel)[] = this.columns
    .map(({ value }) => value)
    .filter((columnValue) => this.route.snapshot.data['displayedColumns'].includes(columnValue));
  siteId?: string = this.route.snapshot.params['siteId'];
  devices: Device[] = [];
  totalCount: number = 0;
  totalFilteredCount: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  pageSizeOptions: number[] = [10, 25, 50, 100];
  sortOptions?: SortOptions;
  siteFilter?: SearchFilterItem = this.siteId ? { field: 'site', operator: '=', value: this.siteId } : undefined;
  defaultFilters: SearchFilterItem[] = compact([this.siteFilter]);
  stringifiedDefaultFilters?: string = this.defaultFilters.length ? JSON.stringify(this.defaultFilters) : undefined;
  filter?: string = this.stringifiedDefaultFilters;
  searchFields: Record<string, string> = this.route.snapshot.data['searchFields'];
  expandedElement: Device | null = null;

  constructor(
    private devicesService: DevicesService,
    private route: ActivatedRoute,
    private devicesConstants: DevicesConstants
  ) {}

  ngOnInit(): void {
    this.devicesQuery = this.devicesService.getDevices(0, this.pageSize, this.sortOptions, this.filter); // start off on first page, i.e. offset 0

    this.devicesSubscription = this.devicesQuery.valueChanges.subscribe(({ data }) => {
      this.devices = data.devices?.devices ?? [];
      this.totalCount = data.devices?.totalCount ?? 0;
      this.totalFilteredCount = data.devices?.totalFilteredCount ?? 0;
    });
  }

  ngOnDestroy(): void {
    this.devicesSubscription.unsubscribe(); // cleanup
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
    const allFilters = union(this.defaultFilters, filters);

    this.filter = allFilters.length ? JSON.stringify(allFilters) : undefined; // don't send filter field if no filter

    this._refetch();
  };

  private _refetch(offset: number = 0): void {
    this.devicesQuery.refetch({
      searchOptions: {
        offset,
        batchSize: this.pageSize,
        sortOptions: this.sortOptions,
        filter: this.filter,
      },
    });
  }
}
