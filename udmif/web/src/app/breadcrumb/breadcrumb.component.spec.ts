import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { RouterTestingModule } from '@angular/router/testing';
import { BreadcrumbComponent } from './breadcrumb.component';
import { BreadcrumbModule } from './breadcrumb.module';

describe('BreadcrumbComponent', () => {
  let component: BreadcrumbComponent;
  let fixture: ComponentFixture<BreadcrumbComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouterTestingModule, BreadcrumbModule],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: {
            parent: {
              parent: {
                parent: {
                  snapshot: {
                    url: [{ path: 'devices' }],
                  },
                },
                snapshot: {
                  url: [{ path: 'device-id-1' }],
                },
              },
              snapshot: {
                url: [{ path: 'points' }],
              },
            },
          },
        },
      ],
    }).compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(BreadcrumbComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should store the proper breadcrumb items in memory', () => {
    expect(component.items).toEqual([
      {
        label: 'Devices', // start case word
        url: '/devices',
      },
      {
        label: 'device-id-1',
        url: '/devices/device-id-1',
      },
      {
        label: 'Points', // start case word
        url: '/devices/device-id-1/points',
      },
    ]);
  });
});
