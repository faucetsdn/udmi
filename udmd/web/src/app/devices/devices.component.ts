import { Component, OnInit } from '@angular/core';
import { Apollo } from 'apollo-angular';
import { GET_DEVICES } from './device.gql';
import { Device, SearchOptions } from './device.interface';

@Component({
  templateUrl: './devices.component.html',
  styleUrls: ['./devices.component.scss'],
})
export class DevicesComponent implements OnInit {
  displayedColumns: string[] = ['name', 'make', 'model', 'site', 'section', 'lastPayload', 'operational', 'tags'];
  loading: boolean = true;
  devices: Device[] = [];
  totalCount: number = 0;

  constructor(private apollo: Apollo) {}

  ngOnInit() {
    this.apollo
      .watchQuery<any>({
        query: GET_DEVICES,
        variables: {
          searchOptions: <SearchOptions>{
            batchSize: 10,
            offset: 0,
          },
        },
      })
      .valueChanges.subscribe(({ data, loading }) => {
        this.loading = loading;
        this.devices = data.devices?.devices;
        this.totalCount = data.devices?.totalCount;
      });
  }
}
