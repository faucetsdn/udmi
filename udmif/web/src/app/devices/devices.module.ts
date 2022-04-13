import { NgModule } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { DevicesComponent } from './devices.component';
import { DevicesRoutingModule } from './devices-routing.module';
import { CommonModule } from '@angular/common';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatSortModule } from '@angular/material/sort';
import { SearchFilterModule } from '../search-filter/search-filter.module';
import { MatButtonModule } from '@angular/material/button';
import { PipesModule } from '../common/pipes/pipes.module';

@NgModule({
  declarations: [DevicesComponent],
  imports: [
    DevicesRoutingModule,
    MatTableModule,
    CommonModule,
    MatPaginatorModule,
    MatSortModule,
    SearchFilterModule,
    MatButtonModule,
    PipesModule,
  ],
})
export class DevicesModule {}
