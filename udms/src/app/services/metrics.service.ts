import { Injectable } from '@angular/core';
import { AngularFirestore } from '@angular/fire/firestore';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { State } from '../enums';
import { Origin } from './origins.service';

export interface Metric {
  timestamp: Date;
  state: State;
  detail: string;
  lastChanged?: Date;
  changeCount?: number;
}

export interface SystemMetric extends Metric {
  controller?: Metric;
  egress?: Metric;
  processes?: Metric;
  dataPlane?: Metric;
}

@Injectable({
  providedIn: 'root'
})
export class MetricsService {

  constructor(private firestore: AngularFirestore) { }

  private mapMetric(metric: any): Metric | undefined {
    if (!metric) {
      return;
    }
    const timestamp = metric.last_updated && metric.last_updated.toDate();
    const lastChanged = metric.last_changed && metric.last_changed.toDate();
    return {
      state: State[metric.state as keyof typeof State],
      detail: metric.detail,
      changeCount: metric.change_count,
      lastChanged,
      timestamp: timestamp || lastChanged
    };
  }

  private mapSystemMetric(metric: any): SystemMetric {
    const systemMetric = this.mapMetric(metric);
    return Object.assign(systemMetric, {
      controller: this.mapMetric(metric.controller),
      egress: this.mapMetric(metric.egress),
      processes: this.mapMetric(metric.processes),
      dataPlane: this.mapMetric(metric.dataplane)
    });
  }

  public getLatest(origin: Origin): Observable<SystemMetric> {
    return this.firestore.collection('origin').doc(origin.id)
      .collection('system', ref => ref.orderBy('last_updated', 'desc').limit(1))
      .valueChanges().pipe(
        filter((docs) => {
          return !!docs.length;
        }),
        map((metrics) => {
          return this.mapSystemMetric(metrics[0]);
        }));
  }
}
