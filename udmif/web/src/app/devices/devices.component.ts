import { Component, OnDestroy, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SearchFilterItem } from '../search-filter/search-filter';
import { Device, DeviceModel } from '../device/device';
import { DevicesQueryResponse, DevicesQueryVariables, SortOptions } from './devices';
import { DevicesService } from './devices.service';
import { QueryRef } from 'apollo-angular';
import { Subscription } from 'rxjs';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit, OnDestroy {
  private devicesSubscription!: Subscription;
  private devicesQuery!: QueryRef<DevicesQueryResponse, DevicesQueryVariables>;
  displayedColumns: (keyof DeviceModel)[] = [
    'name',
    'make',
    'model',
    'site',
    'section',
    'lastPayload',
    'operational',
    'tags',
  ];
  devices: Device[] = [];
  totalCount: number = 0;
  totalFilteredCount: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  pageSizeOptions: number[] = [10, 25, 50, 100];
  sortOptions?: SortOptions;
  filter?: string;
  searchFields: Record<string, string> = {
    name: 'getDeviceNames',
    make: 'getDeviceMakes',
    model: 'getDeviceModels',
    site: 'getDeviceSites',
    section: 'getDeviceSections',
  };

  constructor(private devicesService: DevicesService) {}

  ngOnInit(): void {
    this.devicesQuery = this.devicesService.getDevices(0, this.pageSize); // start off on first page, i.e. offset 0

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
    this.filter = filters.length ? JSON.stringify(filters) : undefined; // don't send filter field if no filter

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
