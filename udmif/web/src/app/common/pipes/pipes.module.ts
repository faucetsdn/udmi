import { NgModule } from '@angular/core';
import { CapitalizePipe } from './capitalize.pipe';
import { OrderByPipe } from './order-by.pipe';
import { ParsePipe } from './parse.pipe';

@NgModule({
  declarations: [CapitalizePipe, ParsePipe, OrderByPipe],
  exports: [CapitalizePipe, ParsePipe, OrderByPipe],
  providers: [CapitalizePipe, ParsePipe, OrderByPipe],
})
export class PipesModule {}
