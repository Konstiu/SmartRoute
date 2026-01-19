import {Component, OnInit, ChangeDetectorRef} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import * as echarts from 'echarts';
import {UserService} from "../../../services/user.service";
import {Injury, StatisticalData, ConsistencyData, RunHistory} from "../../dtos/user"
import {IonicModule} from "@ionic/angular";
import {CommonModule, DecimalPipe} from "@angular/common";
import {DetailedActivity} from "../../dtos/Activity";

interface TimeSeriesPoint {
  x: Date;
  y: number | null;
}

interface InjuryTimelinePoint {
  date: Date;
  index: number;
}

@Component({
  selector: 'app-stats',
  templateUrl: './statistics.page.html',
  standalone: true,
  imports: [
    IonicModule,
    DecimalPipe,
    CommonModule
  ],
  styleUrls: ['./statistics.page.scss']
})
export class StatsPage implements OnInit {
  // Summary data
  todayCtl: number | null = null;
  todayAtl: number | null = null;
  todayTsb: number | null = null;
  todayConsistency: number | null = null;

  // Run history data
  runHistory: RunHistory | null = null;
  recentRuns: DetailedActivity[] = [];

  // Injury data
  injuries: Injury[] = [];
  private injuryTimelinesByArea: Map<string, InjuryTimelinePoint[]> = new Map();

  // Charts
  fitnessChart: echarts.ECharts | null = null;
  consistencyChart: echarts.ECharts | null = null;
  injuryChart: echarts.ECharts | null = null;
  runDistanceChart: echarts.ECharts | null = null;

  // Data storage
  private ctlData: TimeSeriesPoint[] = [];
  private atlData: TimeSeriesPoint[] = [];
  private tsbData: TimeSeriesPoint[] = [];
  private finalScoreData: TimeSeriesPoint[] = [];
  private frequencyData: TimeSeriesPoint[] = [];
  private regularityData: TimeSeriesPoint[] = [];

  loading = true;
  noData = false;

  // Range selection
  fitnessRange: number = 90;
  consistencyRange: number = 90;
  injuryRange: number = 180;
  runHistoryRange: number = 30;

  constructor(
    private http: HttpClient,
    private userService: UserService,
    private cdr: ChangeDetectorRef
  ) {
  }

  // Get computed CSS variable color
  private getCSSColor(variable: string): string {
    return getComputedStyle(document.documentElement).getPropertyValue(variable).trim();
  }

  // Get theme-aware colors for charts
  private getChartColors() {
    const a =
      {
        textPrimary: this.getCSSColor('--chart-text-primary'),
        textSecondary: this.getCSSColor('--chart-text-secondary'),
        axisLine: this.getCSSColor('--chart-axis-line'),
        gridLine: this.getCSSColor('--chart-grid-line'),
        border: this.getCSSColor('--chart-border')
      };
    return a
  }

  ngOnInit() {
    this.loadData();
  }

  ionViewDidEnter() {
    setTimeout(() => {
      if (!this.loading) {
        this.initCharts();
      }
    }, 100);
  }

  loadData() {
    this.loading = true;
    this.userService.getStats().subscribe({
      next: (data) => {
        if (data) {
          this.noData = false;
          this.processData(data);
          this.cdr.detectChanges();

          setTimeout(() => {
            this.initCharts();
          }, 200);
        }
        this.loading = false;
      },
      error: (error) => {
        console.error('Failed to load data:', error);
        this.noData = true;
        this.loading = false;
      }
    });
  }

