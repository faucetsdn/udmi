import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { Device, DeviceQueryResponse } from '../device/device';
import { DeviceErrorsComponent } from './device-errors.component';
import { DeviceErrorsModule } from './device-errors.module';
import { DeviceService } from '../device/device.service';
import { DeviceError } from './device-errors';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';

describe('DeviceErrorsComponent', () => {
  let component: DeviceErrorsComponent;
  let fixture: ComponentFixture<DeviceErrorsComponent>;
  let mockDeviceService: jasmine.SpyObj<DeviceService>;

  const errors: DeviceError[] = [
    {
      message: 'My error message',
      category: 'some.category',
      timestamp: '2022-09-20T14:41:02Z',
      level: 500,
    },
  ];
  const device: Device = {
    validation: JSON.stringify({
      id: 'device-id-123',
      errors,
    }),
  };

  beforeEach(async () => {
    mockDeviceService = jasmine.createSpyObj(DeviceService, ['getDevice']);
    mockDeviceService.getDevice.and.returnValue(
      of(<ApolloQueryResult<DeviceQueryResponse>>{
        data: {
          device,
        },
        loading: false,
      })
    );

    await TestBed.configureTestingModule({
      imports: [DeviceErrorsModule, RouterTestingModule, BrowserAnimationsModule], // BrowserAnimationsModule for MatTable native sorting
      providers: [
        { provide: DeviceService, useValue: mockDeviceService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              params: {
                deviceId: 'device-id-123',
              },
            },
          },
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceErrorsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize correctly', () => {
    expect(mockDeviceService.getDevice).toHaveBeenCalledWith('device-id-123');
    expect(component.errors).toEqual(errors);
    expect(component.loading).toBeFalse();
  });

  it('should cleanup correctly', () => {
    spyOn(component.deviceSubscription, 'unsubscribe');

    fixture.destroy();

    expect(component.deviceSubscription.unsubscribe).toHaveBeenCalled();
  });
});
