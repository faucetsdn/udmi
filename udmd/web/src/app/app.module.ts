import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { NavigationModule } from './navigation/navigation.module';
import { DevicesModule } from './devices/devices.module';

@NgModule({
  declarations: [AppComponent],
  imports: [BrowserModule, AppRoutingModule, BrowserAnimationsModule, NavigationModule, DevicesModule],
  bootstrap: [AppComponent],
})
export class AppModule {}
