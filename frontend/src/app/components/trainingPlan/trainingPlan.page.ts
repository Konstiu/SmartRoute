import { AlertController, IonicModule, ModalController, ToastController } from '@ionic/angular';
import { Component, inject, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  formatDistance,
  formatElevation, formatInjuryIndex,
  formatPace,
  formatPrecipitation,
  formatTemperature,
  formatWindDirection,
  formatWindSpeed,
} from "../../util/formatters";
import { RecommendedActivityDto, SessionType, SaveRouteDto } from "../../dtos/recommended-activity";
import { Router } from "@angular/router";
import { BodyPart, getBodyPartLabel, getSeverityColor } from "../../dtos/injuries";
import { TrainingPlanService } from "../../../services/training-plan.service";
import { MapComponent } from '../map/map.component';
import { RouteService } from 'src/services/route.service';
import { convertPolylineToCoordinateList } from 'src/services/utils';
import { WeatherInfoComponent } from '../weather/weather.component';
import { MapModalComponent } from '../map/mapModal.component'
import { MAP_MARKER_COLORS, coloredMarker } from '../map/map-icon';
import { StopsService } from 'src/services/add-stops.service';
import { firstValueFrom } from 'rxjs';
import { GeoJsonPosition, AddStopsRequest } from '../../dtos/add-stops';
import { latLng, LatLng, Layer, marker, polyline, Polyline, Marker, LatLngBounds } from 'leaflet';
import L from 'leaflet';
import 'leaflet-polylinedecorator';
import {encodePolyline} from "../../util/polyline-encode-decode";
import { TrainingPlan7dDto, PlannedDayDto, WorkoutType } from "../../dtos/training-plan-7d";
import { TrainingPlan7dService } from 'src/services/training-plan-7d.service';

type RouteUpdate = { layers: Layer[]; bounds: LatLngBounds | null };

