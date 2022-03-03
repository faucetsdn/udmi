import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DevicesComponent } from './devices.component';
import { DevicesModule } from './devices.module';
import { DevicesService } from './devices.service';
import { of } from 'rxjs';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { PageEvent } from '@angular/material/paginator';
import { Sort } from '@angular/material/sort';
import { SortOptions } from './device.interface';

describe('DevicesComponent', () => {
  let component: DevicesComponent;
  let fixture: ComponentFixture<DevicesComponent>;
  let mockDevicesService: jasmine.SpyObj<DevicesService>;

  beforeEach(async () => {
    mockDevicesService = jasmine.createSpyObj(DevicesService, ['getDevices', 'fetchMore']);
    mockDevicesService.getDevices.and.returnValue(of());

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

  it('should compile', () => {
    expect(component).toBeTruthy();
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
    expect(mockDevicesService.fetchMore).toHaveBeenCalledWith(22, 11);
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

    expect(mockDevicesService.fetchMore).toHaveBeenCalledWith(0, 10, sortOptions);
  });
});
