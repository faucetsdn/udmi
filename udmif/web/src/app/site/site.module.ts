import { NgModule } from '@angular/core';
import { SiteComponent } from './site.component';
import { SiteRoutingModule } from './site-routing.module';
import { MatCardModule } from '@angular/material/card';
import { CommonModule } from '@angular/common';
import { PipesModule } from '../common/pipes/pipes.module';
import { ValidationStatusModule } from '../validation-status/validation-status.module';
import { MatTabsModule } from '@angular/material/tabs';

@NgModule({
  declarations: [SiteComponent],
  imports: [SiteRoutingModule, MatCardModule, CommonModule, PipesModule, MatTabsModule, ValidationStatusModule],
})
export class SiteModule {}
