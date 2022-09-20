import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DeviceErrorsComponent } from './device-errors.component';
import { DeviceErrorsRoutingModule } from './device-errors-routing.module';
import { PipesModule } from '../common/pipes/pipes.module';
import { MatTableModule } from '@angular/material/table';
import { MatSortModule } from '@angular/material/sort';

@NgModule({
  declarations: [DeviceErrorsComponent],
  imports: [DeviceErrorsRoutingModule, CommonModule, MatTableModule, MatSortModule, PipesModule],
})
export class DeviceErrorsModule {}
