import { NgModule } from '@angular/core';
import { DeviceComponent } from './device.component';
import { DeviceRoutingModule } from './device-routing.module';
import { MatCardModule } from '@angular/material/card';
import { CommonModule } from '@angular/common';
import { PipesModule } from '../common/pipes/pipes.module';
import { MatTabsModule } from '@angular/material/tabs';
import { ValidationStatusModule } from '../validation-status/validation-status.module';

@NgModule({
  declarations: [DeviceComponent],
  imports: [DeviceRoutingModule, MatCardModule, CommonModule, PipesModule, MatTabsModule, ValidationStatusModule],
})
export class DeviceModule {}
