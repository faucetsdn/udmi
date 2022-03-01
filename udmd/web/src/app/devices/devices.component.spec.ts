import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ApolloTestingModule, ApolloTestingController } from 'apollo-angular/testing';
import { GraphqlModule } from '../graphql/graphql.module';
import { GET_DEVICES } from './device.gql';
import { DevicesComponent } from './devices.component';
import { DevicesModule } from './devices.module';

describe('DevicesComponent', () => {
  let controller: ApolloTestingController;
  let component: DevicesComponent;
  let fixture: ComponentFixture<DevicesComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DevicesModule, ApolloTestingModule, GraphqlModule],
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

  it('should return the devices', () => {
    // Make some assertion about the result for once it's fulfilled.
    component.getDevices().subscribe(({ data }) => {
      expect(data.devices.totalCount).toEqual(2);
    });

    // The following `expectOne()` will match the operation's document.
    // If no requests or multiple requests matched that document
    // `expectOne()` would throw.
    const op = controller.expectOne(GET_DEVICES);

    // Assert the correct search options were sent.
    expect(op.operation.variables).toEqual({
      searchOptions: {
        batchSize: 10,
        offset: 0,
      },
    });

    // Respond with mock data, causing Observable to resolve.
    op.flush({
      data: {
        devices: {
          devices: [
            {
              id: '123',
              name: 'device one',
            },
            {
              id: '456',
              name: 'device two',
            },
          ],
          totalCount: 2,
        },
      },
    });

    // Finally, assert that there are no outstanding operations.
    controller.verify();
  });
});
