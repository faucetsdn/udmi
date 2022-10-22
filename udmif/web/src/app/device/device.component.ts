import { Component, OnDestroy, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Subscription } from 'rxjs';
import { NavigationService } from '../navigation/navigation.service';
import { Device, DeviceDetail } from './device';
import { DeviceService } from './device.service';
import { DeviceConstants } from './device.constants';

@Component({
  templateUrl: './device.component.html',
  styleUrls: ['./device.component.scss'],
})
export class DeviceComponent implements OnInit, OnDestroy {
  deviceSubscription!: Subscription;
  fields: DeviceDetail[] = this.deviceConstants.deviceDetails;
  device?: Device;
  loading: boolean = true;

  constructor(
    private route: ActivatedRoute,
    private deviceService: DeviceService,
    private navigationService: NavigationService,
    private deviceConstants: DeviceConstants
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
