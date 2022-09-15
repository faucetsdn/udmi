import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { Site, SiteQueryResponse } from './site';
import { SiteComponent } from './site.component';
import { SiteModule } from './site.module';
import { SiteService } from './site.service';

describe('SiteComponent', () => {
  let component: SiteComponent;
  let fixture: ComponentFixture<SiteComponent>;
  let mockSiteService: jasmine.SpyObj<SiteService>;
  let site: Site = {
    id: 'site-id-123',
    name: 'site one',
  };

  beforeEach(async () => {
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

  it('should store the site in memory', () => {
    expect(mockSiteService.getSite).toHaveBeenCalledWith('site-id-123');
    expect(component.site).toEqual(site);
    expect(component.loading).toBeFalse();
  });
});
