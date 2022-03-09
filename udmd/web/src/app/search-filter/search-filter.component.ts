import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { startCase } from 'lodash';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';
import { ChipItem, SearchFilterItem } from './search-filter';
import { MatChipInputEvent } from '@angular/material/chips';

@Component({
  selector: 'app-search-filter',
  inputs: ['data', 'handleFilterChange'],
  templateUrl: './search-filter.component.html',
  styleUrls: ['./search-filter.component.scss'],
})
export class SearchFilterComponent implements OnInit {
  data: Record<string, string[]> = {};
  handleFilterChange = (_filters: SearchFilterItem[]): void => {};
  filterEntry: SearchFilterItem = {}; // chip cache
  filters: SearchFilterItem[] = [];
  itemCtrl = new FormControl();
  filteredItems: Observable<ChipItem[]>;
  items: ChipItem[] = [];
  allItems: ChipItem[] = [];
  filterIndex: number = 0;
  placeholder: string = 'Select field...';
  fieldItems: ChipItem[] = [];

  @ViewChild('itemInput') itemInput!: ElementRef<HTMLInputElement>;

  constructor() {
    this.filteredItems = this.itemCtrl.valueChanges.pipe(
      startWith(null),
      map((item: string | null) => (item ? this._filter(item) : this.allItems.slice()))
    );
  }

  ngOnInit(): void {
    this.allItems = Object.keys(this.data).map((chipValue) => ({ label: startCase(chipValue), value: chipValue }));
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
    const index: number = this.items.indexOf(item);

    this.items.splice(index, 1); // remove the chip

    // Check if we're deleting an exisitng filter, or one we are halfway done building.
    if (index === this.filters.length) {
      // We're deleting a half built filter.
      this.allItems = this.fieldItems; // select field
      this.placeholder = 'Select field...';
      this.filterIndex = 0;
      this.filterEntry = {}; // clear the chip cache
    } else {
      // We're deleting a built filter.
      this.filters.splice(index, 1); // remove the filter
      this.handleFilterChange(this.filters);
    }

    // Show the auto-complete options panel.
    this._focusItemInput();
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    const chipValue: string = event.option.value;
    const chipItem: ChipItem = { label: this.filterIndex === 0 ? startCase(chipValue) : chipValue, value: chipValue };

    this.items.push(chipItem);
    this.filterIndex++;

    switch (this.filterIndex) {
      case 0:
        this.allItems = this.fieldItems; // select field
        this.placeholder = 'Select field...';
        break;
      case 1:
        this.allItems = [
          { label: '(=) Equals', value: '=' },
          { label: '(~) Contains', value: '~' },
        ]; // select operator
        this.placeholder = 'Select operator...';
        this.filterEntry.field = chipValue; // store the field
        break;
      case 2:
        this.allItems =
          chipValue === '~'
            ? []
            : this.data[this.items[this.items.length - 2].value].map((cv) => ({ label: cv, value: cv })); // select the fields options when operator is not contains (~)
        this.placeholder = 'Select value...';
        this.filterEntry.operator = chipValue; // store the operator
        this._combineLastTwoChips();
        break;
      default:
        this.allItems = this.fieldItems; // reset
        this.placeholder = 'Select field...';
        this.filterIndex = 0; // reset
        this.filterEntry.value = chipValue; // store the value
        this.filters.push(this.filterEntry);
        this.filterEntry = {}; // clear the chip cache
        this._combineLastTwoChips();
        this.handleFilterChange(this.filters);
        break;
    }

    // Show the auto-complete options panel.
    this._focusItemInput();
  }

  private _focusItemInput(): void {
    // Clear the input.
    this.itemInput.nativeElement.value = '';
    this.itemCtrl.setValue(null);

    setTimeout(() => {
      this.itemInput.nativeElement.blur();
      this.itemInput.nativeElement.focus();
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
