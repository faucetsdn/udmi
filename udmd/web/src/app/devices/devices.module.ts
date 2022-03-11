import { NgModule } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { DevicesComponent } from './devices.component';
import { DevicesRoutingModule } from './devices-routing.module';
import { CommonModule } from '@angular/common';
import { CapitalizePipe } from '../common/pipes/capitalize.pipe';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { SearchFilterModule } from '../search-filter/search-filter.module';
import { RouterModule } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

@NgModule({
  declarations: [DevicesComponent, CapitalizePipe],
  imports: [
    DevicesRoutingModule,
    MatTableModule,
    CommonModule,
    MatPaginatorModule,
    MatSortModule,
    SearchFilterModule,
    RouterModule,
    MatButtonModule,
  ],
})
export class DevicesModule {}
