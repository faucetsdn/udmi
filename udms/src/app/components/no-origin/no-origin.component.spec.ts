import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NoOriginComponent } from './no-origin.component';

describe('NoOriginComponent', () => {
  let component: NoOriginComponent;
  let fixture: ComponentFixture<NoOriginComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ NoOriginComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(NoOriginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
