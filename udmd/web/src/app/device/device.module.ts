import { NgModule } from '@angular/core';
import { DeviceComponent } from './device.component';
import { DeviceRoutingModule } from './device-routing.module';
import { BreadcrumbModule } from '../breadcrumb/breadcrumb.module';

@NgModule({
  declarations: [DeviceComponent],
  imports: [DeviceRoutingModule, BreadcrumbModule],
})
export class DeviceModule {}
