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
  handleFilterChange: any = () => {};
  builtEntry: any = {}; // chip cache
  filters: any = [];
  fruitCtrl = new FormControl();
  filteredFruits: Observable<string[]>;
  fruits: string[] = [];
  allFruits: string[] = [];
  filterIndex: number = 0;
  placeholder: string = 'Select field...';

  @ViewChild('fruitInput') fruitInput!: ElementRef<HTMLInputElement>;

  constructor() {
    this.filteredFruits = this.fruitCtrl.valueChanges.pipe(
      startWith(null),
      map((fruit: string | null) => (fruit ? this._filter(fruit) : this.allFruits.slice()))
    );
  }

  ngOnInit(): void {
    this.allFruits = Object.keys(this.data);
  }

  remove(fruit: string): void {
    const index = this.fruits.indexOf(fruit);

    this.fruits.splice(index, 1); // remove the chip

    // Check if we're deleting an exisitng filter, or one we are halfway done building.
    if (index === this.filters.length) {
      // We're deleting a half built filter.
      this.allFruits = Object.keys(this.data); // select field
      this.placeholder = 'Select field...';
      this.filterIndex = 0;
      this.fruits.splice(index, 1); // remove the chip
      this.builtEntry = {}; // clear the chip cache
    } else {
      // We're deleting a built filter.
      this.filters.splice(index, 1); // remove the filter
      this.handleFilterChange(this.filters);
    }

    // Clear the input.
    this.fruitInput.nativeElement.value = '';
    this.fruitCtrl.setValue(null);

    // Show the auto-complete options panel.
    this._focusFruitInput();
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    const chipValue = event.option.viewValue;

    this.fruits.push(chipValue);
    this.filterIndex++;

    switch (this.filterIndex) {
      case 0:
        this.allFruits = Object.keys(this.data); // select field
        this.placeholder = 'Select field...';
        break;
      case 1:
        this.allFruits = ['=', '!=']; // select operator
        this.placeholder = 'Select operator...';
        this.builtEntry.field = chipValue; // store the field
        break;
      case 2:
        this.allFruits = this.data[this.fruits[this.fruits.length - 2]]; // select the fields options
        this.placeholder = 'Select value...';
        this.builtEntry.operator = chipValue; // store the operator

        this._combineLastTwoChips();
        break;
      default:
        this.allFruits = Object.keys(this.data); // reset
        this.placeholder = 'Select field...';
        this.filterIndex = 0; // reset
        this.builtEntry.value = chipValue; // store the value
        this.filters.push(this.builtEntry);
        this.builtEntry = {}; // clear the chip cache

        this._combineLastTwoChips();
        this.handleFilterChange(this.filters);
        break;
    }

    // Clear the input.
    this.fruitInput.nativeElement.value = '';
    this.fruitCtrl.setValue(null);

    // Show the auto-complete options panel.
    this._focusFruitInput();
  }

  private _focusFruitInput(): void {
    setTimeout(() => {
      this.fruitInput.nativeElement.blur();
      this.fruitInput.nativeElement.focus();
    });
  }

  private _combineLastTwoChips(): void {
    const lastTwoItems = this.fruits.splice(this.fruits.length - 2, 2); // remove last 2 chips

    if (lastTwoItems.length) {
      this.fruits.push(lastTwoItems.join(' ')); // push it back as a single chip
    }
  }

  private _filter(value: string): string[] {
    const filterValue = value.toLowerCase();

    return this.allFruits.filter((fruit) => fruit.toLowerCase().includes(filterValue));
  }
}
