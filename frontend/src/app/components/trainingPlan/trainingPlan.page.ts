import { AlertController, IonicModule } from '@ionic/angular';
import { Component, EventEmitter, inject, OnInit, ViewChild } from '@angular/core';
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
import { Icon, icon, latLng, LatLng, Layer, marker, polyline } from 'leaflet';
import { RouteService } from 'src/services/route.service';
import { convertPolylineToCoordinateList } from 'src/services/utils';
import { ModalController, ViewDidEnter, ToastController} from '@ionic/angular';
import { WeatherInfoComponent } from '../weather/weather.component';
import { MapModalComponent } from '../map/mapModal.component'
import { Polyline, Marker, LatLngBounds } from "leaflet";
import { MAP_MARKER_COLORS, coloredMarker } from '../map/map-icon';
import { StopsService } from 'src/services/add-stops.service';
import { firstValueFrom } from 'rxjs';
import { GeoJsonPosition, AddStopsRequest } from '../../dtos/add-stops';
type RouteUpdate = { layers: Layer[]; bounds: LatLngBounds | null };
import L from 'leaflet';
import 'leaflet-polylinedecorator';

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
  private routeLine: Polyline | null = null;
  private routeLineGeoPosition: GeoJsonPosition[] = [];
  private userLocationMarker: Marker | null = null;
  private routeBounds: LatLngBounds | null = null;
  private stopsService = inject(StopsService);
  private committedStops: LatLng[] = [];

  private originalRouteLine: Polyline | null = null;
  private originalRouteBounds: LatLngBounds | null = null;
  private originalLatlngs: LatLng[] | null = null;
  private originalDistance: number | null = null;
  private originalElevation: number | null = null;
  private originalStart: LatLng | null = null;
  private toastCtrl = inject(ToastController);


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
  error: string | null = null;
  isLoading: boolean = true;
  latlngs: LatLng[] | null = null;

  ngOnInit(): void {
    this.loadTrainingPlan();
  }

  loadTrainingPlan(): void {
    this.isLoading = true;
    this.error = null;

    // TODO change position dynamically - current lat, long: ~Vienna
    this.service.getTrainingPlan(48.21, 16.36,).subscribe({
      next: res => {
        this.recommendedActivity = res;
        console.log(this.recommendedActivity);
        this.error = null;
        this.isLoading = false;

        // wait for Angular to render <app-map>
        setTimeout(() => this.forceMapResize(), 0);
      },
      error: err => {
        console.error(err);
        this.isLoading = false;
        this.error = "Failed to load Training Plan.";
        setTimeout(() => this.forceMapResize(), 0);
      }
    });
  }

  navigateToGymExercise(exerciseId: number) {
    this.router.navigate(['tabs/gym/' + exerciseId]);
  }

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

  layers: Layer[] = [];
  routeService = inject(RouteService);

  alertController = inject(AlertController);

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


  onLocationSelected(location: LatLng) {
    if (this.userLocationMarker) return;

    this.userLocationMarker = marker(location, {
      icon: coloredMarker(MAP_MARKER_COLORS.start)});

    this.originalStart = location;

    this.generateRouteFromLocation(location, true);
  }

private generateRouteFromLocation(location: LatLng, updateBaseline: boolean) {
  this.committedStops = [];

  this.routeService
    .getGeneratedRoute(location.lat, location.lng, this.recommendedActivity!.route!.distance)
    .subscribe({
      next: e => {
        this.recommendedActivity!.route!.distance = e.distance;
        this.recommendedActivity!.route!.elevation = e.elevation;

        this.routeLineGeoPosition = e.coordinates3d.map(([lat, lng, alt]) => ({
          latitude: lat,
          longitude: lng,
          altitude: alt,
        }));

        this.routeLine = polyline(
          convertPolylineToCoordinateList(e.polyline).map(p => latLng(p[0], p[1]))
        );

        this.latlngs = this.routeLine.getLatLngs() as LatLng[];
        this.routeBounds = this.routeLine.getBounds();

        if (updateBaseline) {
          this.originalLatlngs = [...this.latlngs];
          this.originalRouteBounds = this.routeBounds;
          this.originalDistance = e.distance;
          this.originalElevation = e.elevation;

          // ensure originalStartLatLng exists
          if (!this.originalStart) this.originalStart = location;
        }

        this.rebuildLayers();

        const map = this.mapComponent?.map;
        if (map && this.routeBounds) {
          requestAnimationFrame(() => {
            map.invalidateSize(true);
            // (optional) cap zoom a bit
            map.fitBounds(this.routeBounds!, { padding: [30, 30], animate: true, maxZoom: 16 });
          });
        }
      },

      error: async (err) => {
        if (err?.error?.code === 'ROUTE_NOT_FOUND') {
          await this.showToast(
            'No route could be generated here (e.g., water/no paths). Please choose a different starting point.',
            4500,
            'warning'
          );
          return;
        }

        console.error('Failed to generate route', err);
        await this.showToast('Failed to generate route. Please try another location.', 3500, 'danger');

        this.resetRouteToOriginal();
      }
    });
}



