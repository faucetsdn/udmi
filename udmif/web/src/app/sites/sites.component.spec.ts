import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SitesComponent } from './sites.component';
import { SitesModule } from './sites.module';
import { SitesService } from './sites.service';
import { of } from 'rxjs';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SitesQueryResponse, SitesQueryVariables, SortOptions } from './sites';
import { SearchFilterItem } from '../search-filter/search-filter';
import { Site } from '../site/site';
import { ApolloQueryResult } from '@apollo/client/core';
import { QueryRef } from 'apollo-angular';

describe('SitesComponent', () => {
  let component: SitesComponent;
  let fixture: ComponentFixture<SitesComponent>;
  let mockSitesService: jasmine.SpyObj<SitesService>;
  let refetch: jasmine.Spy;
  let sites: Site[] = [
    {
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
    },
  ];

  beforeEach(async () => {
    refetch = jasmine.createSpy().and.resolveTo(<ApolloQueryResult<SitesQueryResponse>>{
      data: {},
    });
    mockSitesService = jasmine.createSpyObj(SitesService, ['getSites']);
    mockSitesService.getSites.and.returnValue(<QueryRef<SitesQueryResponse, SitesQueryVariables>>(<unknown>{
      valueChanges: of(<ApolloQueryResult<SitesQueryResponse>>{
        data: {
          sites: {
            sites,
            totalCount: 1,
            totalFilteredCount: 1,
          },
        },
      }),
      refetch,
    }));

    await TestBed.configureTestingModule({
      imports: [SitesModule, BrowserAnimationsModule],
      providers: [{ provide: SitesService, useValue: mockSitesService }],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SitesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should store the sites in memory', () => {
    expect(mockSitesService.getSites).toHaveBeenCalledWith(0, 10);
    expect(component.sites).toEqual(sites);
    expect(component.totalCount).toEqual(1);
    expect(component.totalFilteredCount).toEqual(1);
  });

  it('should change pages', () => {
    const e: PageEvent = {
      pageSize: 11,
      pageIndex: 2,
      length: 100,
    };

    component.pageChanged(e);

    expect(component.pageSize).toEqual(11);
    expect(component.currentPage).toEqual(2);
    expect(refetch).toHaveBeenCalledWith({
      searchOptions: {
        offset: 22,
        batchSize: 11,
        sortOptions: undefined,
        filter: undefined,
      },
    });
  });

  it('should sort', () => {
    const e: Sort = {
      active: 'site',
      direction: 'asc',
    };

    component.sortData(e);

    const sortOptions: SortOptions = {
      direction: 'ASC',
      field: 'site',
    };

    expect(component.sortOptions).toEqual(sortOptions);
    expect(refetch).toHaveBeenCalledWith({
      searchOptions: {
        offset: 0,
        batchSize: 10,
        sortOptions,
        filter: undefined,
      },
    });
  });

  it('should filter', () => {
    const filters: SearchFilterItem[] = [
      {
        field: 'name',
        operator: '=',
        value: 'AHU1',
      },
    ];

    component.filterData(filters);

    const filter: string = JSON.stringify(filters);

    expect(component.filter).toEqual(filter);
    expect(refetch).toHaveBeenCalledWith({
      searchOptions: {
        offset: 0,
        batchSize: 10,
        sortOptions: undefined,
        filter,
      },
    });
  });
});
