import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApolloModule } from 'apollo-angular';
import { ApolloTestingModule, ApolloTestingController } from 'apollo-angular/testing';
import { DevicesComponent } from './devices.component';

describe('DevicesComponent', () => {
  let controller: ApolloTestingController;
  let component: DevicesComponent;
  let fixture: ComponentFixture<DevicesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DevicesComponent],
      imports: [ApolloTestingModule, ApolloModule],
    }).compileComponents();
  });

  beforeEach(() => {
    controller = TestBed.inject(ApolloTestingController);
    fixture = TestBed.createComponent(DevicesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    controller.verify();
  });

  it('should compile', () => {
    expect(component).toBeTruthy();
  });
});
