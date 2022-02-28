import { Component, OnInit } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_DEVICES } from './device.gql';
import { Device } from './device.interface';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['name', 'make', 'model', 'site', 'section', 'lastPayload', 'operational', 'tags'];
  loading: boolean = true;
  devices: Device[] = [];

  constructor(private apollo: Apollo) {}

  ngOnInit() {
    this.apollo
      .watchQuery<any>({
        query: GET_DEVICES,
      })
      .valueChanges.subscribe(({ data, loading }) => {
        this.loading = loading;
        this.devices = data.devices;
      });
  }
}
