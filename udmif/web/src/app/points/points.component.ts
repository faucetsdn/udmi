import { Component, OnInit, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { ActivatedRoute } from '@angular/router';
import { Point, PointModel } from './points';
import { PointsService } from './points.service';

@Component({
  templateUrl: './points.component.html',
  styleUrls: ['./points.component.scss'],
})
export class PointsComponent implements OnInit {
  displayedColumns: (keyof PointModel)[] = ['name', 'value', 'units', 'state'];
  points: Point[] = [];
  loading: boolean = true;
  dataSource = new MatTableDataSource<Point>();

  @ViewChild(MatSort) sort!: MatSort;

  constructor(private route: ActivatedRoute, private pointsService: PointsService) {}

  ngOnInit(): void {
    const deviceId: string = this.route.snapshot.params['deviceId'];

    this.pointsService.getPoints(deviceId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.points = data.points ?? [];

      // Init the table data source so sorting will work natively.
      this.dataSource = new MatTableDataSource(this.points);
      this.dataSource.sort = this.sort;
    });
  }
}
