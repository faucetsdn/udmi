import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Device } from '../devices/devices';

@Component({
  templateUrl: './device.component.html',
  styleUrls: ['./device.component.scss'],
})
export class DeviceComponent implements OnInit {
  fields: (keyof Device)[] = ['name', 'make', 'model', 'site', 'section', 'operational', 'tags'];
  device!: Device;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    const deviceId = this.route.snapshot.params['id'];

    // TODO:: make call to get device by id
    this.device = {
      id: '123',
      name: 'Name2',
      make: 'TEst',
      model: 'Model X',
      site: 'LA',
      section: 'Bay 4',
      operational: true,
      tags: [],
    };
  }
}
