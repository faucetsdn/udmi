import { Component, ElementRef, HostBinding, Injector, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { startCase, findIndex, some } from 'lodash-es';
import { iif, Observable, of } from 'rxjs';
import { map, startWith, switchMap } from 'rxjs/operators';
import { ChipItem, SearchFilterItem } from './search-filter';
import { MatChipInputEvent } from '@angular/material/chips';
import { DevicesService } from '../devices/devices.service';
import { SitesService } from '../sites/sites.service';

const services: any = {
  DevicesService,
  SitesService,
};

@Component({
  selector: 'app-search-filter',
  inputs: ['serviceName', 'fields', 'limit', 'handleFilterChange', 'filter'],
  templateUrl: './search-filter.component.html',
  styleUrls: ['./search-filter.component.scss'],
})
export class SearchFilterComponent implements OnInit {
  filter?: string;
  serviceName!: string;
  fields!: Record<string, string>;
  limit: number = 5;
  handleFilterChange = (_filters: SearchFilterItem[]): void => {};
  filterEntry: SearchFilterItem = {}; // chip cache
  filters: SearchFilterItem[] = [];
  itemCtrl = new FormControl();
  filteredItems: Observable<ChipItem[]>; // the autocomplete list of options, filtered based on users input
  items: ChipItem[] = []; // list of chips
  allItems: ChipItem[] = []; // list of options, options will switch based on filterIndex
  filterIndex: number = 0; // position in the current chip we are building
  fieldItems: ChipItem[] = []; // hardcoded fields we can filter on, set by the input 'fields'

  @ViewChild('itemInput') itemInput!: ElementRef<HTMLInputElement>;
  @ViewChild(MatAutocompleteTrigger) triggerAutocompleteInput!: MatAutocompleteTrigger;
  @HostBinding('className') componentClass: string;

  constructor(private injector: Injector) {
    this.componentClass = 'app-search-filter';
    this.filteredItems = this.itemCtrl.valueChanges.pipe(
      startWith(''),
      switchMap((term) =>
        iif(
          () => this.filterEntry.operator === '=' && !some(this.allItems, (item: ChipItem) => term === item.value), // avoid calling the backend again with the populated search term when the value is selected
          // Auto-complete on suggested values when we've chosen the equals operator on a field.
          <Observable<ChipItem[]>>this.injector
            .get<any>(services[this.serviceName])
            [this.fields[this.filterEntry.field]]?.(term, this.limit, this.filter)
            .pipe(
              map(({ values }) => {
                this.allItems = values.map((value: string): ChipItem => ({ label: value, value }));
                return this.allItems;
              })
            ),
          // Else auto-complete on the field names or equals(=)/contains(~).
          of(term ? this._filter(term) : this.allItems)
        )
      )
    );
  }

  ngOnInit(): void {
    this.allItems = Object.keys(this.fields).map((chipValue) => ({ label: startCase(chipValue), value: chipValue }));
    this.fieldItems = this.allItems;
  }

  add(event: MatChipInputEvent): void {
    const value: string = (event.value || '').trim();

    // If we have chosen the contains (~) operator, inject what the user
    // has typed in as the selected value.
    if (this.filterIndex === 2 && this.filterEntry.operator === '~' && value) {
      const $event: MatAutocompleteSelectedEvent = {
        option: {
          value: event.value,
        },
      } as MatAutocompleteSelectedEvent;

      this.selected($event);
    }
  }

  remove(item: ChipItem): void {
    const index: number = findIndex(this.items, { value: item.value });

    this.items.splice(index, 1); // remove the chip

    // Check if we're deleting an exisitng chip,
    // or one we are halfway done building.
    if (index === this.filters.length) {
      // We're deleting a partially built chip.
      this.allItems = this.fieldItems;
      this.filterIndex = 0;
      this.filterEntry = {};
      this._resetInput();
      this._closePanel();
    } else {
      // We're deleting a fully built chip.
      this.filters.splice(index, 1); // remove the filter
      this.handleFilterChange(this.filters);

      if (this.filterIndex) {
        // Keep the panel open when we're still building a chip
        // but deleting one next to it.
        this._closePanel();
        this._openPanel();
      } else if (this.itemInput.nativeElement.value !== '') {
        // We've typed something before deleting chip next to this one,
        // so reopen the panel in the right position.
        this._closePanel();
        this._openPanel();
      } else {
        // We've typed nothing, so don't keep the panel open.
        this._closePanel();
      }
    }
  }

  clear(): void {
    this.items = [];
    this.filters = [];
    this.allItems = this.fieldItems;
    this.filterIndex = 0;
    this.filterEntry = {}; // clear the chip cache

    this._resetInput();
    this._closePanel();
    this.handleFilterChange(this.filters);
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    const chipValue: string = event.option.value;
    const chipItem: ChipItem = { label: this.filterIndex === 0 ? startCase(chipValue) : chipValue, value: chipValue };

    this.items.push(chipItem);
    this.filterIndex++;

    switch (this.filterIndex) {
      case 1:
        this.allItems = [
          { label: '(=) Equals', value: '=' },
          { label: '(~) Contains', value: '~' },
        ]; // select operator
        this.filterEntry.field = chipValue; // store the field
        this._resetInput();
        this._openPanel();
        break;
      case 2:
        this.allItems = []; // can clear the items, the api will handle filtering user input above in observable
        this.filterEntry.operator = chipValue; // store the operator
        this._combineLastTwoChips();
        this._resetInput();
        this._openPanel();
        break;
      default:
        this.allItems = this.fieldItems; // reset
        this.filterIndex = 0; // reset
        this.filterEntry.value = chipValue; // store the value
        this.filters.push(this.filterEntry);
        this.filterEntry = {}; // clear the chip cache
        this._combineLastTwoChips();
        this._resetInput();
        this._closePanel();
        this.handleFilterChange(this.filters);
        break;
    }
  }

  private _resetInput(): void {
    this.itemInput.nativeElement.value = '';
    this.itemCtrl.setValue('');
  }

  private _openPanel(): void {
    setTimeout(() => {
      this.triggerAutocompleteInput.openPanel();
    });
  }

  private _closePanel(): void {
    setTimeout(() => {
      this.triggerAutocompleteInput.closePanel();
    });
  }

  private _combineLastTwoChips(): void {
    const lastTwoItems: ChipItem[] = this.items.splice(this.items.length - 2, 2); // remove last 2 chips
    const chipValue: string = lastTwoItems.map(({ label }) => label).join(' ');
    const chipItem: ChipItem = { label: chipValue, value: chipValue };

    if (lastTwoItems.length) {
      this.items.push(chipItem); // push it back as a single chip
    }
  }

  private _filter(value: string): ChipItem[] {
    const filterValue: string = value.toLowerCase();

    return this.allItems.filter((item) => item.label.toLowerCase().includes(filterValue));
  }
}
