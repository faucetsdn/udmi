import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  templateUrl: './device.component.html',
  styleUrls: ['./device.component.scss'],
})
export class DeviceComponent implements OnInit {
  deviceId: string | null = null;

  constructor(private route: ActivatedRoute) {}

  ngOnInit(): void {
    this.deviceId = this.route.snapshot.params['id'];
  }
}
