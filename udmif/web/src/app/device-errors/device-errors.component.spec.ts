import { ComponentFixture, TestBed } from '@angular/core/testing';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { ActivatedRoute } from '@angular/router';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { Point, PointsQueryResponse } from './device-errors';
import { DeviceErrorsComponent } from './device-errors.component';
import { PointsModule } from './device-errors.module';
import { PointsService } from './points.service';

describe('DeviceErrorsComponent', () => {
  let component: DeviceErrorsComponent;
  let fixture: ComponentFixture<DeviceErrorsComponent>;
  let mockPointsService: jasmine.SpyObj<PointsService>;
  let errors: Point[] = [
    {
      id: 'point-id-123',
    },
  ];

  beforeEach(async () => {
    mockPointsService = jasmine.createSpyObj(PointsService, ['getPoints']);
    mockPointsService.getPoints.and.returnValue(
      of(<ApolloQueryResult<PointsQueryResponse>>{
        data: {
          points,
        },
        loading: false,
      })
    );

    await TestBed.configureTestingModule({
      imports: [PointsModule, BrowserAnimationsModule],
      providers: [
        { provide: PointsService, useValue: mockPointsService },
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

  it('should store the points in memory', () => {
    expect(mockPointsService.getPoints).toHaveBeenCalledWith('device-id-123');
    expect(component.points).toEqual(points);
    expect(component.loading).toBeFalse();
  });
});
