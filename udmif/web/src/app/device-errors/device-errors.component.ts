import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSort, MatSortable } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { ParsePipe } from '../common/pipes/parse.pipe';
import { DeviceService } from '../device/device.service';
import { DeviceError } from './device-errors';

@Component({
  templateUrl: './device-errors.component.html',
  styleUrls: ['./device-errors.component.scss'],
})
export class DeviceErrorsComponent implements OnInit {
  deviceSubscription!: Subscription;
  errors: DeviceError[] = [];
  displayedColumns: (keyof DeviceError)[] = ['timestamp', 'message', 'detail', 'level'];
  loading: boolean = true;
  dataSource = new MatTableDataSource<DeviceError>();

  @ViewChild(MatSort) sort!: MatSort;

  constructor(private route: ActivatedRoute, private deviceService: DeviceService, private parsePipe: ParsePipe) {}

  ngOnInit(): void {
    const deviceId: string = this.route.snapshot.params['deviceId'];

    this.deviceSubscription = this.deviceService.getDevice(deviceId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.errors = this.parsePipe.transform(data.device?.validation)?.errors ?? [];

      // Init the table data source so sorting will work natively.
      this.dataSource = new MatTableDataSource(this.errors);
      this.sort.sort({ id: 'timestamp', start: 'desc' } as MatSortable);
      this.dataSource.sort = this.sort;
    });
  }

  ngOnDestroy(): void {
    this.deviceSubscription.unsubscribe();
  }
}
