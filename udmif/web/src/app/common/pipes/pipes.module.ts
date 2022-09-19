import { NgModule } from '@angular/core';
import { CapitalizePipe } from './capitalize.pipe';
import { ParsePipe } from './parse.pipe';

@NgModule({
  declarations: [CapitalizePipe, ParsePipe],
  exports: [CapitalizePipe, ParsePipe],
  providers: [CapitalizePipe, ParsePipe],
})
export class PipesModule {}
