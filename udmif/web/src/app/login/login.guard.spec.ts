import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject } from 'rxjs';
import { LoginGuard } from './login.guard';
import { AuthService } from '../auth/auth.service';
import { DevicesComponent } from '../devices/devices.component';

describe('LoginGuard', () => {
  let guard: LoginGuard;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockRouter: jasmine.SpyObj<Router>;
  let route: ActivatedRouteSnapshot = { queryParams: {} } as ActivatedRouteSnapshot;
  let state: RouterStateSnapshot;

  beforeEach(() => {
    mockRouter = jasmine.createSpyObj(Router, ['navigateByUrl']);
    mockAuthService = jasmine.createSpyObj(AuthService, ['isLoggedIn$']);
    mockAuthService.isLoggedIn$ = new BehaviorSubject<boolean | null>(null);

    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([{ path: 'devices', component: DevicesComponent }])],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });
    guard = TestBed.inject(LoginGuard);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should redirect to the attempted screen when logged in', () => {
    const returnUrl = '%2Fsites';
    mockAuthService.isLoggedIn$.next(true);
    route.queryParams['returnUrl'] = returnUrl;
    guard.canActivate(route, state).subscribe((res) => {
      expect(mockRouter.navigateByUrl).toHaveBeenCalledWith(returnUrl);
      expect(res).toEqual(false);
    });
  });

  it('should proceed when not logged in', () => {
    mockAuthService.isLoggedIn$.next(false);
    guard.canActivate(route, state).subscribe((res) => {
      expect(mockRouter.navigateByUrl).not.toHaveBeenCalled();
      expect(res).toEqual(true);
    });
  });
});
