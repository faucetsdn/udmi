import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { DevicesComponent } from './devices/devices.component';
import { NavigationModule } from './navigation/navigation.module';

@NgModule({
  declarations: [AppComponent, DevicesComponent],
  imports: [BrowserModule, AppRoutingModule, BrowserAnimationsModule, NavigationModule],
  bootstrap: [AppComponent],
})
export class AppModule {}
