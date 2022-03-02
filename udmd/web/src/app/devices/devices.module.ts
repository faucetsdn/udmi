import { NgModule } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { DevicesComponent } from './devices.component';
import { DevicesRoutingModule } from './devices-routing.module';
import { CommonModule } from '@angular/common';
import { CapitalizePipe } from '../common/pipes/capitalize.pipe';
import { MatIconModule } from '@angular/material/icon';

@NgModule({
  declarations: [DevicesComponent, CapitalizePipe],
  imports: [DevicesRoutingModule, MatTableModule, CommonModule, MatIconModule],
})
export class DevicesModule {}
