import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatChipInputEvent } from '@angular/material/chips';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { DeviceDistinctQueryResult } from '../devices/devices';
import { DevicesService } from '../devices/devices.service';
import { SearchFilterComponent } from './search-filter.component';
import { SearchFilterModule } from './search-filter.module';

describe('SearchFilterComponent', () => {
  let component: SearchFilterComponent;
  let fixture: ComponentFixture<SearchFilterComponent>;
  let mockDevicesService: jasmine.SpyObj<DevicesService>;

  function injectViewValue(value: string): void {
    let e: MatAutocompleteSelectedEvent = {
      option: {
        value,
      },
    } as MatAutocompleteSelectedEvent;

    component.selected(e);
    tick(); // clear setTimeout from _focusItemInput
  }

  beforeEach(async () => {
    mockDevicesService = jasmine.createSpyObj(DevicesService, ['getDeviceNames', 'getDeviceMakes']);
    mockDevicesService.getDeviceNames.and.returnValue(
      of(<DeviceDistinctQueryResult>{
        values: ['CDS-1', 'AHU-2', 'CDS-3'],
      })
    );
    mockDevicesService.getDeviceMakes.and.returnValue(
      of(<DeviceDistinctQueryResult>{
        values: [],
      })
    );

    await TestBed.configureTestingModule({
      imports: [SearchFilterModule, BrowserAnimationsModule],
      providers: [{ provide: DevicesService, useValue: mockDevicesService }],
    }).compileComponents();

    fixture = TestBed.createComponent(SearchFilterComponent);
    component = fixture.componentInstance;
    component.fields = { name: 'getDeviceNames', make: 'getDeviceMakes' };
    component.serviceName = 'DevicesService';
    fixture.detectChanges();
  });

  beforeEach(() => {
    spyOn(component, 'handleFilterChange');
    spyOn(component.itemCtrl, 'setValue');
    spyOn(component.triggerAutocompleteInput, 'closePanel');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should add a filter', fakeAsync(() => {
    injectViewValue('name');

    expect(component.items).toContain({ label: 'Name', value: 'name' });
    expect(component.filterIndex).toEqual(1);
    expect(component.allItems).toContain({ label: '(~) Contains', value: '~' });
    expect(component.allItems).toContain({ label: '(=) Equals', value: '=' });
    expect(component.filterEntry.field).toEqual('name');
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.filterEntry.field).toEqual('name');
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');

    injectViewValue('=');

    expect(component.items).toContain({ label: 'Name =', value: 'Name =' });
    expect(component.filterIndex).toEqual(2);
    expect(component.allItems).toEqual([]);
    expect(component.filterEntry.field).toEqual('name');
    expect(component.filterEntry.operator).toEqual('=');
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');

    injectViewValue('AHU-2');

    expect(component.items).toContain({ label: 'Name = AHU-2', value: 'Name = AHU-2' });
    expect(component.filterIndex).toEqual(0);
    expect(component.allItems).toContain({ label: 'Name', value: 'name' });
    expect(component.allItems).toContain({ label: 'Make', value: 'make' });
    expect(component.filterEntry.field).not.toBeDefined();
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.handleFilterChange).toHaveBeenCalledWith([{ field: 'name', operator: '=', value: 'AHU-2' }]);
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');
  }));

  it('should remove a built filter', fakeAsync(() => {
    injectViewValue('name');
    injectViewValue('=');
    injectViewValue('AHU-2');

    component.remove({ label: 'Name = AHU-2', value: 'Name = AHU-2' });
    tick(); // clear setTimeout from _focusItemInput

    expect(component.handleFilterChange).toHaveBeenCalledTimes(2); // once with the filter, once without
    expect(component.handleFilterChange).toHaveBeenCalledWith([]); // next with it removed
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');
  }));

  it('should remove a partially built filter', fakeAsync(() => {
    injectViewValue('name');
    injectViewValue('=');
    injectViewValue('AHU-2');
    injectViewValue('make');
    injectViewValue('=');

    component.remove({ label: 'Make =', value: 'Make =' });
    tick(); // clear setTimeout from _focusItemInput

    expect(component.filterIndex).toEqual(0);
    expect(component.allItems).toContain({ label: 'Name', value: 'name' });
    expect(component.allItems).toContain({ label: 'Make', value: 'make' });
    expect(component.filterEntry.field).not.toBeDefined();
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');
    expect(component.handleFilterChange).toHaveBeenCalledTimes(1); // once to add the filter
  }));

  it('should add an adhoc filter', fakeAsync(() => {
    injectViewValue('name');
    injectViewValue('~');

    const event: MatChipInputEvent = {
      value: 'vaV',
    } as MatChipInputEvent;

    component.add(event);
    tick();

    expect(component.items).toContain({ label: 'Name ~ vaV', value: 'Name ~ vaV' });
    expect(component.filterIndex).toEqual(0);
    expect(component.allItems).toContain({ label: 'Name', value: 'name' });
    expect(component.allItems).toContain({ label: 'Make', value: 'make' });
    expect(component.filterEntry.field).not.toBeDefined();
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.handleFilterChange).toHaveBeenCalledWith([{ field: 'name', operator: '~', value: 'vaV' }]);
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');
  }));

  it('should clear all filters', fakeAsync(() => {
    injectViewValue('name');
    injectViewValue('=');
    injectViewValue('AHU-2');
    injectViewValue('make');
    injectViewValue('=');
    injectViewValue('VAVX12');

    component.clear();
    tick();

    expect(component.items).toEqual([]);
    expect(component.filters).toEqual([]);
    expect(component.allItems).toEqual(
      jasmine.arrayContaining([
        { label: 'Name', value: 'name' },
        { label: 'Make', value: 'make' },
      ])
    );
    expect(component.filterIndex).toEqual(0);
    expect(component.filterEntry).toEqual({});
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith('');
    expect(component.triggerAutocompleteInput.closePanel).toHaveBeenCalled();
    expect(component.handleFilterChange).toHaveBeenCalledWith([]);
  }));
});
