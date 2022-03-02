import { Component, OnInit } from '@angular/core';
import { Device } from './device.interface';
import { DevicesService } from './devices.service';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['name', 'make', 'model', 'site', 'section', 'lastPayload', 'operational', 'tags'];
  loading: boolean = false;
  devices: Device[] = [];
  totalCount: number = 0;

  constructor(private devicesService: DevicesService) {}

  ngOnInit() {
    this.devicesService.getDevices().subscribe(({ data, loading }) => {
      this.loading = loading;
      this.devices = data.devices?.devices;
      this.totalCount = data.devices?.totalCount;
    });
  }

  fetchMore() {
    this.devicesService.fetchMore(10);
  }
}
