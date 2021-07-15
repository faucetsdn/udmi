import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';

import { AngularFireModule } from '@angular/fire';
import { AppRoutingModule } from './app-routing.module';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonHarness } from '@angular/material/button/testing';
import { MatMenuModule } from '@angular/material/menu';
import { MatMenuHarness, MatMenuItemHarness } from '@angular/material/menu/testing';

import { environment } from '../environments/environment';
import { AppComponent } from './app.component';
import { By } from '@angular/platform-browser';
import { AuthService, User } from './services/auth.service';
import { Origin, OriginsService } from './services/origins.service';

import { Observable, of } from 'rxjs';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SystemType } from './enums';

class MockAuthService {
  user?: User;
  public getUser(): Observable<User | undefined> {
    return of(this.user);
  }
}

class MockOriginsService {
  origins: Origin[] = [];
  public getOrigins(): Observable<Origin[]> {
    return of(this.origins);
  }

  public changeOrigin(origin: Origin): void { }
}

describe('AppComponent', () => {
  let component: AppComponent;
  let fixture: ComponentFixture<AppComponent>;
  let loader: HarnessLoader;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [AppComponent],
      imports: [
        AngularFireModule.initializeApp(environment.firebase),
        MatToolbarModule,
        MatMenuModule,
        MatIconModule,
        AppRoutingModule,
        BrowserAnimationsModule
      ],
      providers: [
        { provide: AuthService, useClass: MockAuthService },
        { provide: OriginsService, useClass: MockOriginsService }
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(AppComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('Should have title', () => {
    const toolbar = fixture.debugElement.query(By.css('mat-toolbar'));
    expect(toolbar).toBeTruthy();
    const title = toolbar.query(By.css('.title'));
    expect(title).toBeTruthy();
    expect(title.nativeElement.textContent).toEqual(component.title);
  });

  it('Should show blank toolbar when not logged in', () => {
    const toolbar = fixture.debugElement.query(By.css('mat-toolbar'));
    expect(toolbar.query(By.css('mat-menu'))).toBeFalsy();
  });

  it('Should show user when logged in', async () => {
    const service = TestBed.inject(AuthService) as MockAuthService;
    service.user = {
      id: 'test',
      name: 'User'
    };
    component.ngOnInit();
    fixture.detectChanges();
    const userButton = await loader.getHarness(MatButtonHarness.with({ text: 'User' }));
    expect(userButton).toBeTruthy();
    userButton.click();
    fixture.detectChanges();
    const userMenu = await loader.getHarness(MatMenuHarness.with({ triggerText: 'User' }));
    expect(userMenu.isOpen()).toBeTruthy();
    const signOutButton = await userMenu.getHarness(MatMenuItemHarness.with({ text: 'Sign Out' }));
    expect(signOutButton).toBeTruthy();
  });

  it('Should show origins when logged in', async () => {
    const service = TestBed.inject(AuthService) as MockAuthService;
    service.user = {
      id: 'test',
      name: 'User'
    };
    component.ngOnInit();
    fixture.detectChanges();
    const originsButton = await loader.getHarness(MatButtonHarness.with({ text: /Choose Origin/ }));
    expect(originsButton).toBeTruthy();
    originsButton.click();
    fixture.detectChanges();
    const originsMenu = await loader.getHarness(MatMenuHarness.with({ triggerText: /Choose Origin/ }));
    expect(originsMenu.isOpen()).toBeTruthy();
    const noOrigins = await originsMenu.getHarness(MatMenuItemHarness.with({ text: 'No Origins Available' }));
    expect(noOrigins).toBeTruthy();
  });

  it('Should select first origin when logged in', async () => {
    const service = TestBed.inject(AuthService) as MockAuthService;
    service.user = {
      id: 'test',
      name: 'User'
    };
    const originservice = TestBed.inject(OriginsService) as any as MockOriginsService;
    originservice.origins = [{
      type: SystemType.DAQ,
      id: 'origin1'
    }, {
      type: SystemType.UDMS,
      id: 'origin2'
    }];
    component.ngOnInit();
    // Wait for origins to be resolved before detecting changes.
    await component.origins.toPromise();
    fixture.detectChanges();
    const originsButton = await loader.getHarness(MatButtonHarness.with({ text: /origin1/ }));
    expect(originsButton).toBeTruthy();
    originsButton.click();
    fixture.detectChanges();
    const originsMenu = await loader.getHarness(MatMenuHarness.with({ triggerText: /origin1/ }));
    expect(originsMenu.isOpen()).toBeTruthy();
    const origin1 = await originsMenu.getHarness(MatMenuItemHarness.with({text: 'origin1'}));
    expect(origin1).toBeTruthy();
    const origin2 = await originsMenu.getHarness(MatMenuItemHarness.with({text: 'origin2'}));
    expect(origin2).toBeTruthy();
  });

});
