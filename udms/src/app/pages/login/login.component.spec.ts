import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, Subscriber } from 'rxjs';
import { AuthService } from 'src/app/services/auth.service';

import { AngularFireModule } from '@angular/fire';
import { RouterTestingModule } from '@angular/router/testing';
import { MatButtonHarness } from '@angular/material/button/testing';

import { environment } from '../../../environments/environment';

import { LoginComponent } from './login.component';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { By } from '@angular/platform-browser';
import { HarnessLoader } from '@angular/cdk/testing';
import { TestbedHarnessEnvironment } from '@angular/cdk/testing/testbed';

class MockAuthService {
  subscriber?: Subscriber<{ authenticated: boolean }>;
  public isAuthenticated(): Observable<{ authenticated: boolean }> {
    return new Observable<{ authenticated: boolean }>((subscriber) => this.subscriber = subscriber);
  }

  public signOut(): void {
    this.subscriber?.next({authenticated: false});
  }
}

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;
  let loader: HarnessLoader;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [LoginComponent],
      imports: [
        AngularFireModule.initializeApp(environment.firebase),
        RouterTestingModule,
        MatProgressSpinnerModule
      ],
      providers: [
        { provide: AuthService, useClass: MockAuthService }
      ]
    })
      .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LoginComponent);
    loader = TestbedHarnessEnvironment.loader(fixture);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show spinner when waiting for authentication results', () => {
    expect(fixture.debugElement.query(By.css('mat-spinner'))).toBeTruthy();
  });

  it('should show correct action button when authentication results arrive', async () => {
    const service = TestBed.inject(AuthService) as MockAuthService;
    service.subscriber?.next({authenticated: true});
    fixture.detectChanges();
    expect(fixture.debugElement.query(By.css('mat-spinner'))).toBeFalsy();
    const signOutButton = await loader.getHarness(MatButtonHarness.with({ text: 'Sign Out'}));
    expect(signOutButton).toBeTruthy();
    signOutButton.click();
    fixture.detectChanges();
    const signInButton = await loader.getHarness(MatButtonHarness.with({ text: 'Sign In'}));
    expect(signInButton).toBeTruthy();
  });

});
