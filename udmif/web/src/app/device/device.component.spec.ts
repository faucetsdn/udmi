import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { Device, DeviceQueryResponse } from './device';
import { DeviceComponent } from './device.component';
import { DeviceModule } from './device.module';
import { DeviceService } from './device.service';

describe('DeviceComponent', () => {
  let component: DeviceComponent;
  let fixture: ComponentFixture<DeviceComponent>;
  let mockDeviceService: jasmine.SpyObj<DeviceService>;
  let device: Device = {
    id: 'device-id-123',
    name: 'device one',
    tags: [],
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
      imports: [DeviceModule, RouterTestingModule],
      providers: [
        { provide: DeviceService, useValue: mockDeviceService },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              params: {
                id: 'device-id-123',
              },
            },
          },
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should store the device in memory', () => {
    expect(mockDeviceService.getDevice).toHaveBeenCalledWith('device-id-123');
    expect(component.device).toEqual(device);
    expect(component.loading).toBeFalse();
  });
});
