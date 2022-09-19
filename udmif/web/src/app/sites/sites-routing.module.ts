import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { SitesComponent } from './sites.component';

const routes: Routes = [
  { path: '', component: SitesComponent },
  { path: ':siteId', loadChildren: () => import('../site/site.module').then((m) => m.SiteModule) },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class SitesRoutingModule {}
