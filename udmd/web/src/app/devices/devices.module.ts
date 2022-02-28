import { NgModule } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { DevicesComponent } from './devices.component';

@NgModule({
  declarations: [DevicesComponent],
  imports: [MatTableModule],
  exports: [DevicesComponent],
})
export class DevicesModule {}
