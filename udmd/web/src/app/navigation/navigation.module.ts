import { NgModule } from '@angular/core';
import { NavigationComponent } from './navigation.component';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { AppRoutingModule } from '../app-routing.module';

@NgModule({
  declarations: [NavigationComponent],
  imports: [AppRoutingModule, MatToolbarModule, MatButtonModule],
  exports: [NavigationComponent],
})
export class NavigationModule {}
