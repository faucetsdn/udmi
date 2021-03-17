import { Component, OnDestroy, OnInit } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { Device, DeviceSearch, DevicesService } from '../../services/devices.service';
import { Origin, OriginsService } from '../../services/origins.service';

@Component({
  selector: 'app-devices-overview',
  templateUrl: './devices-overview.component.html',
  styleUrls: ['./devices-overview.component.scss']
})
export class DevicesOverviewComponent implements OnInit, OnDestroy {

  subscriptions: { [key: string]: Subscription } = {};
  origin?: Origin;
  pageSize = 30;
  dataSource?: Observable<Device[]>;
  displayColumns: string[] = ['mac', 'ip', 'port', 'vlan', 'role'];

  constructor(private originService: OriginsService, private service: DevicesService) { }

  ngOnInit(): void {
    this.subscriptions.origin = this.originService.onOriginChange().subscribe((origin) => {
      this.origin = origin;
      this.getDevices(origin);
    });
  }

  getDevices(origin: Origin, search?: DeviceSearch): void {
    if (this.subscriptions.devices) {
      this.subscriptions.devices.unsubscribe();
    }
    if (origin) {
      this.dataSource = this.service.get(origin, this.pageSize, search);
    }
  }

  ngOnDestroy(): void {
    Object.values(this.subscriptions).forEach((subscription) => {
      subscription.unsubscribe();
    });
  }
}
