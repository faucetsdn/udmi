import { NgModule } from '@angular/core';
import { DeviceComponent } from './device.component';
import { DeviceRoutingModule } from './device-routing.module';
import { BreadcrumbModule } from '../breadcrumb/breadcrumb.module';
import { MatCardModule } from '@angular/material/card';
import { CommonModule } from '@angular/common';
import { PipesModule } from '../common/pipes/pipes.module';

@NgModule({
  declarations: [DeviceComponent],
  imports: [DeviceRoutingModule, BreadcrumbModule, MatCardModule, CommonModule, PipesModule],
})
export class DeviceModule {}
