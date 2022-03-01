import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GraphQLModule } from '../graphql/graphql.module';
import { DevicesComponent } from './devices.component';
import { DevicesModule } from './devices.module';

describe('DevicesComponent', () => {
  let component: DevicesComponent;
  let fixture: ComponentFixture<DevicesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DevicesModule, GraphQLModule],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DevicesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should compile', () => {
    expect(component).toBeTruthy();
  });
});