private async generateRouteFromLocationAsync(location: LatLng, updateBaseline: boolean): Promise<void> {
  this.committedStops = [];

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

    this.routeLine = polyline(
      convertPolylineToCoordinateList(e.polyline).map(p => latLng(p[0], p[1]))
    );

    this.latlngs = this.routeLine.getLatLngs() as LatLng[];
    this.routeBounds = this.routeLine.getBounds();

    if (updateBaseline) {
      this.originalLatlngs = [...this.latlngs];
      this.originalRouteBounds = this.routeBounds;
      this.originalDistance = e.distance;
      this.originalElevation = e.elevation;
      if (!this.originalStart) this.originalStart = location;
    }

    this.rebuildLayers();
    this.refitPreviewMap();

  } catch (err: any) {
    if (err?.error?.code === 'ROUTE_NOT_FOUND') {
      await this.showToast(
        'No route could be generated here (e.g., water/no paths). Please choose a different starting point.',
        4500,
        'warning'
      );
      return;
    }

    console.error('Failed to generate route', err);
    await this.showToast('Failed to generate route. Please try another location.', 3500, 'danger');
    this.resetRouteToOriginal();
  }
}


  private rebuildLayers() {
    const layers: Layer[] = [];

    if (this.userLocationMarker) {
       layers.push(this.userLocationMarker);
       }

    if (this.routeLine) {
      layers.push(this.routeLine);
      //layers.push(...this.buildGradientRouteLayers(this.routeLine));
      layers.push(this.buildDirectionArrows(this.routeLine));
    }

    for (const p of this.committedStops) {
      layers.push(marker(p, { icon: coloredMarker(MAP_MARKER_COLORS.confirmed) }));
    }

    this.layers = layers;
  }

  // ------- Weather -------
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

      // let modal trigger a global reset
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
      //cloned.push(...this.buildGradientRouteLayers(routeClone));
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

