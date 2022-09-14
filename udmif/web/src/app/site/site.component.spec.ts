import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { NavigationService } from '../navigation/navigation.service';
import { Site, SiteQueryResponse } from './site';
import { SiteComponent } from './site.component';
import { SiteModule } from './site.module';
import { SiteService } from './site.service';

describe('SiteComponent', () => {
  let component: SiteComponent;
  let fixture: ComponentFixture<SiteComponent>;
  let mockNavigationService: jasmine.SpyObj<NavigationService>;
  let mockSiteService: jasmine.SpyObj<SiteService>;
  let site: Site = {
    id: 'site-id-123',
    name: 'site one',
    totalDevicesCount: 0,
    correctDevicesCount: 0,
    missingDevicesCount: 0,
    errorDevicesCount: 0,
    extraDevicesCount: 0,
    lastValidated: '2022-04-24T02:54:51Z',
    percentValidated: 0,
    totalDeviceErrorsCount: 0,
    validation: '',
  };

  beforeEach(async () => {
    mockNavigationService = jasmine.createSpyObj(NavigationService, ['setTitle', 'clearTitle']);
    mockSiteService = jasmine.createSpyObj(SiteService, ['getSite']);
    mockSiteService.getSite.and.returnValue(
      of(<ApolloQueryResult<SiteQueryResponse>>{
        data: {
          site,
        },
        loading: false,
      })
    );

    await TestBed.configureTestingModule({
      imports: [SiteModule, RouterTestingModule],
      providers: [
        { provide: NavigationService, useValue: mockNavigationService },
        { provide: SiteService, useValue: mockSiteService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              params: {
                id: 'site-id-123',
              },
            },
          },
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SiteComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize correctly', () => {
    expect(mockNavigationService.setTitle).toHaveBeenCalledWith('site one');
    expect(mockSiteService.getSite).toHaveBeenCalledWith('site-id-123');
    expect(component.site).toEqual(site);
    expect(component.loading).toBeFalse();
  });

  it('should cleanup correctly', () => {
    spyOn(component.siteSubscription, 'unsubscribe');

    fixture.destroy();

    expect(component.siteSubscription.unsubscribe).toHaveBeenCalled();
    expect(mockNavigationService.clearTitle).toHaveBeenCalled();
  });
});