  private processData(data: StatisticalData) {
    // Extract all unique dates
    const allDates = this.extractAllDates(data.consistencyHistory);

    // Convert to time series
    this.ctlData = this.mapToTimeSeries(data.consistencyHistory.ctlHistory, allDates);
    this.atlData = this.mapToTimeSeries(data.consistencyHistory.atlHistory, allDates);
    this.tsbData = this.mapToTimeSeries(data.consistencyHistory.tsbHistory, allDates);

    this.finalScoreData = this.mapToTimeSeries(
      this.extractField(data.consistencyHistory.consistencyHistory, 'finalScore'),
      allDates
    );
    this.frequencyData = this.mapToTimeSeries(
      this.extractField(data.consistencyHistory.consistencyHistory, 'frequencyConsistency'),
      allDates
    );
    this.regularityData = this.mapToTimeSeries(
      this.extractField(data.consistencyHistory.consistencyHistory, 'regularityConsistency'),
      allDates
    );

    // Trim leading zeros
    this.ctlData = this.trimLeadingZeros(this.ctlData);
    this.atlData = this.trimLeadingZeros(this.atlData);
    this.tsbData = this.trimLeadingZeros(this.tsbData);

    // Set today's summary values
    this.todayCtl = this.getLatestValue(this.ctlData);
    this.todayAtl = this.getLatestValue(this.atlData);
    this.todayTsb = this.getLatestValue(this.tsbData);
    this.todayConsistency = this.getLatestValue(this.finalScoreData);

    // Process injuries
    const injuriesList = data.injuryHistory?.injuriesList || [];
    this.injuries = [...injuriesList];
    this.injuryTimelinesByArea = this.buildInjuryTimelines(injuriesList);

    // Process run history
    if (data.runHistory) {
      this.runHistory = data.runHistory;
      // Sort runs by date (most recent first) and take the last 10
      this.recentRuns = [...data.runHistory.runHistory]
        .sort((a, b) => new Date(b.startDate).getTime() - new Date(a.startDate).getTime())
        .slice(0, 10);
    }

  }

  private buildInjuryTimelines(injuries: Injury[]): Map<string, InjuryTimelinePoint[]> {
    const timelinesByArea = new Map<string, InjuryTimelinePoint[]>();

    const injuriesByArea = new Map<string, Injury[]>();
    injuries.forEach(injury => {
      if (!injuriesByArea.has(injury.affectedArea)) {
        injuriesByArea.set(injury.affectedArea, []);
      }
      injuriesByArea.get(injury.affectedArea)!.push(injury);
    });

    injuriesByArea.forEach((areaInjuries, area) => {
      const timeline: InjuryTimelinePoint[] = [];

      interface Event {
        date: Date;
        type: 'healthy' | 'injury';
        index: number;
      }

      const events: Event[] = [];
      areaInjuries.forEach(injury => {
        events.push({
          date: new Date(injury.lastHealthyDate),
          type: 'healthy',
          index: injury.injuryIndex
        });
        events.push({
          date: new Date(injury.lastInjuryDate),
          type: 'injury',
          index: 0
        });
      });

      events.sort((a, b) => a.date.getTime() - b.date.getTime());

      if (events.length > 0) {
        const firstEvent = events[0];

        timeline.push({
          date: firstEvent.date,
          index: 0
        });

        let currentIndex = 0;
        for (let i = 0; i < events.length; i++) {
          const event = events[i];

          const justBefore = new Date(event.date.getTime() - 1);
          timeline.push({
            date: justBefore,
            index: currentIndex
          });

          timeline.push({
            date: event.date,
            index: event.index
          });

          currentIndex = event.index;
        }

        timeline.push({
          date: new Date(),
          index: currentIndex
        });
      }

      timelinesByArea.set(area, timeline);
    });

    return timelinesByArea;
  }

  private extractAllDates(consistencyHistory: any): string[] {
    const dateSet = new Set<string>();

    Object.keys(consistencyHistory.ctlHistory || {}).forEach(d => dateSet.add(d));
    Object.keys(consistencyHistory.atlHistory || {}).forEach(d => dateSet.add(d));
    Object.keys(consistencyHistory.tsbHistory || {}).forEach(d => dateSet.add(d));
    Object.keys(consistencyHistory.consistencyHistory || {}).forEach(d => dateSet.add(d));

    return Array.from(dateSet).sort();
  }

  private extractField(obj: { [key: string]: any }, field: string): { [key: string]: number } {
    const result: { [key: string]: number } = {};

    for (const [date, value] of Object.entries(obj)) {
      if (value && typeof value === 'object' && field in value) {
        result[date] = value[field];
      }
    }

    return result;
  }

