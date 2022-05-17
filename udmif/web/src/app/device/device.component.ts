import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Device, DeviceModel } from './device';
import { DeviceService } from './device.service';

@Component({
  templateUrl: './device.component.html',
  styleUrls: ['./device.component.scss'],
})
export class DeviceComponent implements OnInit {
  fields: (keyof DeviceModel)[] = [
    'name',
    'make',
    'model',
    'site',
    'section',
    'operational',
    'serialNumber',
    'firmware',
    'lastPayload',
    'tags',
  ];
  device: Device | null = null;
  loading: boolean = true;

  constructor(private route: ActivatedRoute, private deviceService: DeviceService) {}

  ngOnInit(): void {
    const deviceId: string = this.route.snapshot.params['id'];

    this.deviceService.getDevice(deviceId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.device = data.device;
    });
  }
}
