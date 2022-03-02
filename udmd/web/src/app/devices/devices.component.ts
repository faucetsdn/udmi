import { Component, OnInit } from '@angular/core';
import { PageEvent } from '@angular/material/paginator';
import { Device } from './device.interface';
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

  ngOnInit() {
    this.devicesService.getDevices(this.currentPage * this.pageSize, this.pageSize).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.devices = data.devices?.devices;
      this.totalCount = data.devices?.totalCount;
    });
  }

  pageChanged(e: PageEvent) {
    this.pageSize = e.pageSize;
    this.currentPage = e.pageIndex;
    this.devicesService.fetchMore(this.currentPage * this.pageSize, this.pageSize);
  }
}
