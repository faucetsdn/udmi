import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DevicesComponent } from './devices.component';
import { DevicesModule } from './devices.module';
import { DevicesService } from './devices.service';
import { of } from 'rxjs';

describe('DevicesComponent', () => {
  let component: DevicesComponent;
  let fixture: ComponentFixture<DevicesComponent>;
  let mockDevicesService: jasmine.SpyObj<DevicesService>;

  beforeEach(async () => {
    mockDevicesService = jasmine.createSpyObj(DevicesService, ['getDevices']);
    mockDevicesService.getDevices.and.returnValue(of());

    await TestBed.configureTestingModule({
      imports: [DevicesModule],
      providers: [{ provide: DevicesService, useValue: mockDevicesService }],
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
