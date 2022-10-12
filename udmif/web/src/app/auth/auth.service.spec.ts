import { SocialAuthService } from '@abacritt/angularx-social-login';
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
    mockSocialAuthService = jasmine.createSpyObj(SocialAuthService, ['signOut', 'refreshAuthToken']);
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

  it('should route the user to the login page and should clear the cache after logout', async () => {
    mockApollo.client.clearStore = jasmine.createSpy();
    mockSocialAuthService.signOut.and.resolveTo();

    await service.logout();

    expect(mockRouter.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(mockApollo.client.clearStore).toHaveBeenCalled();
  });

  it('should not do anything when logging out fails', async () => {
    mockApollo.client.clearStore = jasmine.createSpy();
    mockSocialAuthService.signOut.and.rejectWith();

    await service.logout();

    expect(mockRouter.navigateByUrl).not.toHaveBeenCalled();
    expect(mockApollo.client.clearStore).not.toHaveBeenCalled();
  });

  it('should logout when refreshing the token fails', async () => {
    spyOn(service, 'logout');
    mockSocialAuthService.refreshAuthToken.and.rejectWith();

    await service.refreshToken();

    expect(service.logout).toHaveBeenCalled();
  });

  it('should not logout when refreshing the token succeeds', async () => {
    spyOn(service, 'logout');
    mockSocialAuthService.refreshAuthToken.and.resolveTo();

    await service.refreshToken();

    expect(service.logout).not.toHaveBeenCalled();
  });
});
