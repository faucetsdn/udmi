import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';

@Component({
  selector: 'app-search-filter',
  inputs: ['data'],
  templateUrl: './search-filter.component.html',
  styleUrls: ['./search-filter.component.scss'],
})
export class SearchFilterComponent implements OnInit {
  data: any = {};

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
    this._updateAllFruits();
  }

  remove(fruit: string): void {
    const index = this.fruits.indexOf(fruit);

    this.fruits.splice(index, 1); // remove last chip
    this.filterIndex = 0;

    this._updateAllFruits();

    // Show the auto-complete options panel.
    this._focusFruitInput();
  }

  selected(event: MatAutocompleteSelectedEvent): void {
    this.fruits.push(event.option.viewValue);
    this.filterIndex++;

    this._updateAllFruits();

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
    // Combine the last two chips.
    const lastTwoItems = this.fruits.splice(this.fruits.length - 2, 2); // remove last 2 chips
    if (lastTwoItems.length) {
      this.fruits.push(lastTwoItems.join(' ')); // push it back as a single chip
    }
  }

  private _updateAllFruits(): void {
    switch (this.filterIndex) {
      case 0:
        this.allFruits = Object.keys(this.data); // select field
        this.placeholder = 'Select field...';
        break;
      case 1:
        this.allFruits = ['=', '!=']; // select operator
        this.placeholder = 'Select operator...';
        break;
      case 2:
        this.allFruits = this.data[this.fruits[this.fruits.length - 2]]; // select the fields options
        this.placeholder = 'Select value...';
        this._combineLastTwoChips();
        break;
      default:
        this.allFruits = Object.keys(this.data); // reset
        this.filterIndex = 0; // reset
        this.placeholder = 'Select field...';
        this._combineLastTwoChips();

        // TODO:: run the query with the filter...
        console.log('run query');

        break;
    }

    // Clear the input.
    if (this.fruitInput) this.fruitInput.nativeElement.value = '';
    this.fruitCtrl.setValue(null);
  }

  private _filter(value: string): string[] {
    const filterValue = value.toLowerCase();

    return this.allFruits.filter((fruit) => fruit.toLowerCase().includes(filterValue));
  }
}
