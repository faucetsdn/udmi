import { NgModule } from '@angular/core';
import { PageNotFoundComponent } from './page-not-found.component';
import { MatButtonModule } from '@angular/material/button';
import { PageNotFoundRoutingModule } from './page-not-found-routing.module';

@NgModule({
  declarations: [PageNotFoundComponent],
  imports: [PageNotFoundRoutingModule, MatButtonModule],
})
export class PageNotFoundModule {}
