import { NgModule } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { DevicesComponent } from './devices.component';
import { DevicesRoutingModule } from './devices-routing.module';

@NgModule({
  declarations: [DevicesComponent],
  imports: [DevicesRoutingModule, MatTableModule],
})
export class DevicesModule {}
