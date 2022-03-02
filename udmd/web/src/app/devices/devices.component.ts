import { Component, OnInit } from '@angular/core';
import { Device } from './device.interface';
import { Observable, of } from 'rxjs';
import { pluck } from 'rxjs/operators';
import { DevicesService } from './devices.service';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['name', 'make', 'model', 'site', 'section', 'lastPayload', 'operational', 'tags'];

  loading$: Observable<boolean> = of(false);
  devices$: Observable<Device[]> = of([]);
  totalCount$: Observable<number> = of(0);

  constructor(private devicesService: DevicesService) {}

  ngOnInit() {
    const source$ = this.devicesService.getDevices();

    this.loading$ = source$.pipe(pluck('loading'));
    this.devices$ = source$.pipe(pluck('data', 'devices', 'devices'));
    this.totalCount$ = source$.pipe(pluck('data', 'devices', 'totalCount'));
  }
}
