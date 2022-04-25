import { NgModule } from '@angular/core';
import { NavigationComponent } from './navigation.component';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { AppRoutingModule } from '../app-routing.module';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';
import { AuthService } from '../auth/auth.service';

@NgModule({
  declarations: [NavigationComponent],
  imports: [AppRoutingModule, MatToolbarModule, MatButtonModule, MatIconModule, MatMenuModule, CommonModule],
  exports: [NavigationComponent],
})
export class NavigationModule {}
