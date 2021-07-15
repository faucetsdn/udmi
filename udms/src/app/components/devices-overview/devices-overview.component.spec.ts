import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';
import { Observable, of, Subscriber } from 'rxjs';

import { AngularFireModule } from '@angular/fire';
import { AppRoutingModule } from '../../app-routing.module';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { MatTableHarness } from '@angular/material/table/testing';

import { environment } from '../../../environments/environment';
import { DevicesOverviewComponent } from './devices-overview.component';
import { NoOriginComponent } from '../no-origin/no-origin.component';
import { Origin, OriginsService } from '../../services/origins.service';
import { By } from '@angular/platform-browser';
import { Device, DevicesService } from 'src/app/services/devices.service';
import { SystemType } from 'src/app/enums';


class MockOriginsService {
  subscriber?: Subscriber<Origin>;

  public onOriginChange(): Observable<Origin> {
    return new Observable<Origin>((subscriber) => this.subscriber = subscriber);
  }
}

class MockDevicesService {
  devices: Device[] = [];

  public get(): Observable<Device[]> {
    return of(this.devices);
  }
}

describe('DevicesOverviewComponent', () => {
  let component: DevicesOverviewComponent;
  let fixture: ComponentFixture<DevicesOverviewComponent>;
  let loader: HarnessLoader;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DevicesOverviewComponent, NoOriginComponent ],
      imports: [
        AngularFireModule.initializeApp(environment.firebase),
        MatIconModule,
        AppRoutingModule,
        MatTableModule
      ],
      providers: [
        { provide: OriginsService, useClass: MockOriginsService },
        { provide: DevicesService, useClass: MockDevicesService }
      ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DevicesOverviewComponent);
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
    const table = fixture.debugElement.query(By.css('table'));
    expect(table).toBeFalsy();
  });

  it('should show data when an origin is selected', async () => {
    const devicesService = TestBed.inject(DevicesService) as any as MockDevicesService;
    devicesService.devices = [{
      mac: 'mac1',
      ip: 'ip1',
      port: 1,
      vlan: 2,
      role: 'role'
    }];
    const originService = TestBed.inject(OriginsService) as any as MockOriginsService;
    originService.subscriber?.next({
      type: SystemType.DAQ,
      id: 'origin1'
    });
    component.ngOnInit();
    await component.dataSource?.toPromise();
    fixture.detectChanges();
    const table = await loader.getHarness(MatTableHarness);
    expect(table).toBeTruthy();

    const headerRow = await table.getHeaderRows();
    expect(headerRow.length).toEqual(1);
    const headers = await headerRow[0].getCells();
    expect(headers.length).toEqual(component.displayColumns.length);
    component.displayColumns.forEach(async (header, i) => {
      expect(header).toEqual((await headers[i].getText()).toLowerCase());
    });

    const rows = await table.getRows();
    expect(rows.length).toEqual(1);
    const tableData = await rows[0].getCellTextByColumnName();
    Object.keys(devicesService.devices[0]).forEach((key) => {
      const value = devicesService.devices[0][key as keyof Device];
      expect(value + '').toEqual(tableData[key]);
    });
  });
});
