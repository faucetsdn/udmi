import { Component, OnDestroy, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { State } from '../../enums';
import { Metric, MetricsService, SystemMetric } from '../../services/metrics.service';
import { Origin, OriginsService } from '../../services/origins.service';

@Component({
  selector: 'app-system-state',
  templateUrl: './system-state.component.html',
  styleUrls: ['./system-state.component.scss']
})
export class SystemStateComponent implements OnInit, OnDestroy {
  stateColorMap = new Map<State, string>([
    [State.unknown, '#DCDCDE'],
    [State.initializing, '#DCDCDE'],
    [State.broken, '#ED553B'],
    [State.healthy, '#00A32A'],
    [State.damaged, '#F0C33C'],
    [State.split, '#F0C33C'],
    [State.up, '#00A32A'],
    [State.down, '#ED553B'],
    [State.active, '#00A32A'],
    [State.inactive, '#C3C4C7']
  ]);
  origin?: Origin;
  data: { name: string, value: number }[] = [];
  dataColor: { domain: string[] } = { domain: [] };
  latest?: SystemMetric;
  systemState?: string;
  selectedMetric?: { type: string, metric: Metric };
  subscriptions: { [key: string]: Subscription } = {};

  constructor(private originService: OriginsService, private metricsService: MetricsService) { }

  ngOnInit(): void {
    this.subscriptions.origin = this.originService.onOriginChange().subscribe((origin) => {
      this.origin = origin;
      this.getSystemMetric(origin);
    });
    this.formatTooltip = this.formatTooltip.bind(this);
    this.onSelectMetric = this.onSelectMetric.bind(this);
  }

  formatTooltip(e: { data: { label: string } }): string {
    const subSystem = this.latest && this.latest[e.data.label as keyof SystemMetric] as Metric;
    return e.data.label + ': ' + (subSystem ? State[subSystem.state] : 'Unknown');
  }

  formatSystemMetric(metric: SystemMetric): void {
    this.data = [];
    this.dataColor.domain = [];
    this.systemState = State[metric.state];
    ['controller', 'egress', 'processes', 'dataPlane'].forEach((type) => {
      const keyType = type as keyof SystemMetric;
      this.data.push({
        name: type,
        value: 1
      });
      const subMetric = metric[keyType] as Metric;
      if (subMetric && this.stateColorMap.has(subMetric.state)) {
        const color = this.stateColorMap.get(subMetric.state) as string;
        this.dataColor.domain.push(color);
      } else {
        this.dataColor.domain.push('#00131C');
      }
    });
  }

  onSelectMetric(e: { label: string }): void {
    const metric = this.latest && this.latest[e.label as keyof SystemMetric] as Metric;
    if (metric) {
      this.selectedMetric = { type: e.label, metric };
    }
  }

  unSelectedMetric(): void {
    delete this.selectedMetric;
  }

  getSystemMetric(origin: Origin): void {
    if (this.subscriptions.systemMetric) {
      this.subscriptions.systemMetric.unsubscribe();
    }
    if (origin) {
      this.subscriptions.systemMetric = this.metricsService.getLatest(origin).subscribe((metric) => {
        this.latest = metric;
        this.formatSystemMetric(metric);
      });
    }
  }

  ngOnDestroy(): void {
    Object.values(this.subscriptions).forEach((subscription) => {
      subscription.unsubscribe();
    });
  }

}
