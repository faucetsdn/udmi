import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BehaviorSubject } from 'rxjs';
import { LoginComponent } from '../login/login.component';
import { AuthGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('AuthGuard', () => {
  let guard: AuthGuard;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockRouter: jasmine.SpyObj<Router>;
  let route: ActivatedRouteSnapshot;
  let state: RouterStateSnapshot = {} as RouterStateSnapshot;

  beforeEach(() => {
    mockRouter = jasmine.createSpyObj(Router, ['navigate']);
    mockAuthService = jasmine.createSpyObj(AuthService, ['isLoggedIn$']);
    mockAuthService.isLoggedIn$ = new BehaviorSubject<boolean | null>(null);

    TestBed.configureTestingModule({
      imports: [RouterTestingModule.withRoutes([{ path: 'login', component: LoginComponent }])],
      providers: [
        { provide: AuthService, useValue: mockAuthService },
        { provide: Router, useValue: mockRouter },
      ],
    });
    guard = TestBed.inject(AuthGuard);
  });

  it('should be created', () => {
    expect(guard).toBeTruthy();
  });

  it('should redirect to the login screen when not logged in and store the attempted url when redirecting', () => {
    const url = '/sites';
    mockAuthService.isLoggedIn$.next(false);
    state.url = url; // attempted to nagivate to sites while not logged in
    guard.canActivate(route, state).subscribe((res) => {
      expect(mockRouter.navigate).toHaveBeenCalledWith(['login'], { queryParams: { returnUrl: url } });
      expect(res).toEqual(false);
    });
  });

  it('should proceed when logged in', () => {
    mockAuthService.isLoggedIn$.next(true);
    guard.canActivate(route, state).subscribe((res) => {
      expect(mockRouter.navigate).not.toHaveBeenCalled();
      expect(res).toEqual(true);
    });
  });
});
