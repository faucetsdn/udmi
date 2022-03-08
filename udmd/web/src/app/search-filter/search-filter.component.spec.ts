import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { SearchFilterComponent } from './search-filter.component';
import { SearchFilterModule } from './search-filter.module';

describe('SearchFilterComponent', () => {
  let component: SearchFilterComponent;
  let fixture: ComponentFixture<SearchFilterComponent>;

  function injectViewValue(viewValue: string): void {
    let e: MatAutocompleteSelectedEvent = {
      option: {
        viewValue,
      },
    } as MatAutocompleteSelectedEvent;

    component.selected(e);
    tick(); // clear setTimeout from _focusItemInput
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SearchFilterModule, BrowserAnimationsModule],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(SearchFilterComponent);
    component = fixture.componentInstance;
    component.data = {
      name: ['CDS-1', 'AHU-2', 'CDS-3'],
      make: ['make-1', 'make-2', 'make-3'],
    };
    fixture.detectChanges();
  });

  beforeEach(() => {
    spyOn(component, 'handleFilterChange');
    spyOn(component.itemCtrl, 'setValue');
    spyOn(component.itemInput.nativeElement, 'blur');
    spyOn(component.itemInput.nativeElement, 'focus');
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should add a filter', fakeAsync(() => {
    injectViewValue('name');

    expect(component.items).toContain('name');
    expect(component.filterIndex).toEqual(1);
    expect(component.allItems).toContain('=');
    expect(component.allItems).toContain('!=');
    expect(component.placeholder).toEqual('Select operator...');
    expect(component.filterEntry.field).toEqual('name');
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.filterEntry.field).toEqual('name');
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith(null);

    injectViewValue('=');

    expect(component.items).toContain('name =');
    expect(component.filterIndex).toEqual(2);
    expect(component.allItems).toContain('CDS-1');
    expect(component.allItems).toContain('AHU-2');
    expect(component.allItems).toContain('CDS-3');
    expect(component.placeholder).toEqual('Select value...');
    expect(component.filterEntry.field).toEqual('name');
    expect(component.filterEntry.operator).toEqual('=');
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith(null);

    injectViewValue('AHU-2');

    expect(component.items).toContain('name = AHU-2');
    expect(component.filterIndex).toEqual(0);
    expect(component.allItems).toContain('name');
    expect(component.allItems).toContain('make');
    expect(component.placeholder).toEqual('Select field...');
    expect(component.filterEntry.field).not.toBeDefined();
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.handleFilterChange).toHaveBeenCalledWith([{ field: 'name', operator: '=', value: 'AHU-2' }]);
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith(null);

    // Confirm we refocused.
    fixture.whenStable().then(() => {
      expect(component.itemInput.nativeElement.blur).toHaveBeenCalledTimes(3);
      expect(component.itemInput.nativeElement.focus).toHaveBeenCalledTimes(3);
    });
  }));

  it('should remove a built filter', fakeAsync(() => {
    injectViewValue('name');
    injectViewValue('=');
    injectViewValue('AHU-2');

    component.remove('name = AHU-2');
    tick(); // clear setTimeout from _focusItemInput

    expect(component.handleFilterChange).toHaveBeenCalledTimes(2); // once with the filter, once without
    expect(component.handleFilterChange).toHaveBeenCalledWith([]); // next with it removed
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith(null);

    // Confirm we refocused.
    fixture.whenStable().then(() => {
      expect(component.itemInput.nativeElement.blur).toHaveBeenCalledTimes(4);
      expect(component.itemInput.nativeElement.focus).toHaveBeenCalledTimes(4);
    });
  }));

  it('should remove a partially built filter', fakeAsync(() => {
    injectViewValue('name');
    injectViewValue('=');
    injectViewValue('AHU-2');
    injectViewValue('make');
    injectViewValue('=');

    component.remove('make =');
    tick(); // clear setTimeout from _focusItemInput

    expect(component.filterIndex).toEqual(0);
    expect(component.allItems).toContain('name');
    expect(component.allItems).toContain('make');
    expect(component.placeholder).toEqual('Select field...');
    expect(component.filterEntry.field).not.toBeDefined();
    expect(component.filterEntry.operator).not.toBeDefined();
    expect(component.filterEntry.value).not.toBeDefined();
    expect(component.itemInput.nativeElement.value).toEqual('');
    expect(component.itemCtrl.setValue).toHaveBeenCalledWith(null);
    expect(component.handleFilterChange).toHaveBeenCalledTimes(1); // once to add the filter

    // Confirm we refocused.
    fixture.whenStable().then(() => {
      expect(component.itemInput.nativeElement.blur).toHaveBeenCalledTimes(6);
      expect(component.itemInput.nativeElement.focus).toHaveBeenCalledTimes(6);
    });
  }));
});
