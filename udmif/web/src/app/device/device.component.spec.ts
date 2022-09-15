import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { NavigationService } from '../navigation/navigation.service';
import { Device, DeviceQueryResponse } from './device';
import { DeviceComponent } from './device.component';
import { DeviceModule } from './device.module';
import { DeviceService } from './device.service';

describe('DeviceComponent', () => {
  let component: DeviceComponent;
  let fixture: ComponentFixture<DeviceComponent>;
  let mockNavigationService: jasmine.SpyObj<NavigationService>;
  let mockDeviceService: jasmine.SpyObj<DeviceService>;
  let device: Device = {
    id: 'device-id-123',
    name: 'device one',
  };

  beforeEach(async () => {
    mockNavigationService = jasmine.createSpyObj(NavigationService, ['setTitle', 'clearTitle']);
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
        { provide: NavigationService, useValue: mockNavigationService },
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
    fixture = TestBed.createComponent(DeviceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize correctly', () => {
    expect(mockNavigationService.setTitle).toHaveBeenCalledWith('device one');
    expect(mockDeviceService.getDevice).toHaveBeenCalledWith('device-id-123');
    expect(component.device).toEqual(device);
    expect(component.loading).toBeFalse();
  });

  it('should cleanup correctly', () => {
    spyOn(component.deviceSubscription, 'unsubscribe');

    fixture.destroy();

    expect(component.deviceSubscription.unsubscribe).toHaveBeenCalled();
    expect(mockNavigationService.clearTitle).toHaveBeenCalled();
  });
});
