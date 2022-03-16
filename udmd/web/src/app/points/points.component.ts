import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Point } from './points';
import { PointsService } from './points.service';

@Component({
  templateUrl: './points.component.html',
  styleUrls: ['./points.component.scss'],
})
export class PointsComponent implements OnInit {
  points: Point[] = [];
  loading: boolean = true;

  constructor(private route: ActivatedRoute, private pointsService: PointsService) {}

  ngOnInit(): void {
    const deviceId: string = this.route.parent?.snapshot.parent?.params['id'];

    this.pointsService.getPoints(deviceId).subscribe(({ data, loading }) => {
      this.loading = loading;
      this.points = data.device?.points ?? [];
    });
  }
}
