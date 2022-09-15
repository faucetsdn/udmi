import { NgModule } from '@angular/core';
import { SiteComponent } from './site.component';
import { SiteRoutingModule } from './site-routing.module';
import { BreadcrumbModule } from '../breadcrumb/breadcrumb.module';
import { MatCardModule } from '@angular/material/card';
import { CommonModule } from '@angular/common';
import { PipesModule } from '../common/pipes/pipes.module';
import { MatTabsModule } from '@angular/material/tabs';

@NgModule({
  declarations: [SiteComponent],
  imports: [SiteRoutingModule, BreadcrumbModule, MatCardModule, CommonModule, PipesModule, MatTabsModule],
})
export class SiteModule {}
