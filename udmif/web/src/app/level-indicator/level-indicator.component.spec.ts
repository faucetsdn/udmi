import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LevelIndicatorComponent } from './level-indicator.component';
import { LevelIndicatorModule } from './level-indicator.module';

describe('LevelIndicatorComponent', () => {
  let component: LevelIndicatorComponent;
  let fixture: ComponentFixture<LevelIndicatorComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [LevelIndicatorModule],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(LevelIndicatorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
