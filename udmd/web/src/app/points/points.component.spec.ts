import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { ApolloQueryResult } from '@apollo/client/core';
import { of } from 'rxjs';
import { Point, PointsQueryResponse } from './points';
import { PointsComponent } from './points.component';
import { PointsModule } from './points.module';
import { PointsService } from './points.service';

describe('PointsComponent', () => {
  let component: PointsComponent;
  let fixture: ComponentFixture<PointsComponent>;
  let mockPointsService: jasmine.SpyObj<PointsService>;
  let points: Point[] = [
    {
      id: 'point-id-123',
    },
  ];

  beforeEach(async () => {
    mockPointsService = jasmine.createSpyObj(PointsService, ['getPoints']);
    mockPointsService.getPoints.and.returnValue(
      of(<ApolloQueryResult<PointsQueryResponse>>{
        data: {
          device: {
            id: 'device-id-123',
            points,
          },
        },
        loading: false,
      })
    );

    await TestBed.configureTestingModule({
      imports: [PointsModule],
      providers: [
        { provide: PointsService, useValue: mockPointsService },
        {
          provide: ActivatedRoute,
          useValue: {
            parent: {
              snapshot: {
                parent: {
                  params: {
                    id: 'device-id-123',
                  },
                },
              },
            },
          },
        },
      ],
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

  it('should store the points in memory', () => {
    expect(mockPointsService.getPoints).toHaveBeenCalledWith('device-id-123');
    expect(component.points).toEqual(points);
    expect(component.loading).toBeFalse();
  });
});
