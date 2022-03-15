import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DevicesComponent } from './devices.component';
import { DevicesModule } from './devices.module';
import { DevicesService } from './devices.service';
import { of } from 'rxjs';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { DevicesQueryResponse, SortOptions } from './devices';
import { SearchFilterItem } from '../search-filter/search-filter';
import { Device } from '../device/device';
import { ApolloQueryResult } from '@apollo/client/core';

describe('DevicesComponent', () => {
  let component: DevicesComponent;
  let fixture: ComponentFixture<DevicesComponent>;
  let mockDevicesService: jasmine.SpyObj<DevicesService>;
  let devices: Device[] = [
    {
      id: 'device-id-123',
      name: 'device one',
      tags: [],
    },
  ];

  beforeEach(async () => {
    mockDevicesService = jasmine.createSpyObj(DevicesService, ['getDevices', 'fetchMore']);
    mockDevicesService.getDevices.and.returnValue(
      of(<ApolloQueryResult<DevicesQueryResponse>>{
        data: {
          devices: {
            devices,
            totalCount: 1,
            totalFilteredCount: 1,
          },
        },
        loading: false,
      })
    );

    await TestBed.configureTestingModule({
      imports: [DevicesModule, BrowserAnimationsModule],
      providers: [{ provide: DevicesService, useValue: mockDevicesService }],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DevicesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should store the devices in memory', () => {
    expect(mockDevicesService.getDevices).toHaveBeenCalledWith(0, 10);
    expect(component.devices).toEqual(devices);
    expect(component.totalCount).toEqual(1);
    expect(component.totalFilteredCount).toEqual(1);
    expect(component.loading).toBeFalse();
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
    expect(mockDevicesService.fetchMore).toHaveBeenCalledWith(22, 11, undefined, undefined);
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
    expect(mockDevicesService.fetchMore).toHaveBeenCalledWith(0, 10, sortOptions, undefined);
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
    expect(mockDevicesService.fetchMore).toHaveBeenCalledWith(0, 10, undefined, filter);
  });
});
