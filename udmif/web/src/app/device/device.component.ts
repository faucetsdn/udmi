import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NavigationService } from '../navigation/navigation.service';
import { Device, DeviceModel } from './device';
import { DeviceService } from './device.service';

@Component({
  templateUrl: './device.component.html',
  styleUrls: ['./device.component.scss'],
})
export class DeviceComponent implements OnInit, OnDestroy {
  deviceSubscription!: Subscription;
  fields: (keyof DeviceModel)[] = [
    'make',
    'model',
    'site',
    'section',
    'operational',
    'serialNumber',
    'firmware',
    'lastPayload',
  ];
  device?: Device;
  loading: boolean = true;

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService,
    private navigationService: NavigationService
  ) {}

  ngOnInit(): void {
    const deviceId: string = this.route.snapshot.params['deviceId'];

    this.deviceSubscription = this.deviceService.getDevice(deviceId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.device = data.device;

      this.navigationService.setTitle(this.device?.name);
    });
  }

  ngOnDestroy(): void {
    this.deviceSubscription.unsubscribe();
    this.navigationService.clearTitle();
  }
}
