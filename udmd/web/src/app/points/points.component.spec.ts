import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PointsComponent } from './points.component';
import { PointsModule } from './points.module';

describe('PointsComponent', () => {
  let component: PointsComponent;
  let fixture: ComponentFixture<PointsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PointsModule],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(PointsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