  private mapToTimeSeries(
    data: { [key: string]: number },
    allDates: string[]
  ): TimeSeriesPoint[] {
    return allDates.map(dateStr => ({
      x: new Date(dateStr),
      y: data[dateStr] ?? null
    }));
  }

  private trimLeadingZeros(data: TimeSeriesPoint[]): TimeSeriesPoint[] {
    const firstNonZero = data.findIndex(p => p.y !== null && p.y !== 0);
    return firstNonZero >= 0 ? data.slice(firstNonZero) : data;
  }

  private getLatestValue(data: TimeSeriesPoint[]): number | null {
    for (let i = data.length - 1; i >= 0; i--) {
      if (data[i].y !== null) {
        return data[i].y;
      }
    }
    return null;
  }

  private initCharts() {
    const fitnessEl = document.getElementById('fitness-chart');
    const consistencyEl = document.getElementById('consistency-chart');
    const injuryEl = document.getElementById('injury-chart');
    const runDistanceEl = document.getElementById('run-distance-chart');

    if (fitnessEl) {
      this.fitnessChart = echarts.init(fitnessEl);
      window.addEventListener('resize', () => this.fitnessChart?.resize());
    }

    if (consistencyEl) {
      this.consistencyChart = echarts.init(consistencyEl);
      window.addEventListener('resize', () => this.consistencyChart?.resize());
    }

    if (injuryEl) {
      this.injuryChart = echarts.init(injuryEl);
      window.addEventListener('resize', () => this.injuryChart?.resize());
    }

    if (runDistanceEl && this.runHistory) {
      this.runDistanceChart = echarts.init(runDistanceEl);
      window.addEventListener('resize', () => this.runDistanceChart?.resize());
    }

    this.updateCharts();
  }

  private filterByRange(data: TimeSeriesPoint[], days: number): TimeSeriesPoint[] {
    if (days === -1 || data.length === 0) return data;

    const cutoff = new Date();
    cutoff.setDate(cutoff.getDate() - days);

    return data.filter(p => p.x >= cutoff);
  }

  updateCharts() {
    this.updateFitnessChart();
    this.updateConsistencyChart();
    this.updateInjuryChart();
    this.updateRunDistanceChart();
  }

