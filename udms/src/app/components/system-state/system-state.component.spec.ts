import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { Observable, Subscriber } from 'rxjs';

import { AngularFireModule } from '@angular/fire';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MatCardModule } from '@angular/material/card';
import { MatCardHarness } from '@angular/material/card/testing';
import { MatIconModule } from '@angular/material/icon';
import { MatIconHarness } from '@angular/material/icon/testing';

import { environment } from '../../../environments/environment';
import { NoOriginComponent } from '../no-origin/no-origin.component';
import { Origin, OriginsService } from '../../services/origins.service';
import { SystemStateComponent } from './system-state.component';
import { By } from '@angular/platform-browser';
import { State, SystemType } from 'src/app/enums';
import { MetricsService, SystemMetric } from 'src/app/services/metrics.service';
import { NgxChartsModule } from '@swimlane/ngx-charts';

class MockOriginsService {
  subscriber?: Subscriber<Origin>;

  public onOriginChange(): Observable<Origin> {
    return new Observable<Origin>((subscriber) => this.subscriber = subscriber);
  }
}

class MockMetricsService {
  subscriber?: Subscriber<SystemMetric>;

  public getLatest(origin: Origin): Observable<SystemMetric> {
    return new Observable<SystemMetric>((subscriber) => this.subscriber = subscriber);
  }
}

describe('SystemStateComponent', () => {
  let component: SystemStateComponent;
  let fixture: ComponentFixture<SystemStateComponent>;
  let loader: HarnessLoader;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [SystemStateComponent, NoOriginComponent],
      imports: [
        AngularFireModule.initializeApp(environment.firebase),
        NgxChartsModule,
        BrowserAnimationsModule,
        MatCardModule,
        MatIconModule
      ],
      providers: [
        { provide: OriginsService, useClass: MockOriginsService },
        { provide: MetricsService, useClass: MockMetricsService }
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SystemStateComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show no origin when no origins are selected', () => {
    const noOrigin = fixture.debugElement.query(By.directive(NoOriginComponent));
    expect(noOrigin).toBeTruthy();
    const chart = fixture.debugElement.query(By.css('ngx-charts-pie-chart'));
    expect(chart).toBeFalsy();
  });

  it('should show system state when origin is selected', async () => {
    const metricsService = TestBed.inject(MetricsService) as any as MockMetricsService;
    const originService = TestBed.inject(OriginsService) as any as MockOriginsService;
    originService.subscriber?.next({
      type: SystemType.DAQ,
      id: 'origin1'
    });
    component.ngOnInit();
    await fixture.whenStable();
    const systemMetric: SystemMetric = {
      timestamp: new Date(),
      state: State.damaged,
      detail: 'detail',
      lastChanged: new Date(),
      changeCount: 10,
      controller: {
        timestamp: new Date(),
        state: State.damaged,
        detail: 'controller detail'
      }
    };
    metricsService.subscriber?.next(systemMetric);
    fixture.detectChanges();
    const systemStateText = fixture.debugElement.query(By.css('.system-text'));
    expect(systemStateText).toBeTruthy();
    expect(systemStateText.nativeElement.textContent).toEqual('System Damaged');

    component.onSelectMetric({ label: 'controller' });
    fixture.detectChanges();
    const selectedMetric = await loader.getHarness(MatCardHarness);
    const title = await selectedMetric.getTitleText();
    expect(title).toEqual('Controller');

    const content = await selectedMetric.getText();
    expect(content.includes('Detail: controller detail')).toBeTrue();

    const closeButton = fixture.debugElement.query(By.css('.close-icon mat-icon'));
    expect(closeButton).toBeTruthy();
    closeButton.nativeElement.click();
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('mat-card'))).toBeFalsy();
  });

});
