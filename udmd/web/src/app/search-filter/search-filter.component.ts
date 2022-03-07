import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';

@Component({
  selector: 'app-search-filter',
  inputs: ['data', 'handleFilterChange'],
  templateUrl: './search-filter.component.html',
  styleUrls: ['./search-filter.component.scss'],
})
export class SearchFilterComponent implements OnInit {
  data: any = {};
  handleFilterChange = (_filter: any[]): void => {};
  builtEntry: any = {}; // chip cache
  filter: any[] = [];
  itemCtrl = new FormControl();
  filteredItems: Observable<string[]>;
  items: string[] = [];
  allItems: string[] = [];
  filterIndex: number = 0;
  placeholder: string = 'Select field...';

  @ViewChild('itemInput') itemInput!: ElementRef<HTMLInputElement>;

  constructor() {
    this.filteredItems = this.itemCtrl.valueChanges.pipe(
      startWith(null),
      map((item: string | null) => (item ? this._filter(item) : this.allItems.slice()))
    );
  }

  ngOnInit(): void {
    this.allItems = Object.keys(this.data);
  }

  remove(item: string): void {
    const index = this.items.indexOf(item);

    this.items.splice(index, 1); // remove the chip

    // Check if we're deleting an exisitng filter, or one we are halfway done building.
    if (index === this.filter.length) {
      // We're deleting a half built filter.
      this.allItems = Object.keys(this.data); // select field
      this.placeholder = 'Select field...';
      this.filterIndex = 0;
      this.items.splice(index, 1); // remove the chip
      this.builtEntry = {}; // clear the chip cache
    } else {
      // We're deleting a built filter.
      this.filter.splice(index, 1); // remove the filter
      this.handleFilterChange(this.filter);
    }

    // Clear the input.
    this.itemInput.nativeElement.value = '';
    this.itemCtrl.setValue(null);

    // Show the auto-complete options panel.
    this._focusItemInput();
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    const chipValue = event.option.viewValue;

    this.items.push(chipValue);
    this.filterIndex++;

    switch (this.filterIndex) {
      case 0:
        this.allItems = Object.keys(this.data); // select field
        this.placeholder = 'Select field...';
        break;
      case 1:
        this.allItems = ['=', '!=']; // select operator
        this.placeholder = 'Select operator...';
        this.builtEntry.field = chipValue; // store the field
        break;
      case 2:
        this.allItems = this.data[this.items[this.items.length - 2]]; // select the fields options
        this.placeholder = 'Select value...';
        this.builtEntry.operator = chipValue; // store the operator

        this._combineLastTwoChips();
        break;
      default:
        this.allItems = Object.keys(this.data); // reset
        this.placeholder = 'Select field...';
        this.filterIndex = 0; // reset
        this.builtEntry.value = chipValue; // store the value
        this.filter.push(this.builtEntry);
        this.builtEntry = {}; // clear the chip cache

        this._combineLastTwoChips();
        this.handleFilterChange(this.filter);
        break;
    }

    // Clear the input.
    this.itemInput.nativeElement.value = '';
    this.itemCtrl.setValue(null);

    // Show the auto-complete options panel.
    this._focusItemInput();
  }

  private _focusItemInput(): void {
    setTimeout(() => {
      this.itemInput.nativeElement.blur();
      this.itemInput.nativeElement.focus();
    });
  }

  private _combineLastTwoChips(): void {
    const lastTwoItems = this.items.splice(this.items.length - 2, 2); // remove last 2 chips

    if (lastTwoItems.length) {
      this.items.push(lastTwoItems.join(' ')); // push it back as a single chip
    }
  }

  private _filter(value: string): string[] {
    const filterValue = value.toLowerCase();

    return this.allItems.filter((item) => item.toLowerCase().includes(filterValue));
  }
}