@Component({
  selector: 'app-trainingplan',
  templateUrl: 'trainingPlan.page.html',
  styleUrls: ['trainingPlan.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, MapComponent]
})
export class TrainingPlanPage implements OnInit {
  @ViewChild(MapComponent) mapComponent!: MapComponent;

  private readonly router: Router = inject(Router);
  private readonly service: TrainingPlanService = inject(TrainingPlanService);
  private readonly plan7dService: TrainingPlan7dService = inject(TrainingPlan7dService);
  private readonly ROUTE_NOT_FOUND_CODE = 'ROUTE_NOT_FOUND';

  private routeLine: Polyline | null = null;
  private routeLineGeoPosition: GeoJsonPosition[] = [];
  private userLocationMarker: Marker | null = null;
  private routeBounds: LatLngBounds | null = null;
  private stopsService = inject(StopsService);
  private committedStops: LatLng[] = [];

  private initialPlanLoaded = false;
  private initialPlanLocation: LatLng | null = null;
  private initialRouteFitDone = false;
  private pendingInitialLocation: LatLng | null = null;
  private initialRouteGenerated = false;
  private applyingStart = false;

  private originalRouteBounds: LatLngBounds | null = null;
  private originalLatlngs: LatLng[] | null = null;
  private originalDistance: number | null = null;
  private originalElevation: number | null = null;
  private originalStart: LatLng | null = null;
  private originalRouteLineGeoPosition: GeoJsonPosition[] = [];

  private toastCtrl = inject(ToastController);
  private modalCtrl = inject(ModalController);

  private showRoute = false;
  private routeArrows: Layer | null = null;

  error: string | null = null;
  isLoadingWeek: boolean = true;
  isLoadingDay: boolean = false;
  latlngs: LatLng[] | null = null;
  layers: Layer[] = [];
  routeService = inject(RouteService);
  alertController = inject(AlertController);
  isRouteSaved = false;
  date: string = new Date().toLocaleDateString();

  weekPlan: TrainingPlan7dDto | null = null;
  selectedDay: PlannedDayDto | null = null;
  planId: string | null = null;

  recommendedActivity: RecommendedActivityDto | undefined = {
    name: "Gym Session",
    type: SessionType.GYM,
    route: {
      distance: 5421,
      pace: 2.6,
      elevation: 22,
    },
    weather: {
      weatherScore: .9,
      temperature: 16,
      windSpeed: 4,
      precipitation: 0,
      relativeHumidity: 50,
      weatherPerformancePenalty: 1.0,
      weatherScoreDescription: "Excellent weather",
      weatherSummary: {
        temperatureText: "Mild temperatures, comfortable for most training.",
        windText: "Light winds with little impact.",
        precipitationText: "No precipitation expected.",
      }
    },
    athleteStatus: {
      tsb: 22,
      readinessScore: 75,
      injuryIndex: 0.4,
      injuries: [
        {
          injuryId: 0,
          injuryIndex: 0.8,
          affectedArea: BodyPart.FEET_REGION,
          lastHealthyDate: "",
          lastInjuryDate: "",
        }
      ]
    },
    gymSession: {
      id: 1,
      exercises: [
        {
          name: "Exercise 1",
          exerciseId: "1",
          bodyParts: ["core"],
          equipments: [],
          gifUrl: "",
          instructions: [],
          secondaryMuscles: [],
          targetMuscles: []
        },
        {
          name: "Exercise 2",
          exerciseId: "2",
          bodyParts: ["core"],
          equipments: [],
          gifUrl: "",
          instructions: [],
          secondaryMuscles: [],
          targetMuscles: []
        },
        {
          name: "Exercise 3",
          exerciseId: "3",
          bodyParts: ["core"],
          equipments: [],
          gifUrl: "",
          instructions: [],
          secondaryMuscles: [],
          targetMuscles: []
        },
        {
          name: "Exercise 4",
          exerciseId: "4",
          bodyParts: ["core"],
          equipments: [],
          gifUrl: "",
          instructions: [],
          secondaryMuscles: [],
          targetMuscles: []
        },
      ],
      sets: 4,
      reps: 40,
    }
  };

  // =====================================================
  // Lifecycle
  // =====================================================

  ngOnInit(): void {
    this.loadWeekPlan();
  }

  ionViewDidEnter() {
    // page is now visible -> Leaflet can compute sizes correctly
    this.forceMapResize();
  }

  // =====================================================
  // Training plan loading
  // =====================================================

  loadWeekPlan(location?: LatLng): void {
    this.isLoadingWeek = true;
    this.error = null;

    const lat = location?.lat ?? 48.21;
    const lng = location?.lng ?? 16.36;

    this.plan7dService.getNext7Days(lat, lng, {
        debug: false,
        seed: 20,
        regen: true,
        historyDays: 60,
        historyMean: 35,
        historyStd: 8,
        readiness: 50,
        injuryIndex: 0.5,
        ctl: 50,
        atl: 35
      }).subscribe({
      next: (plan: TrainingPlan7dDto) => {
        this.weekPlan = plan;
        this.planId = plan.planId ?? null;

        const todayIso = new Date().toISOString().slice(0, 10); // YYYY-MM-DD
        const initial = plan.days.find(d => d.date === todayIso) ?? plan.days[0];

        this.selectDay(initial);
        this.isLoadingWeek = false;
      },
      error: err => {
        console.error(err);
        this.isLoadingWeek = false;
        this.error = "Failed to load 7-day plan.";
      }
    });
  }

  loadTrainingPlan(location?: LatLng): void {
    this.isLoadingDay = true;
    this.error = null;

    const lat = location?.lat ?? 48.21;
    const lng = location?.lng ?? 16.36;

    this.service.getTrainingPlan(lat, lng).subscribe({
      next: res => {
        this.recommendedActivity = res;
        console.log(this.recommendedActivity);
        this.error = null;
        this.isLoadingDay = false;

        if (this.pendingInitialLocation && !this.initialRouteGenerated) {
          this.initialRouteGenerated = true;
          const loc = this.pendingInitialLocation;
          this.pendingInitialLocation = null;

          this.generateRouteFromLocation(loc, true);
        }

      },
      error: err => {
        console.error(err);
        this.isLoadingDay = false;
        this.error = "Failed to load Training Plan.";
      }
    });
  }

  navigateToGymExercise(exerciseId: number) {
    this.router.navigate(['tabs/gym/' + exerciseId]);
  }

  /**
   * Applies a new start location:
   * refreshes training plan and regenerates route accordingly
   */
  private async applyStartLocation(location: LatLng, updateBaseline: boolean) {
    this.committedStops = [];

    if (this.applyingStart) {
      return;
      }

    this.applyingStart = true;

    try {
      // ensure marker exists (you can also move it only after success if you want)
      if (!this.userLocationMarker) {
        this.userLocationMarker = marker(location, { icon: coloredMarker(MAP_MARKER_COLORS.start) });
      } else {
        this.userLocationMarker.setLatLng(location);
      }

      // 1) refresh training plan for this location

      // 2) generate route using the new plan distance
      await this.generateRouteFromLocationAsync(location, updateBaseline);

      // (generateRouteFromLocationAsync already rebuilds layers + refits)
    } catch (err: any) {
      console.error('applyStartLocation failed', err);
      await this.showToast('Could not refresh plan/route for this location.', 3500, 'danger');
      // optionally rollback marker here if you keep a snapshot
    } finally {
      this.applyingStart = false;
    }
  }

  // =====================================================
  // Map lifecycle + resize helpers
  // =====================================================

  /** Forces Leaflet to recalculate map size (used after layout changes) */
  private forceMapResize() {
    requestAnimationFrame(() => this.mapComponent?.map?.invalidateSize(true));
    setTimeout(() => this.mapComponent?.map?.invalidateSize(true), 150);
    setTimeout(() => this.mapComponent?.map?.invalidateSize(true), 400);
  }

  /** Fits the preview map to the current route bounds */
  private refitPreviewMap() {
    const map = this.mapComponent?.map;
    const bounds = this.routeBounds;

    if (!map) {
      return;
      }

    requestAnimationFrame(() => {
      map.invalidateSize(true);

      if (bounds) {
        requestAnimationFrame(() => {
          map.fitBounds(bounds, { padding: [30, 30], animate: true, maxZoom: 16 });
        });
      }
    });
  }

  // =====================================================
  // Geolocation UI handlers
  // =====================================================

  /** Called when no geolocation is available and user must place marker manually */
  async onGeolocationError() {
    const alert = await this.alertController.create({
      header: 'Unable to determine location',
      message: 'Choose a starting point on the map to generate a route.',
      buttons: [
        {
          text: 'Choose on map',
          handler: () => this.openMapModal({ mode: 'SET_START' })
        }
      ]
    });

    await alert.present();
  }

  /** Handles the first or updated start location selection */
  async onLocationSelected(location: LatLng) {
    this.originalStart = location;
    await this.applyStartLocation(location, true);
  }

  // =====================================================
  // Route generation (ORS)
  // =====================================================

  /** Generates a route for the current start using Observable API */
  private generateRouteFromLocation(location: LatLng, updateBaseline: boolean) {
    void this.generateRouteFromLocationAsync(location, updateBaseline);
  }

  /** Generates a route for the current start using async/await */
  private async generateRouteFromLocationAsync(location: LatLng, updateBaseline: boolean, shouldFit: boolean = true): Promise<void> {
    try {
      const distance = this.selectedDay?.routeDto?.distance ?? this.recommendedActivity?.route?.distance;
      const seed = this.selectedDay?.routeDto?.seed;

      if (!distance) {
        console.warn('No distance available for route generation');
        return;
      }

      const e = await firstValueFrom(
        this.routeService.getGeneratedRoute(
          location.lat,
          location.lng,
          distance,
          seed
        )
      );

      this.recommendedActivity!.route!.distance = e.distance;
      this.recommendedActivity!.route!.elevation = e.elevation;

      const newLatLngs = convertPolylineToCoordinateList(e.polyline).map(p => latLng(p[0], p[1]));

      // update geo positions
      this.routeLineGeoPosition = e.coordinates3d.map(([lat, lng, alt]) => ({
        latitude: lat,
        longitude: lng,
        altitude: alt,
      }));

      // update existing polyline instead of replacing it
      if (this.routeLine) {
        this.routeLine.setLatLngs(newLatLngs);
      } else {
        this.routeLine = polyline(newLatLngs);
      }

      this.latlngs = this.routeLine.getLatLngs() as LatLng[];
      this.routeBounds = this.routeLine.getBounds();

      if (updateBaseline) {
        this.originalLatlngs = [...this.latlngs];
        this.originalRouteLineGeoPosition = this.routeLineGeoPosition;
        this.originalRouteBounds = this.routeBounds;
        this.originalDistance = e.distance;
        this.originalElevation = e.elevation;
        if (!this.originalStart) this.originalStart = location;
      }

      this.showRoute = true;

      // arrows depend on latlngs => rebuild
      this.routeArrows = null;

      this.rebuildLayers();

      if (shouldFit) {
        this.refitPreviewMap();
      }


    } catch (err: any) {
      if (await this.handleRouteError(err)) {
        return;
      }

      console.error('Failed to generate route', err);
      await this.showToast('Failed to generate route. Please try another location.', 3500, 'danger');
      this.resetRouteToOriginal();
    }
  }

  private async handleRouteError(err: any) {
    if (err?.error?.code === this.ROUTE_NOT_FOUND_CODE) {
      await this.showToast('No route could be generated here. Please choose a different starting point.', 4500, 'warning');
      return true;
    }
    return false;
  }

  // =====================================================
  // Stops / reshape / insert
  // =====================================================

  /** Opens the fullscreen map modal for route editing */
  async openMapModal(opts?: { mode?: 'SET_START' | 'EDIT_STOPS' }) {
    const modal = await this.modalCtrl.create({
      component: MapModalComponent,
      componentProps: {
        initialMode: opts?.mode ?? 'EDIT_STOPS',
        layers: this.cloneLayersForModal(),
        routeBounds: this.routeBounds,
        committedStops: this.committedStops,

        // true baseline for "Reset"
        originalLayers: this.cloneOriginalLayersForModal(),
        originalBounds: this.originalRouteBounds,

        // existing confirm
        onConfirm: (points: LatLng[], mode: 'KEEP_SHAPE' | 'KEEP_LENGTH') =>
          this.handleAdditionalPoints(points, mode),

        // modal triggers a global reset
        onReset: async () => {
          this.resetRouteToOriginal();
          return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
        },

        onChangeStart: (start: LatLng) => this.changeStartLocation(start),
      },
      cssClass: 'fullscreen-map-modal',
      animated: false
    });

    await modal.present();
  }

  /**
   * Inserts or reshapes the route with additional stops
   * and updates the preview on success
   */
  async handleAdditionalPoints(points: LatLng[], mode: 'KEEP_SHAPE' | 'KEEP_LENGTH'): Promise<RouteUpdate> {
    if (!this.routeLine || points.length === 0) {
      return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
    }

    const prevStops = [...this.committedStops];
    const candidateStops = this.addUniqueStops(prevStops, points);

    const request: AddStopsRequest = {
      originalRoute: this.routeLineGeoPosition,
      newPoints: candidateStops.map(p => this.toGeoJsonPosition(p)),
    };

    try {
      // wait for backend response
      const e = await firstValueFrom(
        mode === 'KEEP_SHAPE'
          ? this.stopsService.insertStops(request)
          : this.stopsService.reshape(request)
      );

      this.committedStops = candidateStops;

      // rebuild routeLine from returned polyline
      this.routeLine = polyline(
        convertPolylineToCoordinateList(e.polyline).map(p => latLng(p[0], p[1]))
      );

      this.latlngs = this.routeLine.getLatLngs() as LatLng[];

      this.routeBounds = this.routeLine.getBounds();

      // update the UI stats
      if (this.recommendedActivity?.route) {
        this.recommendedActivity.route.distance = e.distance;
        this.recommendedActivity.route.elevation = e.elevation; // @TODO add elevation
      }

      // rebuild preview layers (new array reference)
      this.rebuildLayers();

      // force leaflet preview map redraw
      requestAnimationFrame(() => {
        const map = this.mapComponent?.map;
        if (!map || !this.routeBounds) return;

        map.invalidateSize();
        map.fitBounds(this.routeBounds, { padding: [30, 30], animate: true });
      });

      return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
    } catch (err) {
        this.committedStops = prevStops;
        this.rebuildLayers();

        throw err;
      }
  }

  /** Adds incoming stops only if they are not already near existing ones */
  private addUniqueStops(existing: LatLng[], incoming: LatLng[], epsMeters = 5): LatLng[] {
     const isNear = (a: LatLng, b: LatLng) => a.distanceTo(b) <= epsMeters;

     const out = [...existing];
     for (const p of incoming) {
       if (!out.some(x => isNear(x, p))) out.push(p);
     }
     return out;
  }

  /** Converts a Leaflet LatLng into backend GeoJSON format */
  private toGeoJsonPosition(p: LatLng): GeoJsonPosition {
    return {
      latitude: p.lat,
      longitude: p.lng,
      altitude: null
    };
  }

  // =====================================================
  // Map rendering (layers / decorators)
  // =====================================================

  /** Rebuilds all visible map layers (route, start, committed stops) */
private rebuildLayers() {
  const layers: Layer[] = [];

  if (this.userLocationMarker) layers.push(this.userLocationMarker);

  if (this.routeLine) {
    layers.push(this.routeLine);

    // (re)create arrows for the current route geometry
    this.routeArrows = this.buildDirectionArrows(this.routeLine);
    layers.push(this.routeArrows);
  } else {
    this.routeArrows = null;
  }

  for (const p of this.committedStops) {
    layers.push(marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.confirmed) }));
  }

  this.layers = layers; // new array ref => ngx-leaflet updates properly
}


  /** Adds directional arrows along the route polyline */
  private buildDirectionArrows(route: Polyline): Layer {
    const decorator = (L as any).polylineDecorator(route, {
      patterns: [
        {
          repeat: 75,
          offset: 0,
          symbol: (L as any).Symbol.arrowHead({
            pixelSize: 10,
            polygon: false,
            pathOptions: { weight: 3, opacity: 0.9 }
          })
        }
      ]
    });

    return decorator as Layer;
  }

  // =====================================================
  // Modals
  // =====================================================

  /** Clones current layers for display inside the modal */
  private cloneLayersForModal(): Layer[] {
    const cloned: Layer[] = [];

    if (this.userLocationMarker) {
      cloned.push(
        marker(this.userLocationMarker.getLatLng(), {
          icon: coloredMarker(MAP_MARKER_COLORS.start)
        })
      );
    }

    if (this.routeLine) {
      const routeClone = polyline(
        this.routeLine.getLatLngs() as LatLng[],
        (this.routeLine as any).options
      );

      cloned.push(routeClone);
      cloned.push(this.buildDirectionArrows(routeClone));
    }

    // show committed stops inside modal
    for (const p of this.committedStops) {
      cloned.push(marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.added) }));
    }

    return cloned;
  }

  /** Clones the original baseline layers for modal reset */
  private cloneOriginalLayersForModal(): Layer[] {
    const cloned: Layer[] = [];

    if (this.userLocationMarker) {
      cloned.push(
        marker(this.userLocationMarker.getLatLng(), {
          icon: coloredMarker(MAP_MARKER_COLORS.start)
        })
      );
    }

    if (this.originalLatlngs && this.originalLatlngs.length > 0) {
      cloned.push(
        polyline(this.originalLatlngs, (this.routeLine as any)?.options)
      );
    } else if (this.routeLine) {
      // fallback if baseline not set yet
      cloned.push(
        polyline(this.routeLine.getLatLngs() as LatLng[], (this.routeLine as any).options)
      );
    }

    return cloned;
  }

  // =====================================================
  // Reset / baseline
  // =====================================================

  /** Resets the route, stops, and start marker to the original baseline */
  async resetRouteToOriginal() {
    if (!this.originalLatlngs || this.originalLatlngs.length === 0) return;

    if (this.originalStart) {
      if (!this.userLocationMarker) {
        this.userLocationMarker = marker(this.originalStart, {
          icon: coloredMarker(MAP_MARKER_COLORS.start)
        });
      } else {
        this.userLocationMarker.setLatLng(this.originalStart);
      }
    }

    this.committedStops = [];

    this.routeLine = polyline(this.originalLatlngs);
    this.routeLineGeoPosition = this.originalRouteLineGeoPosition;
    this.latlngs = this.routeLine.getLatLngs() as LatLng[];
    this.routeBounds = this.routeLine.getBounds();

    if (this.recommendedActivity?.route) {
      if (this.originalDistance != null) {
        this.recommendedActivity.route.distance = this.originalDistance;
        }
      if (this.originalElevation != null) {
        this.recommendedActivity.route.elevation = this.originalElevation;
        }
    }

    this.rebuildLayers();
    this.refitPreviewMap();

    // refresh training plan based on reset start
    if (this.originalStart) {
      await this.refreshTrainingPlanFor(this.originalStart);
    }
  }

  /** Changes the start location from the map modal */
  private async changeStartLocation(newStart: LatLng): Promise<{ layers: Layer[]; bounds: LatLngBounds | null }> {
    // refresh plan + route, but do NOT overwrite original baseline
    await this.applyStartLocation(newStart, false);
    return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
  }

  /** Refreshes the training plan for a specific location */
  private async refreshTrainingPlanFor(location: LatLng) {
    try {
      const plan = await firstValueFrom(this.service.getTrainingPlan(location.lat, location.lng));
      this.recommendedActivity = plan;
    } catch (e) {
      console.error('Failed to refresh training plan', e);
      await this.showToast('Failed to refresh training plan.', 3000, 'danger');
    }
  }


  // =====================================================
  // Weather
  // =====================================================

  getWeatherScoreColor(score: number): string {
    if (score >= 0.7) return "success";
    if (score >= 0.4) return "warning";
    return "danger";
  }

  getPrecipitationIcon(value: number): string {
    if (value === 0) {
      return "cloud-outline"; // no rain
    }

    return "rainy-outline"; // rain
  }

  /** Opens a modal explaining the weather conditions */
  async openWeatherExplanation() {
    const summary = this.recommendedActivity!.weather.weatherSummary;
    const modal = await this.modalCtrl.create({
      component: WeatherInfoComponent,
      componentProps: {
        temperatureText: summary.temperatureText,
        windText: summary.windText,
        precipitationText: summary.precipitationText,
        weatherScore: this.recommendedActivity!.weather.weatherScore
      }
    });

    await modal.present();
  }

  // =====================================================
  // Export (GPX)
  // =====================================================

  exportGpx() {
    if (!this.latlngs || this.latlngs.length === 0) {
      console.warn('No route to export (latlngs is null or empty).');
      return;
    }

    const xmlString = this.generateGpxXml(this.latlngs);

    const blob = new Blob([xmlString], {type: 'application/gpx+xml'});
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `route_${new Date().toISOString().slice(0, 10)}.gpx`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
  }

  generateGpxXml(latlngs: LatLng[]): string {
    const now = new Date().toISOString();

    const trackPoints = latlngs
      .map(latlng => {
        const lat = (latlng as any).lat;
        const lon = (latlng as any).lng;
        return `      <trkpt lat="${lat}" lon="${lon}">\n        <time>${now}</time>\n      </trkpt>`;
      })
      .join('\n');

    const gpx = [
      '<?xml version="1.0" encoding="UTF-8"?>',
      '<gpx version="1.1" creator="SmartRoute" xmlns="http://www.topografix.com/GPX/1/1">',
      '  <metadata>',
      `    <time>${now}</time>`,
      '  </metadata>',
      '  <trk>',
      '    <name>Exported Route</name>',
      '    <type>running</type>',
      '    <trkseg>',
      trackPoints,
      '    </trkseg>',
      '  </trk>',
      '</gpx>'
    ].join('\n');

    return gpx;
  }

  // =====================================================
  // Save Route
  // =====================================================

  saveRoute() {
    if (this.isRouteSaved) {
      return;
    }

    if (!this.latlngs || this.latlngs.length === 0) {
      console.warn('No route to export (latlngs is null or empty).');
      return;
    }
    if (!this.recommendedActivity?.route) {
      console.warn('No recommended activity route data.');
      return;
    }
    // Convert Leaflet LatLng objects to [lat, lng] for polyline encoding
    const encodedRoute = encodePolyline(this.latlngs);

    const today = new Date();
    const formattedDate = today.toLocaleDateString("en-US", {day: "2-digit", month: "short", year: "numeric"}); // "09 Jan 2026"
    const name = `${this.recommendedActivity.name}, ${formattedDate}`;

    const dto: SaveRouteDto = {
      name: name,
      distance: this.recommendedActivity.route.distance,
      pace: this.recommendedActivity.route.pace,
      elevation: this.recommendedActivity.route.elevation,
      route: encodedRoute
    };
    this.routeService.saveRoute(dto).subscribe({
      next: () => {
        console.log('Route saved successfully');
        this.isRouteSaved = true;
      },
      error: err => {
        console.error('Failed to save route', err);
      }
    });
  }


  // =====================================================
  // UI helpers (toast/alerts/colors/formatters)
  // =====================================================

  interpretReadinessScore(readinessScore: number): string {
    if (readinessScore >= 85) {
      return `${readinessScore} - Excellent, peak readiness`;
    }
    if (readinessScore >= 70) {
      return `${readinessScore} - Good, you can train hard`;
    }
    if (readinessScore >= 40) {
      return `${readinessScore} - Moderate, normal training OK`;
    }
    if (readinessScore >= 15) {
      return `${readinessScore} - Low, consider easier training`;
    }
    return `${readinessScore} - Very low, recovery recommended`;
  }

  getReadinessScoreColor(readinessScore: number): string {
    if (readinessScore >= 85) {
      return "success";
    }
    if (readinessScore >= 70) {
      return "success";
    }
    if (readinessScore >= 40) {
      return "warning";
    }
    if (readinessScore >= 15) {
      return "danger";
    }
    return "danger";
  }

  interpretTSB(tsb: number): string {
    const tsbFixed: string = tsb.toFixed(1);
    if (tsb >= 15) {
      return `${tsbFixed} - Very Fresh, optimal for competition`;
    }
    if (tsb >= 5) {
      return `${tsbFixed} - Fresh, good training readiness`;
    }
    if (tsb >= -5) {
      return `${tsbFixed} - Neutral, normal training`;
    }
    if (tsb >= -15) {
      return `${tsbFixed} - Fatigued, reduce intensity`;
    }
    if (tsb >= -30) {
      return `${tsbFixed} - Very fatigued - high risk of overtraining`;
    }
    return `${tsbFixed} - Extremely fatigued - rest required`;
  }

  getTsbIcon(tsb: number): string {
    if (tsb >= 15) {
      return "battery-charging-outline";
    }
    if (tsb >= 5) {
      return "battery-full-outline";
    }
    if (tsb >= -5) {
      return "battery-half-outline";
    }
    if (tsb >= -15) {
      return "battery-dead-outline";
    }
    if (tsb >= -30) {
      return "battery-dead-outline";
    }
    return "battery-dead-outline";
  }

  getTsbColor(tsb: number): string {
    if (tsb >= 15) {
      return "success";
    }
    if (tsb >= 5) {
      return "success";
    }
    if (tsb >= -5) {
      return "warning";
    }
    if (tsb >= -15) {
      return "warning";
    }
    if (tsb >= -30) {
      return "danger";
    }
    return "danger";
  }

  /** Displays a toast message to the user */
  private async showToast(
    message: string,
    duration = 3500,
    color: 'primary' | 'success' | 'warning' | 'danger' = 'warning'
  ) {
    const toast = await this.toastCtrl.create({
      message,
      duration,
      position: 'bottom',
      color,
    });
    await toast.present();
  }

  // =====================================================
  // Week display
  // =====================================================

  weekdayLabel(dayIso: string): string {
    const d = new Date(dayIso + "T00:00:00");
    return d.toLocaleDateString(undefined, { weekday: "short" }); // Mon
  }

  dayOfMonth(dayIso: string): string {
    const d = new Date(dayIso + "T00:00:00");
    return d.toLocaleDateString(undefined, { day: "2-digit" }); // 09
  }

  workoutIcon(wt: WorkoutType): string {
    switch (wt) {
      case "EASY_RUN":
      case "TEMPO_RUN":
      case "INTERVAL_RUN":
      case "LONG_RUN":
        return "walk-outline";        // or "navigate-outline"
      case "GYM_PREHAB":
        return "barbell-outline";
      case "MOBILITY":
        return "accessibility-outline";
      case "REST_DAY":
        return "bed-outline";
      default:
        return "help-outline";
    }
  }

  workoutLabel(wt: WorkoutType): string {
    // optional: nicer labels
    switch (wt) {
      case "EASY_RUN": return "Easy Run";
      case "TEMPO_RUN": return "Tempo";
      case "INTERVAL_RUN": return "Intervals";
      case "LONG_RUN": return "Long Run";
      case "GYM_PREHAB": return "Gym";
      case "MOBILITY": return "Mobility";
      case "REST_DAY": return "Rest";
      default: return wt;
    }
  }

  confidenceColor(conf: string): "success" | "warning" | "danger" {
    if (conf === "high") return "success";
    if (conf === "medium") return "warning";
    return "danger";
  }

  isSelected(day: PlannedDayDto): boolean {
    return this.selectedDay?.date === day.date;
  }

  formatSelectedDate(dayIso: string): string {
    return new Date(dayIso + 'T00:00:00').toLocaleDateString(undefined, {
      weekday: 'long',
      day: '2-digit',
      month: 'short',
      year: 'numeric',
    });
  }

  selectDay(day: PlannedDayDto) {
    this.saveTodayRouteSnapshot();
    this.selectedDay = day;

    if (!this.planId) {
      return;
    }

    this.isLoadingDay = true;
    this.error = null;

    this.service.getPlannedDay(this.planId, day.date).subscribe({
      next: async (activity) => {
        this.recommendedActivity = activity;
        this.isLoadingDay = false;
        const isToday = day.date === this.todayIso();

        if (activity.type === SessionType.RUN) {
          if (isToday && this.restoreTodayRouteSnapshot()) {
            this.refitPreviewMap();
            return;
          }

          // not today OR no snapshot yet -> preview route only
          this.committedStops = []; // ensure no edits leak into preview
          const start = this.userLocationMarker?.getLatLng() ?? this.originalStart ?? this.pendingInitialLocation;
          if (start) {
            await this.generateRouteFromLocationAsync(start, false, true); // fit=true so route stays in frame
          }
        } else {
          // gym/rest
          this.latlngs = null;
          this.routeLine = null;
          this.routeBounds = null;
          this.committedStops = [];
          this.rebuildLayers();
        }
      },
      error: (err: any) => {
        console.error('Failed to load planned day detail', err);
        this.isLoadingDay = false;
        this.error = 'Failed to load selected day.';
      }
    });
  }

  private todayIso(): string {
    return new Date().toISOString().slice(0, 10); // YYYY-MM-DD
  }

  canEditSelectedDay(): boolean {
    return this.selectedDay?.date === this.todayIso();
  }

  private todayRouteSnapshot: {
    committedStops: LatLng[];
    routeLineGeoPosition: GeoJsonPosition[];
    routeLatLngs: LatLng[] | null;
    bounds: LatLngBounds | null;
  } | null = null;

  private saveTodayRouteSnapshot(): void {
    if (!this.selectedDay || this.selectedDay.date !== this.todayIso()) return;

    const routeLatLngs = this.routeLine ? (this.routeLine.getLatLngs() as LatLng[]) : null;

    this.todayRouteSnapshot = {
      committedStops: [...this.committedStops],
      routeLineGeoPosition: [...this.routeLineGeoPosition],
      routeLatLngs: routeLatLngs ? [...routeLatLngs] : null,
      bounds: this.routeBounds,
    };
  }

  private restoreTodayRouteSnapshot(): boolean {
    if (!this.todayRouteSnapshot) return false;

    this.committedStops = [...this.todayRouteSnapshot.committedStops];
    this.routeLineGeoPosition = [...this.todayRouteSnapshot.routeLineGeoPosition];
    this.routeBounds = this.todayRouteSnapshot.bounds;

    if (this.todayRouteSnapshot.routeLatLngs?.length) {
      if (this.routeLine) {
        this.routeLine.setLatLngs(this.todayRouteSnapshot.routeLatLngs);
      } else {
        this.routeLine = polyline(this.todayRouteSnapshot.routeLatLngs);
      }
      this.latlngs = this.routeLine.getLatLngs() as LatLng[];
    }

    this.rebuildLayers();
    return true;
  }

  // dummy for now
  showConfidence(): boolean {
    return true;
  }

  protected readonly SessionType = SessionType;
  protected readonly formatDistance = formatDistance;
  protected readonly formatPace = formatPace;
  protected readonly formatElevation = formatElevation;
  protected readonly formatTemperature = formatTemperature;
  protected readonly formatWindDirection = formatWindDirection;
  protected readonly formatWindSpeed = formatWindSpeed;
  protected readonly formatPrecipitation = formatPrecipitation;
  protected readonly getBodyPartLabel = getBodyPartLabel;
  protected readonly getSeverityColor = getSeverityColor;
  protected readonly formatInjuryIndex = formatInjuryIndex;
}


