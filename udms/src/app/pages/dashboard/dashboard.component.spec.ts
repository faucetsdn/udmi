import { TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { AngularFireModule } from '@angular/fire';

import { environment } from '../../../environments/environment';
import { DevicesOverviewComponent } from '../../components/devices-overview/devices-overview.component';
import { DashboardComponent } from './dashboard.component';
import { SystemStateComponent } from 'src/app/components/system-state/system-state.component';

describe('DashboardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        AngularFireModule.initializeApp(environment.firebase),
        RouterTestingModule
      ],
      declarations: [
        DashboardComponent,
        DevicesOverviewComponent,
        SystemStateComponent
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });
});
