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
import { RecommendedActivityDto, SessionType } from "../../dtos/recommended-activity";
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

  error: string | null = null;
  isLoading: boolean = true;
  latlngs: LatLng[] | null = null;
  layers: Layer[] = [];
  routeService = inject(RouteService);
  alertController = inject(AlertController);
  date: string = new Date().toLocaleDateString();

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
        { name: "Exercise 1", exerciseId: "1", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: [] },
        { name: "Exercise 2", exerciseId: "2", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: [] },
        { name: "Exercise 3", exerciseId: "3", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: [] },
        { name: "Exercise 4", exerciseId: "4", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: [] },
      ],
      sets: 4,
      reps: 40,
    }
  };

  // =====================================================
  // Lifecycle
  // =====================================================

  ngOnInit(): void {
    this.loadTrainingPlan();
  }

  ionViewDidEnter() {
    // page is now visible -> Leaflet can compute sizes correctly
    this.forceMapResize();
  }

  // =====================================================
  // Training plan loading
  // =====================================================

  loadTrainingPlan(location?: LatLng): void {
    this.isLoading = true;
    this.error = null;

    const lat = location?.lat ?? 48.21;
    const lng = location?.lng ?? 16.36;

    this.service.getTrainingPlan(lat, lng).subscribe({
      next: res => {
        this.recommendedActivity = res;
        console.log(this.recommendedActivity);
        this.error = null;
        this.isLoading = false;

        if (this.pendingInitialLocation && !this.initialRouteGenerated) {
          this.initialRouteGenerated = true;
          const loc = this.pendingInitialLocation;
          this.pendingInitialLocation = null;

          this.generateRouteFromLocation(loc, true);
        }

      },
      error: err => {
        console.error(err);
        this.isLoading = false;
        this.error = "Failed to load Training Plan.";
      }
    });
  }

  navigateToGymExercise(exerciseId: number) {
    this.router.navigate(['tabs/gym/' + exerciseId]);
  }

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
      const plan = await firstValueFrom(this.service.getTrainingPlan(location.lat, location.lng));
      this.recommendedActivity = plan;

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

  private forceMapResize() {
    requestAnimationFrame(() => this.mapComponent?.map?.invalidateSize(true));
    setTimeout(() => this.mapComponent?.map?.invalidateSize(true), 150);
    setTimeout(() => this.mapComponent?.map?.invalidateSize(true), 400);
  }

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

  async onExactLocationFailed() {
    const alert = await this.alertController.create({
      header: 'Precise location unavailable',
      message: 'Trying to determine your location with reduced accuracy.',
      buttons: ['OK'],
    });

    await alert.present();
  }

  async onGeolocationError() {
    const alert = await this.alertController.create({
      header: 'Unable to determine location',
      message: 'Please add a marker to the map to select the starting point.',
      buttons: ['OK'],
    });

    await alert.present();
  }


  async onLocationSelected(location: LatLng) {
    this.originalStart = location;
    await this.applyStartLocation(location, true);
  }

  // =====================================================
  // Route generation (ORS)
  // =====================================================

  private generateRouteFromLocation(location: LatLng, updateBaseline: boolean) {
    void this.generateRouteFromLocationAsync(location, updateBaseline);
  }

  private async generateRouteFromLocationAsync(location: LatLng, updateBaseline: boolean): Promise<void> {
    try {
      const e = await firstValueFrom(
        this.routeService.getGeneratedRoute(
          location.lat,
          location.lng,
          this.recommendedActivity!.route!.distance
        )
      );

      this.recommendedActivity!.route!.distance = e.distance;
      this.recommendedActivity!.route!.elevation = e.elevation;

      this.routeLineGeoPosition = e.coordinates3d.map(([lat, lng, alt]) => ({
        latitude: lat,
        longitude: lng,
        altitude: alt,
      }));

      this.routeLine = polyline(convertPolylineToCoordinateList(e.polyline).map(p => latLng(p[0], p[1])));

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

      this.rebuildLayers();
      this.refitPreviewMap();

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

  async openMapModal() {
    const modal = await this.modalCtrl.create({
      component: MapModalComponent,
      componentProps: {
        // current (edited) view
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

  private addUniqueStops(existing: LatLng[], incoming: LatLng[], epsMeters = 5): LatLng[] {
     const isNear = (a: LatLng, b: LatLng) => a.distanceTo(b) <= epsMeters;

     const out = [...existing];
     for (const p of incoming) {
       if (!out.some(x => isNear(x, p))) out.push(p);
     }
     return out;
  }

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

  private rebuildLayers() {
    const layers: Layer[] = [];

    if (this.userLocationMarker) {
       layers.push(this.userLocationMarker);
       }

    if (this.routeLine) {
      layers.push(this.routeLine);
      layers.push(this.buildDirectionArrows(this.routeLine));
    }

    for (const p of this.committedStops) {
      layers.push(marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.confirmed) }));
    }

    this.layers = layers;
  }

  private buildDirectionArrows(route: Polyline): Layer {
    const latlngs = route.getLatLngs() as LatLng[];

    const decorator = (L as any).polylineDecorator(latlngs, {
      patterns: [
        {
          repeat: 75,
          offset: 0,
          symbol: (L as any).Symbol.arrowHead({
            pixelSize: 10,
            polygon: false,
            pathOptions: {
              weight: 3,
              opacity: 0.9
            }
          })
        }
      ]
    });

    return decorator as Layer;
  }

  // =====================================================
  // Modals
  // =====================================================

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

  private async changeStartLocation(newStart: LatLng): Promise<{ layers: Layer[]; bounds: LatLngBounds | null }> {
    // refresh plan + route, but do NOT overwrite original baseline
    await this.applyStartLocation(newStart, false);
    return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
  }

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

  private modalCtrl = inject(ModalController);

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

    const blob = new Blob([xmlString], { type: 'application/gpx+xml' });
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


