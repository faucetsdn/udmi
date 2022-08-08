import { SocialAuthService, SocialUser } from '@abacritt/angularx-social-login';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { Apollo } from 'apollo-angular';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let mockSocialAuthService: jasmine.SpyObj<SocialAuthService>;
  let mockApollo: jasmine.SpyObj<Apollo>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(() => {
    mockSocialAuthService = jasmine.createSpyObj(SocialAuthService, ['signIn', 'signOut']);
    mockApollo = jasmine.createSpyObj(Apollo, ['client']);
    mockRouter = jasmine.createSpyObj(Router, ['navigateByUrl']);

    TestBed.configureTestingModule({
      providers: [
        { provide: SocialAuthService, useValue: { ...mockSocialAuthService, authState: new Observable() } },
        { provide: Apollo, useValue: mockApollo },
        { provide: Router, useValue: mockRouter },
      ],
    });
    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should route the user back into the app after login', async () => {
    const user: SocialUser = {} as SocialUser;

    mockApollo.client.clearStore = jasmine.createSpy();
    mockSocialAuthService.signIn.and.returnValue(new Promise((resolve, reject) => resolve(user)));

    await service.loginWithGoogle();

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/devices');
  });

  it('should route the user to the login page and should clear the cache after logout', async () => {
    mockApollo.client.clearStore = jasmine.createSpy();
    mockSocialAuthService.signOut.and.returnValue(new Promise((resolve, reject) => resolve()));

    await service.logout();

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(mockApollo.client.clearStore).toHaveBeenCalled();
  });
});
