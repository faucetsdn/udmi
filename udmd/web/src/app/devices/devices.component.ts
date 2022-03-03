import { Component, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { Device, SortOptions } from './device.interface';
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
  currentPage: number = 0;
  pageSize: number = 10;
  pageSizeOptions: number[] = [10, 25, 50, 100];

  constructor(private devicesService: DevicesService) {}

  ngOnInit(): void {
    this.devicesService.getDevices(this.currentPage * this.pageSize, this.pageSize).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.devices = data.devices?.devices;
      this.totalCount = data.devices?.totalCount;
    });
  }

  pageChanged(e: PageEvent): void {
    this.pageSize = e.pageSize;
    this.currentPage = e.pageIndex;

    this.devicesService.fetchMore(this.currentPage * this.pageSize, this.pageSize);
  }

  sortData(e: Sort): void {
    const sortOptions: SortOptions | undefined = e.direction
      ? {
          field: e.active,
          direction: e.direction === 'asc' ? 'ASC' : 'DESC',
        }
      : undefined;

    this.devicesService.fetchMore(0, this.pageSize, sortOptions); // go back to first page, i.e. offset 0
  }
}
