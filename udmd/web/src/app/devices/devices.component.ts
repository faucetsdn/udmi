import { Component, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SearchFilterItem } from '../search-filter/search-filter';
import { Device } from '../device/device';
import { SortOptions } from './devices';
import { DevicesService } from './devices.service';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['name', 'make', 'model', 'site', 'section', 'lastPayload', 'operational', 'tags'];
  loading: boolean = true;
  devices: Device[] = [];
  totalCount: number = 0;
  totalFilteredCount: number = 0;
  currentPage: number = 0;
  pageSize: number = 10;
  pageSizeOptions: number[] = [10, 25, 50, 100];
  sortOptions: SortOptions | undefined;
  filter: string | undefined;

  //TODO:: Pass along proper options to search filter.
  searchFilterOptions = {
    site: ['site1', 'site2', 'site3'],
    make: [
      'Cisco',
      'BitBox USA',
      'Automated Logic',
      'Enlightened',
      'Tridium',
      'Delta Controls',
      'Acquisuite',
      'Schneider Electric / APC',
    ],
  };

  constructor(private devicesService: DevicesService) {}

  ngOnInit(): void {
    this.devicesService.getDevices(0, this.pageSize).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.devices = data.devices?.devices;
      this.totalCount = data.devices?.totalCount;
      this.totalFilteredCount = data.devices?.totalFilteredCount;
    }); // start off on first page, i.e. offset 0

    //TODO:: Keep observable alive after error.
  }

  pageChanged(e: PageEvent): void {
    this.pageSize = e.pageSize;
    this.currentPage = e.pageIndex;

    this.devicesService.fetchMore(this.currentPage * this.pageSize, this.pageSize, this.sortOptions, this.filter);
  }

  sortData(e: Sort): void {
    this.sortOptions = e.direction
      ? {
          field: e.active,
          direction: e.direction === 'asc' ? 'ASC' : 'DESC',
        }
      : undefined; // don't send sortOptions field if no direction

    this.devicesService.fetchMore(0, this.pageSize, this.sortOptions, this.filter); // go back to first page, i.e. offset 0
  }

  filterData = (filters: SearchFilterItem[]): void => {
    this.filter = filters.length ? JSON.stringify(filters) : undefined; // don't send filter field if no filter

    this.devicesService.fetchMore(0, this.pageSize, this.sortOptions, this.filter); // go back to first page, i.e. offset 0
  };
}
