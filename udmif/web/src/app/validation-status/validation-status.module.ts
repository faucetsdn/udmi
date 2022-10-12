import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ValidationStatusComponent } from './validation-status.component';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { PipesModule } from '../common/pipes/pipes.module';

@NgModule({
  declarations: [ValidationStatusComponent],
  imports: [CommonModule, MatCardModule, MatIconModule, PipesModule],
  exports: [ValidationStatusComponent],
})
export class ValidationStatusModule {}