  private updateFitnessChart() {
    if (!this.fitnessChart) return;

    const ctlFiltered = this.filterByRange(this.ctlData, this.fitnessRange);
    const atlFiltered = this.filterByRange(this.atlData, this.fitnessRange);
    const tsbFiltered = this.filterByRange(this.tsbData, this.fitnessRange);

    const colors = this.getChartColors();

    const option: echarts.EChartsOption = {
      title: {
        text: 'Fitness vs Fatigue vs Freshness',
        left: 'center',
        textStyle: {
          fontSize: 16,
          fontWeight: 600,
          color: colors.textPrimary
        }
      },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          let result = `${new Date(params[0].value[0]).toLocaleDateString()}<br/>`;
          params.forEach((item: any) => {
            result += `${item.marker} ${item.seriesName}: <b>${item.value[1]?.toFixed(1) ?? 'N/A'}</b><br/>`;
          });
          return result;
        }
      },
      legend: {
        data: ['CTL (Fitness)', 'ATL (Fatigue)', 'TSB (Freshness)'],
        top: 30,
        textStyle: {
          color: colors.textSecondary
        }
      },
      grid: {
        left: '3%',
        right: '5%',
        bottom: '3%',
        containLabel: true,
        borderColor: colors.border
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any,
        axisLabel: {
          color: colors.textSecondary
        },
        axisLine: {
          lineStyle: {
            color: colors.axisLine
          }
        },
        splitLine: {
          lineStyle: {
            color: colors.gridLine
          }
        }
      },
      yAxis: [
        {
          type: 'value',
          name: 'CTL / ATL',
          position: 'left',
          nameTextStyle: {
            color: colors.textSecondary
          },
          axisLabel: {
            formatter: '{value}',
            color: colors.textSecondary
          },
          axisLine: {
            lineStyle: {
              color: colors.axisLine
            }
          },
          splitLine: {
            lineStyle: {
              color: colors.gridLine
            }
          }
        },
        {
          type: 'value',
          name: 'TSB',
          position: 'right',
          nameTextStyle: {
            color: colors.textSecondary
          },
          axisLabel: {
            formatter: '{value}',
            color: colors.textSecondary
          },
          axisLine: {
            lineStyle: {
              color: colors.axisLine
            }
          },
          splitLine: {
            show: false
          }
        }
      ],
      series: [
        {
          name: 'CTL (Fitness)',
          type: 'line',
          smooth: true,
          data: ctlFiltered.map(p => [p.x, p.y]),
          itemStyle: {color: '#3b82f6'},
          yAxisIndex: 0
        },
        {
          name: 'ATL (Fatigue)',
          type: 'line',
          smooth: true,
          data: atlFiltered.map(p => [p.x, p.y]),
          itemStyle: {color: '#ef4444'},
          yAxisIndex: 0
        },
        {
          name: 'TSB (Freshness)',
          type: 'line',
          smooth: true,
          data: tsbFiltered.map(p => [p.x, p.y]),
          itemStyle: {color: '#10b981'},
          yAxisIndex: 1,
          markLine: {
            symbol: 'none',
            data: [{yAxis: 0}],
            lineStyle: {color: '#666', type: 'dashed'}
          }
        }
      ]
    };

    this.fitnessChart.setOption(option);
  }

  private updateConsistencyChart() {
    if (!this.consistencyChart) return;

    const finalFiltered = this.filterByRange(this.finalScoreData, this.consistencyRange);
    const freqFiltered = this.filterByRange(this.frequencyData, this.consistencyRange);
    const regFiltered = this.filterByRange(this.regularityData, this.consistencyRange);

    const colors = this.getChartColors();

    const option: echarts.EChartsOption = {
      title: {
        text: 'Training Consistency',
        left: 'center',
        textStyle: {
          fontSize: 16,
          fontWeight: 600,
          color: colors.textPrimary
        }
      },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          let result = `${new Date(params[0].value[0]).toLocaleDateString()}<br/>`;
          params.forEach((item: any) => {
            const val = item.value[1];
            result += `${item.marker} ${item.seriesName}: <b>${val ? (val * 100).toFixed(0) + '%' : 'N/A'}</b><br/>`;
          });
          return result;
        }
      },
      legend: {
        data: ['Final Score', 'Frequency', 'Regularity'],
        top: 30,
        textStyle: {
          color: colors.textSecondary
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
        borderColor: colors.border
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any,
        axisLabel: {
          color: colors.textSecondary
        },
        axisLine: {
          lineStyle: {
            color: colors.axisLine
          }
        },
        splitLine: {
          lineStyle: {
            color: colors.gridLine
          }
        }
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 1,
        nameTextStyle: {
          color: colors.textSecondary
        },
        axisLabel: {
          formatter: (value: number) => (value * 100).toFixed(0) + '%',
          color: colors.textSecondary
        },
        axisLine: {
          lineStyle: {
            color: colors.axisLine
          }
        },
        splitLine: {
          lineStyle: {
            color: colors.gridLine
          }
        }
      },
      series: [
        {
          name: 'Final Score',
          type: 'line',
          smooth: true,
          data: finalFiltered.map(p => [p.x, p.y]),
          itemStyle: {color: '#8b5cf6'},
          lineStyle: {width: 3}
        },
        {
          name: 'Frequency',
          type: 'line',
          smooth: true,
          data: freqFiltered.map(p => [p.x, p.y]),
          itemStyle: {color: '#f59e0b'}
        },
        {
          name: 'Regularity',
          type: 'line',
          smooth: true,
          data: regFiltered.map(p => [p.x, p.y]),
          itemStyle: {color: '#06b6d4'}
        }
      ]
    };

    this.consistencyChart.setOption(option);
  }

  private updateInjuryChart() {
    if (!this.injuryChart) {
      const injuryEl = document.getElementById('injury-chart');
      if (injuryEl && this.injuries.length > 0) {
        this.injuryChart = echarts.init(injuryEl);
      } else {
        return;
      }
    }

    if (this.injuries.length === 0) {
      return;
    }

    const cutoff = this.injuryRange === -1 ? new Date(0) : (() => {
      const d = new Date();
      d.setDate(d.getDate() - this.injuryRange);
      return d;
    })();

    const atlFiltered = this.filterByRange(this.atlData, this.injuryRange);

    const series: any[] = [];
    const colors = ['#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', '#ec4899'];
    let colorIndex = 0;

    this.injuryTimelinesByArea.forEach((timeline, area) => {
      const filteredTimeline = timeline.filter(p => p.date >= cutoff);

      if (filteredTimeline.length > 0) {
        const seriesData = filteredTimeline.map(p => [p.date, p.index]);

        series.push({
          name: this.formatInjuryArea(area),
          type: 'line',
          step: false,
          data: seriesData,
          itemStyle: {color: colors[colorIndex % colors.length]},
          yAxisIndex: 0,
          lineStyle: {width: 3},
          symbol: 'none',
          areaStyle: {
            opacity: 0.1
          }
        });
        colorIndex++;
      }
    });

    if (atlFiltered.length > 0) {
      series.push({
        name: 'ATL (Fatigue)',
        type: 'line',
        smooth: true,
        data: atlFiltered.map(p => [p.x, p.y]),
        itemStyle: {color: '#666666'},
        yAxisIndex: 1,
        lineStyle: {width: 2, type: 'dashed'},
        opacity: 0.5
      });
    }

    if (series.length === 0) {
      const colorsEmpty = this.getChartColors();
      this.injuryChart.setOption({
        title: {
          text: 'No injury data in selected time range',
          left: 'center',
          textStyle: {
            fontSize: 16,
            fontWeight: 600,
            color: colorsEmpty.textPrimary
          }
        }
      });
      return;
    }

    const colors2 = this.getChartColors();

    const option: echarts.EChartsOption = {
      title: {
        text: 'Injury Index Over Time vs Fatigue (ATL)',
        left: 'center',
        textStyle: {
          fontSize: 16,
          fontWeight: 600,
          color: colors2.textPrimary
        }
      },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          let result = `${new Date(params[0].value[0]).toLocaleDateString()}<br/>`;
          params.forEach((item: any) => {
            const val = item.value[1];
            if (item.seriesName === 'ATL (Fatigue)') {
              result += `${item.marker} ${item.seriesName}: <b>${val?.toFixed(1) ?? 'N/A'}</b><br/>`;
            } else {
              result += `${item.marker} ${item.seriesName}: <b>${val ? (val * 100).toFixed(0) + '%' : 'N/A'}</b><br/>`;
            }
          });
          return result;
        }
      },
      legend: {
        data: series.map(s => s.name),
        top: 30,
        type: 'scroll',
        textStyle: {
          color: colors2.textSecondary
        }
      },
      grid: {
        left: '3%',
        right: '5%',
        bottom: '3%',
        containLabel: true,
        borderColor: colors2.border
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any,
        axisLabel: {
          color: colors2.textSecondary
        },
        axisLine: {
          lineStyle: {
            color: colors2.axisLine
          }
        },
        splitLine: {
          lineStyle: {
            color: colors2.gridLine
          }
        }
      },
      yAxis: [
        {
          type: 'value',
          name: 'Injury Index',
          position: 'left',
          min: 0,
          max: 1,
          nameTextStyle: {
            color: colors2.textSecondary
          },
          axisLabel: {
            formatter: (value: number) => (value * 100).toFixed(0) + '%',
            color: colors2.textSecondary
          },
          axisLine: {
            lineStyle: {
              color: colors2.axisLine
            }
          },
          splitLine: {
            lineStyle: {
              color: colors2.gridLine
            }
          }
        },
        {
          type: 'value',
          name: 'ATL',
          position: 'right',
          nameTextStyle: {
            color: colors2.textSecondary
          },
          axisLabel: {
            formatter: '{value}',
            color: colors2.textSecondary
          },
          axisLine: {
            lineStyle: {
              color: colors2.axisLine
            }
          },
          splitLine: {
            show: false
          }
        }
      ],
      series: series
    };

    this.injuryChart.setOption(option, true);
  }

  private updateRunDistanceChart() {
    if (!this.runDistanceChart || !this.runHistory) return;

    const cutoff = this.runHistoryRange === -1 ? new Date(0) : (() => {
      const d = new Date();
      d.setDate(d.getDate() - this.runHistoryRange);
      return d;
    })();

    const filteredRuns = this.runHistory.runHistory
      .filter(run => new Date(run.startDate) >= cutoff)
      .sort((a, b) => new Date(a.startDate).getTime() - new Date(b.startDate).getTime());

    const colors = this.getChartColors();

    const option: echarts.EChartsOption = {
      title: {
        text: 'Run Distance Over Time',
        left: 'center',
        textStyle: {
          fontSize: 16,
          fontWeight: 600,
          color: colors.textPrimary
        }
      },
      tooltip: {
        trigger: 'axis',
        formatter: (params: any) => {
          const run = params[0];
          const data = filteredRuns[run.dataIndex];
          return `
            <b>${data.name}</b><br/>
            Date: ${new Date(data.startDate).toLocaleDateString()}<br/>
            Distance: <b>${(data.distance / 1000).toFixed(2)} km</b><br/>
            Duration: <b>${this.formatDuration(data.movingTime)}</b><br/>
            Pace: <b>${this.formatPace(data.averageSpeed)}</b><br/>
            ${data.averageHeartrate ? `HR: <b>${data.averageHeartrate.toFixed(0)} bpm</b><br/>` : ''}
          `;
        }
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
        borderColor: colors.border
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any,
        axisLabel: {
          color: colors.textSecondary
        },
        axisLine: {
          lineStyle: {
            color: colors.axisLine
          }
        },
        splitLine: {
          lineStyle: {
            color: colors.gridLine
          }
        }
      },
      yAxis: {
        type: 'value',
        name: 'Distance (km)',
        nameTextStyle: {
          color: colors.textSecondary
        },
        axisLabel: {
          formatter: (value: number) => value.toFixed(1),
          color: colors.textSecondary
        },
        axisLine: {
          lineStyle: {
            color: colors.axisLine
          }
        },
        splitLine: {
          lineStyle: {
            color: colors.gridLine
          }
        }
      },
      series: [
        {
          name: 'Distance',
          type: 'bar',
          data: filteredRuns.map(run => [
            new Date(run.startDate),
            run.distance / 1000
          ]),
          itemStyle: {color: '#3b82f6'},
          barWidth: '60%'
        }
      ]
    };

    this.runDistanceChart.setOption(option);
  }

  setFitnessRange(days: number) {
    this.fitnessRange = days;
    this.updateFitnessChart();
  }

  setConsistencyRange(days: number) {
    this.consistencyRange = days;
    this.updateConsistencyChart();
  }

  setInjuryRange(days: number) {
    this.injuryRange = days;
    this.updateInjuryChart();
  }

  setRunHistoryRange(days: number) {
    this.runHistoryRange = days;
    this.updateRunDistanceChart();
  }

  getInjuryColor(index: number): string {
    if (index >= 0.7) return 'danger';
    if (index >= 0.4) return 'warning';
    return 'success';
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr).toLocaleDateString();
  }

  formatInjuryArea(area: string): string {
    return area.replace(/_/g, ' ')
      .toLowerCase()
      .replace(/\b\w/g, l => l.toUpperCase())
      .replace(' Region', '');
  }

  formatDuration(seconds: number): string {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m ${secs}s`;
  }

  formatPace(metersPerSecond: number): string {
    const minutesPerKm = 1000 / (metersPerSecond * 60);
    const minutes = Math.floor(minutesPerKm);
    const seconds = Math.round((minutesPerKm - minutes) * 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}/km`;
  }
}
