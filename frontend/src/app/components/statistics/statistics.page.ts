import {Component, OnInit, ChangeDetectorRef} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import * as echarts from 'echarts';
import {UserService} from "../../../services/user.service";
import {Injury, StatisticalData, ConsistencyData} from "../../dtos/user"
import {IonicModule} from "@ionic/angular";
import {CommonModule, DecimalPipe} from "@angular/common";

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

  // Injury data
  injuries: Injury[] = [];
  private injuryTimelinesByArea: Map<string, InjuryTimelinePoint[]> = new Map();

  // Charts
  fitnessChart: echarts.ECharts | null = null;
  consistencyChart: echarts.ECharts | null = null;
  injuryChart: echarts.ECharts | null = null;

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

  constructor(
    private http: HttpClient,
    private userService: UserService,
    private cdr: ChangeDetectorRef
  ) {
  }

  ngOnInit() {
    this.loadData();
  }

  ionViewDidEnter() {
    // Initialize charts after view is ready
    setTimeout(() => {
      if (!this.loading) {
        this.initCharts();
      }
    }, 100);
  }

  loadData() {
    this.loading = true;
    console.log("start");
    this.userService.getStats().subscribe({
      next: (data) => {
        if (data) {
          this.noData = false;
          this.processData(data);
          this.cdr.detectChanges();

          // Give DOM time to render, then init charts
          setTimeout(() => {
            this.initCharts();
          }, 200);
        }
        this.loading = false;
        console.log("could not receive any data")
        this.noData = true;
      },
      error: (error) => {
        console.error('Failed to load data:', error);
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

    console.log("=== INJURY DATA ===");
    console.log("Processed injuries:", this.injuries);
    console.log("Injury timelines by area:", this.injuryTimelinesByArea);
  }

  private buildInjuryTimelines(injuries: Injury[]): Map<string, InjuryTimelinePoint[]> {
    const timelinesByArea = new Map<string, InjuryTimelinePoint[]>();

    // Group injuries by affected area
    const injuriesByArea = new Map<string, Injury[]>();
    injuries.forEach(injury => {
      if (!injuriesByArea.has(injury.affectedArea)) {
        injuriesByArea.set(injury.affectedArea, []);
      }
      injuriesByArea.get(injury.affectedArea)!.push(injury);
    });

    // Build timeline for each area
    injuriesByArea.forEach((areaInjuries, area) => {
      const timeline: InjuryTimelinePoint[] = [];

      // Sort all events (both healthy and injury dates) chronologically
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

      // Sort events by date
      events.sort((a, b) => a.date.getTime() - b.date.getTime());

      // Find the earliest event to start the timeline
      if (events.length > 0) {
        const firstEvent = events[0];
        console.log(firstEvent)

        // Start at 0 from the first event
        timeline.push({
          date: firstEvent.date,
          index: 0
        });

        // Process each event
        let currentIndex = 0;
        for (let i = 0; i < events.length; i++) {
          const event = events[i];

          // Add a point just before this event at the current level (for horizontal line)
          const justBefore = new Date(event.date.getTime() - 1);
          timeline.push({
            date: justBefore,
            index: currentIndex
          });

          // Add the event point (vertical jump/drop)
          timeline.push({
            date: event.date,
            index: event.index
          });

          // Update current index
          currentIndex = event.index;
        }

        // Extend to today at the current level
        timeline.push({
          date: new Date(),
          index: currentIndex
        });
      }

      timelinesByArea.set(area, timeline);
      console.log(`Timeline for ${area}:`, timeline);
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
    console.log("=== INIT CHARTS ===");

    const fitnessEl = document.getElementById('fitness-chart');
    const consistencyEl = document.getElementById('consistency-chart');
    const injuryEl = document.getElementById('injury-chart');

    console.log("Injury element:", injuryEl);
    console.log("Injuries length:", this.injuries.length);

    if (fitnessEl) {
      this.fitnessChart = echarts.init(fitnessEl);
      window.addEventListener('resize', () => this.fitnessChart?.resize());
    }

    if (consistencyEl) {
      this.consistencyChart = echarts.init(consistencyEl);
      window.addEventListener('resize', () => this.consistencyChart?.resize());
    }

    if (injuryEl) {
      console.log("Initializing injury chart...");
      this.injuryChart = echarts.init(injuryEl);
      window.addEventListener('resize', () => this.injuryChart?.resize());
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
    console.log("=== UPDATE CHARTS ===");
    this.updateFitnessChart();
    this.updateConsistencyChart();
    this.updateInjuryChart();
  }

  private updateFitnessChart() {
    if (!this.fitnessChart) return;

    const ctlFiltered = this.filterByRange(this.ctlData, this.fitnessRange);
    const atlFiltered = this.filterByRange(this.atlData, this.fitnessRange);
    const tsbFiltered = this.filterByRange(this.tsbData, this.fitnessRange);

    const option: echarts.EChartsOption = {
      title: {
        text: 'Fitness vs Fatigue vs Freshness',
        left: 'center',
        textStyle: {fontSize: 16, fontWeight: 600}
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
        top: 30
      },
      grid: {
        left: '3%',
        right: '5%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any
      },
      yAxis: [
        {
          type: 'value',
          name: 'CTL / ATL',
          position: 'left',
          axisLabel: {formatter: '{value}'}
        },
        {
          type: 'value',
          name: 'TSB',
          position: 'right',
          axisLabel: {formatter: '{value}'}
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

    const option: echarts.EChartsOption = {
      title: {
        text: 'Training Consistency',
        left: 'center',
        textStyle: {fontSize: 16, fontWeight: 600}
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
        top: 30
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any
      },
      yAxis: {
        type: 'value',
        min: 0,
        max: 1,
        axisLabel: {
          formatter: (value: number) => (value * 100).toFixed(0) + '%'
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
    console.log("=== UPDATE INJURY CHART ===");
    console.log("injuryChart exists:", !!this.injuryChart);
    console.log("injuries.length:", this.injuries.length);
    console.log("injuryRange:", this.injuryRange);

    if (!this.injuryChart) {
      console.log("No injury chart instance, trying to reinitialize...");
      const injuryEl = document.getElementById('injury-chart');
      if (injuryEl && this.injuries.length > 0) {
        this.injuryChart = echarts.init(injuryEl);
        console.log("Reinitialized injury chart");
      } else {
        console.log("Cannot reinitialize:", {hasElement: !!injuryEl, hasInjuries: this.injuries.length > 0});
        return;
      }
    }

    if (this.injuries.length === 0) {
      console.log("No injuries to display");
      return;
    }

    // Calculate cutoff date for filtering
    const cutoff = this.injuryRange === -1 ? new Date(0) : (() => {
      const d = new Date();
      d.setDate(d.getDate() - this.injuryRange);
      return d;
    })();

    console.log("Cutoff date:", cutoff);

    // Filter ATL data
    const atlFiltered = this.filterByRange(this.atlData, this.injuryRange);

    // Create series for each injury area with filtered data
    const series: any[] = [];
    const colors = ['#ef4444', '#f59e0b', '#10b981', '#3b82f6', '#8b5cf6', '#ec4899'];
    let colorIndex = 0;

    this.injuryTimelinesByArea.forEach((timeline, area) => {
      // Filter timeline by date range
      const filteredTimeline = timeline.filter(p => p.date >= cutoff);

      console.log(`Area: ${area}, Original points: ${timeline.length}, Filtered points: ${filteredTimeline.length}`);

      if (filteredTimeline.length > 0) {
        const seriesData = filteredTimeline.map(p => [p.date, p.index]);

        series.push({
          name: this.formatInjuryArea(area),
          type: 'line',
          step: false,  // Changed from 'end' to false for sharp corners
          data: seriesData,
          itemStyle: {color: colors[colorIndex % colors.length]},
          yAxisIndex: 0,
          lineStyle: {width: 3},
          symbol: 'none',  // Remove dots for cleaner look
          areaStyle: {  // Add slight fill for better visibility
            opacity: 0.1
          }
        });
        colorIndex++;
      }
    });

    // Add ATL as reference
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

    console.log("Total series:", series.length);

    if (series.length === 0) {
      console.log("No series data to display");
      // Clear the chart
      this.injuryChart.setOption({
        title: {
          text: 'No injury data in selected time range',
          left: 'center',
          textStyle: {fontSize: 16, fontWeight: 600}
        }
      });
      return;
    }

    const option: echarts.EChartsOption = {
      title: {
        text: 'Injury Index Over Time vs Fatigue (ATL)',
        left: 'center',
        textStyle: {fontSize: 16, fontWeight: 600}
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
        type: 'scroll'
      },
      grid: {
        left: '3%',
        right: '5%',
        bottom: '3%',
        containLabel: true
      },
      xAxis: {
        type: 'time',
        boundaryGap: false as any,
      },
      yAxis: [
        {
          type: 'value',
          name: 'Injury Index',
          position: 'left',
          min: 0,
          max: 1,
          axisLabel: {
            formatter: (value: number) => (value * 100).toFixed(0) + '%'
          }
        },
        {
          type: 'value',
          name: 'ATL',
          position: 'right',
          axisLabel: {formatter: '{value}'}
        }
      ],
      series: series
    };

    console.log("Setting chart option with xAxis min:", cutoff);
    this.injuryChart.setOption(option, true); // true = notMerge, replace the whole option
    console.log("Chart option set successfully");
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
    console.log("Setting injury range to:", days);
    this.injuryRange = days;
    this.updateInjuryChart();
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
}