async handleAdditionalPoints(points: LatLng[], mode: 'KEEP_SHAPE' | 'KEEP_LENGTH'): Promise<RouteUpdate> {
  if (!this.routeLine || points.length === 0) {
    return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
  }

  this.committedStops = this.addUniqueStops(this.committedStops, points);

  const request: AddStopsRequest = {
    //originalRoute: this.routeLineToGeoJson(this.routeLine),
    originalRoute: this.routeLineGeoPosition,
    newPoints: this.committedStops.map(p => this.toGeoJsonPosition(p)),
  };

  // wait for backend response
  const e = await firstValueFrom(
    mode === 'KEEP_SHAPE'
      ? this.stopsService.insertStops(request)
      : this.stopsService.reshape(request)
  );

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
}

  private toGeoJsonPosition(p: LatLng): GeoJsonPosition {
    return {
      latitude: p.lat,
      longitude: p.lng,
      altitude: null
    };
  }

  private routeLineToGeoJson(route: Polyline): GeoJsonPosition[] {
    return (route.getLatLngs() as LatLng[]).map(p =>
      this.toGeoJsonPosition(p)
    );
  }

  private geoJsonToLatLngs(route: GeoJsonPosition[]): LatLng[] {
    return route.map(p => latLng(p.latitude, p.longitude));
  }

 private addUniqueStops(existing: LatLng[], incoming: LatLng[], epsMeters = 5): LatLng[] {
   const isNear = (a: LatLng, b: LatLng) => a.distanceTo(b) <= epsMeters;

   const out = [...existing];
   for (const p of incoming) {
     if (!out.some(x => isNear(x, p))) out.push(p);
   }
   return out;
 }

  resetRouteToOriginal() {
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
    this.latlngs = this.routeLine.getLatLngs() as LatLng[];
    this.routeBounds = this.routeLine.getBounds();

    if (this.recommendedActivity?.route) {
      if (this.originalDistance != null) this.recommendedActivity.route.distance = this.originalDistance;
      if (this.originalElevation != null) this.recommendedActivity.route.elevation = this.originalElevation;
    }

    this.rebuildLayers();
    this.refitPreviewMap();
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

private downsampleLatLngs(points: LatLng[], maxPoints = 220): LatLng[] {
  if (points.length <= maxPoints) return points;

  const step = (points.length - 1) / (maxPoints - 1);
  const out: LatLng[] = [];
  for (let i = 0; i < maxPoints; i++) {
    out.push(points[Math.round(i * step)]);
  }
  return out;
}

private hexToRgb(hex: string): { r: number; g: number; b: number } {
  const h = hex.replace('#', '').trim();
  const full = h.length === 3 ? h.split('').map(c => c + c).join('') : h;
  const n = parseInt(full, 16);
  return { r: (n >> 16) & 255, g: (n >> 8) & 255, b: n & 255 };
}

private rgbToHex(r: number, g: number, b: number): string {
  const toHex = (x: number) => Math.max(0, Math.min(255, Math.round(x))).toString(16).padStart(2, '0');
  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

private lerp(a: number, b: number, t: number): number {
  return a + (b - a) * t;
}

private lerpHexColor(startHex: string, endHex: string, t: number): string {
  const s = this.hexToRgb(startHex);
  const e = this.hexToRgb(endHex);
  return this.rgbToHex(
    this.lerp(s.r, e.r, t),
    this.lerp(s.g, e.g, t),
    this.lerp(s.b, e.b, t)
  );
}

/**
 * True color gradient: startColor -> endColor along the route.
 * Returns MANY small polylines (segments), each with its own color.
 */
private buildGradientRouteLayers(
  route: Polyline,
  startColor = MAP_MARKER_COLORS.start,
  endColor = '#003b9b',
  maxSegments = 160
): Layer[] {
  const raw = route.getLatLngs() as LatLng[];
  if (!raw || raw.length < 2) return [route];

  const pts = this.downsampleLatLngs(raw, maxSegments + 1);
  const baseOpts: any = (route as any).options ?? {};

  const weight = baseOpts.weight ?? 5;

  const layers: Layer[] = [];
  const denom = Math.max(1, pts.length - 2);

  for (let i = 0; i < pts.length - 1; i++) {
    const t = i / denom;
    const color = this.lerpHexColor(startColor, endColor, t);

    layers.push(
      polyline([pts[i], pts[i + 1]], {
        ...baseOpts,
        color,
        opacity: 1.0,
        weight
      })
    );
  }

  return layers;
}

private async changeStartLocation(newStart: LatLng): Promise<{ layers: Layer[]; bounds: LatLngBounds | null }> {
  if (!this.userLocationMarker) {
    this.userLocationMarker = marker(newStart, { icon: coloredMarker(MAP_MARKER_COLORS.start) });
  } else {
    this.userLocationMarker.setLatLng(newStart);
  }

  // regenerate route BUT do NOT overwrite original baseline
  await this.generateRouteFromLocationAsync(newStart, false);

  return { layers: this.cloneLayersForModal(), bounds: this.routeBounds };
}

viewDidEnter() {
  // page is now visible -> Leaflet can compute sizes correctly
  this.forceMapResize();
}

private forceMapResize() {
  // a few retries covers: transitions, permission prompts, fonts, etc.
  requestAnimationFrame(() => this.mapComponent?.map?.invalidateSize(true));
  setTimeout(() => this.mapComponent?.map?.invalidateSize(true), 150);
  setTimeout(() => this.mapComponent?.map?.invalidateSize(true), 400);
}


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

private refitPreviewMap() {
  const map = this.mapComponent?.map;
  const bounds = this.routeBounds;

  if (!map) return;

  requestAnimationFrame(() => {
    map.invalidateSize(true);

    if (bounds) {
      requestAnimationFrame(() => {
        map.fitBounds(bounds, { padding: [30, 30], animate: true, maxZoom: 16 });
      });
    }
  });
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


