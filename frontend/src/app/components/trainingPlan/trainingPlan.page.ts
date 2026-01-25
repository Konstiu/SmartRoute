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
  private readonly trainingPlanInjuryChangedFlagKey: string = 'trainingPlanInjuryChanged';
  private readonly liveSuggestionToggleKey: string = 'trainingPlanUseLiveSuggestionToday';

  useLiveSuggestionForToday: boolean = localStorage.getItem(this.liveSuggestionToggleKey) === 'true';


  private showingRegenPrompt: boolean = false;
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

  private dayLoadSeq = 0;
  private weekLoadSeq = 0;

  private currentWeekSeed: number | null = null;

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

  ionViewWillEnter(): void
  {
    void this.maybePromptRegeneratePlan();
  }

  // =====================================================
  // Training plan loading
  // =====================================================

  loadWeekPlan(location?: LatLng, regen: boolean = false): void
  {
    const seq = ++this.weekLoadSeq;

    this.isLoadingWeek = true;
    this.error = null;

    // Always prefer the currently active start
    const requestStart = location ?? this.resolvePlanLocation();

    this.lastPlanLocation = latLng(requestStart.lat, requestStart.lng);

    // Do NOT move the marker here. Only ensure it exists.
    if (!this.userLocationMarker)
    {
      this.userLocationMarker = marker(requestStart, { icon: coloredMarker(MAP_MARKER_COLORS.start) });
      this.rebuildLayers();
    }

    // Fresh seed only when regenerating
    const seed = regen ? this.generateFreshSeed() : (this.currentWeekSeed ?? undefined);

    this.plan7dService.getNext7Days(requestStart.lat, requestStart.lng, {
      regen: regen,
      seed: seed
    }).subscribe({
      next: (plan) => {
        if (seq !== this.weekLoadSeq)
        {
          return;
        }

        this.weekPlan = plan;
        this.planId = plan.planId ?? null;

        const todayIso = this.todayIso();
        const initial = plan.days.find(d => d.date === todayIso) ?? plan.days[0];

        // On regen: do NOT restore today snapshot; we want the new plan to drive route
        if (regen)
        {
          this.todayRouteSnapshot = null;
        }

        // Avoid selectDay() because it saves a snapshot for "today" right before switching
        this.selectedDay = initial;
        this.reloadSelectedDay(false);

        this.isLoadingWeek = false;
      },
      error: (err) => {
        if (seq !== this.weekLoadSeq)
        {
          return;
        }

        console.error(err);
        this.isLoadingWeek = false;
        this.error = regen ? "Failed to regenerate 7-day plan." : "Failed to load 7-day plan.";
      }
    });
  }

private generateFreshSeed(): number
{
  // Prefer crypto for better uniqueness
  const cryptoObj = window.crypto as Crypto | undefined;

  if (cryptoObj && cryptoObj.getRandomValues)
  {
    const arr = new Uint32Array(1);
    cryptoObj.getRandomValues(arr);

    // Keep it in signed int range if your backend expects int
    const seed = Number(arr[0] % 2147483647);

    this.currentWeekSeed = seed;
    return seed;
  }

  // Fallback: still fine in practice
  const seed = Math.floor(Math.random() * 2147483647);

  this.currentWeekSeed = seed;
  return seed;
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
  private async applyStartLocation(location: LatLng, updateBaseline: boolean)
  {
    this.committedStops = [];

    if (this.applyingStart)
    {
      return;
    }

    this.applyingStart = true;

    try
    {
      // ensure marker exists
      if (!this.userLocationMarker)
      {
        this.userLocationMarker = marker(location, { icon: coloredMarker(MAP_MARKER_COLORS.start) });
      }
      else
      {
        this.userLocationMarker.setLatLng(location);
      }

      // remember last used location for "today live weather" requests
      this.lastPlanLocation = latLng(location.lat, location.lng);

      // TODAY ONLY: when start changes, refresh today's display (either live or merged)
      if (this.selectedDay && this.selectedDay.date === this.todayIso() && this.planId) {
        try {
          if (this.useLiveSuggestionForToday) {
            // toggle ON: pure live suggestion (type may differ from weekly plan)
            const liveActivity = await firstValueFrom(
              this.service.getTrainingPlan(location.lat, location.lng)
            );
            this.recommendedActivity = liveActivity;
          } else {
            // toggle OFF: keep planned workout type, but patch live weather/status
            const plannedActivity = await firstValueFrom(
              this.service.getPlannedDay(this.planId, this.selectedDay.date)
            );

            const liveActivity = await firstValueFrom(
              this.service.getTrainingPlan(location.lat, location.lng)
            );

            this.recommendedActivity = this.mergeLiveIntoPlannedActivity(plannedActivity, liveActivity);
          }

          // optional: also patch week strip for today's score
          if (this.weekPlan?.days) {
            const idx = this.weekPlan.days.findIndex(d => d.date === this.todayIso());
            if (idx >= 0) {
              this.weekPlan.days[idx] = {
                ...this.weekPlan.days[idx],
                // depending on your DTO naming:
                weatherDto: this.recommendedActivity?.weather, // if week uses weatherDto
                // weather: this.recommendedActivity?.weather,  // if week uses weather
              } as any;
            }
          }
        } catch (e) {
          console.warn('Failed to refresh today after start change', e);
        }
      }


      // generate route using current selected day / activity distance
      await this.generateRouteFromLocationAsync(location, updateBaseline);
    }
    catch (err: any)
    {
      console.error('applyStartLocation failed', err);
      await this.showToast('Could not refresh plan/route for this location.', 3500, 'danger');
    }
    finally
    {
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
    const isToday = this.selectedDay?.date === this.todayIso();

    const distance = isToday
      ? this.recommendedActivity?.route?.distance
      : this.selectedDay?.routeDto?.distance ?? this.recommendedActivity?.route?.distance;

    const seed = isToday
      ? undefined
      : this.selectedDay?.routeDto?.seed;

    if (!distance)
    {
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


    } catch (err: any)
    {
      if (await this.handleRouteError(err))
      {
        return;
      }

      console.error('Failed to generate route', err);
      await this.showToast('Failed to generate route. Please try another location.', 3500, 'danger');

      // IMPORTANT: Do NOT reset to originalStart here.
      // Keep current start marker and keep the previous route (if any).
      // If there is no previous route, clear route UI safely.

      if (!this.routeLine)
      {
        this.latlngs = null;
        this.routeBounds = null;
        this.routeLineGeoPosition = [];
        this.showRoute = false;
        this.routeArrows = null;
        this.rebuildLayers();
      }
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

  selectDay(day: PlannedDayDto): void
  {
    this.saveTodayRouteSnapshot();
    this.selectedDay = day;

    this.reloadSelectedDay(true);
  }

private reloadSelectedDay(allowSnapshotRestore: boolean): void {
  if (!this.selectedDay || !this.planId) return;

  const day = this.selectedDay;
  const isToday = day.date === this.todayIso();

  this.isLoadingDay = true;
  this.error = null;

  const seq = ++this.dayLoadSeq; // 👈 bump + capture
  const stillLatest = () =>
    seq === this.dayLoadSeq &&
    this.selectedDay?.date === day.date; // also ensure same day still selected

  if (!isToday) {
    this.service.getPlannedDay(this.planId, day.date).subscribe({
      next: async (activity) => {
        if (!stillLatest()) return; // 👈 ignore stale
        this.recommendedActivity = activity;
        this.isLoadingDay = false;
        await this.onActivityLoadedForSelectedDay(activity, false, allowSnapshotRestore);
      },
      error: (err) => {
        if (!stillLatest()) return;
        console.error('Failed to load planned day detail', err);
        this.isLoadingDay = false;
        this.error = 'Failed to load selected day.';
      }
    });
    return;
  }

  const loc = this.resolvePlanLocation();

  if (this.useLiveSuggestionForToday) {
    this.service.getTrainingPlan(loc.lat, loc.lng).subscribe({
      next: async (liveActivity) => {
        if (!stillLatest()) return;
        this.recommendedActivity = liveActivity;
        this.isLoadingDay = false;
        await this.onActivityLoadedForSelectedDay(liveActivity, true, false);
      },
      error: (err) => {
        if (!stillLatest()) return;
        console.error('Failed to load live training plan', err);
        this.isLoadingDay = false;
        this.error = 'Failed to load live suggestion for today.';
      }
    });
    return;
  }

  // planned + live merge
  this.service.getPlannedDay(this.planId, day.date).subscribe({
    next: async (plannedActivity) => {
      if (!stillLatest()) return;

      try {
        const liveActivity = await firstValueFrom(
          this.service.getTrainingPlan(loc.lat, loc.lng)
        );

        if (!stillLatest()) return;

        const merged = this.mergeLiveIntoPlannedActivity(plannedActivity, liveActivity);
        this.recommendedActivity = merged;
        this.isLoadingDay = false;

        await this.onActivityLoadedForSelectedDay(merged, true, allowSnapshotRestore);
      } catch (e) {
        if (!stillLatest()) return;
        console.warn('Live fetch failed, showing planned day only', e);
        this.recommendedActivity = plannedActivity;
        this.isLoadingDay = false;

        await this.onActivityLoadedForSelectedDay(plannedActivity, true, allowSnapshotRestore);
      }
    },
    error: (err) => {
      if (!stillLatest()) return;
      console.error('Failed to load planned day detail', err);
      this.isLoadingDay = false;
      this.error = 'Failed to load selected day.';
    }
  });
}

  onLiveSuggestionToggle(ev: CustomEvent) {
    const enabled = !!ev.detail.checked;
    this.useLiveSuggestionForToday = enabled;
    localStorage.setItem(this.liveSuggestionToggleKey, String(enabled));
    this.todayRouteSnapshot = null;

    const startLocation =
      this.userLocationMarker?.getLatLng()
      ?? this.originalStart
      ?? this.pendingInitialLocation
      ?? null;

    if (enabled) {
      // loads :live planId
      this.regenerateWeekPlanLive(startLocation);
    } else {
      // loads base planId again (restore entire week)
      this.loadWeekPlan(startLocation ?? undefined);
    }
  }


  private regenerateWeekPlanLive(startLocation: LatLng | null) {
    const seq = ++this.weekLoadSeq;

    this.isLoadingWeek = true;
    this.error = null;

    const lat = startLocation?.lat ?? 48.21;
    const lng = startLocation?.lng ?? 16.36;

    this.plan7dService.getNext7Days(lat, lng, { /* ... regen:true ... */ }).subscribe({
      next: (plan) => {
        if (seq !== this.weekLoadSeq) return;
        this.weekPlan = plan;
        this.planId = plan.planId ?? null;

        const todayIso = this.todayIso();
        const todayDay = plan.days.find(d => d.date === todayIso) ?? plan.days[0];

        this.selectDay(todayDay);
        this.isLoadingWeek = false;
      },
      error: (err) => {
        if (seq !== this.weekLoadSeq) return;
        console.error(err);
        this.isLoadingWeek = false;
        this.error = "Failed to regenerate 7-day plan.";
      }
    });
  }


  protected todayIso(): string {
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
    return false;
  }

  private async maybePromptRegeneratePlan(): Promise<void>
  {
    if (this.showingRegenPrompt) {
      return;
    }

    const hasChanges = localStorage.getItem(this.trainingPlanInjuryChangedFlagKey) === 'true';
    if (!hasChanges) {
      return;
    }

    this.showingRegenPrompt = true;

    try {
      const alert = await this.alertController.create({
        header: 'Regenerate training plan?',
        message: 'Your injuries changed. Do you want to regenerate the 7-day plan?',
        buttons: [
          {
            text: 'Not now',
            role: 'cancel',
            handler: () => {
              // Keep the flag so we can ask again next time (optional behavior)
              // If you want to only ask once, remove the flag here instead.
            }
          },
          {
            text: 'Regenerate',
            handler: () => {
              localStorage.removeItem(this.trainingPlanInjuryChangedFlagKey);
              const startLocation =
                this.userLocationMarker?.getLatLng()
                ?? this.originalStart
                ?? this.pendingInitialLocation
                ?? undefined;

              this.todayRouteSnapshot = null;
              this.loadWeekPlan(startLocation);
            }
          }
        ]
      });

      await alert.present();
    } finally {
      this.showingRegenPrompt = false;
    }
  }

  private lastPlanLocation: LatLng | null = null;

  private resolvePlanLocation(): LatLng
  {
    if (this.userLocationMarker)
    {
      return this.userLocationMarker.getLatLng();
    }

    if (this.originalStart)
    {
      return this.originalStart;
    }

    if (this.pendingInitialLocation)
    {
      return this.pendingInitialLocation;
    }

    if (this.lastPlanLocation)
    {
      return this.lastPlanLocation;
    }

    return latLng(48.21, 16.36);
  }

  private mergeLiveIntoPlannedActivity(
    planned: RecommendedActivityDto,
    live: RecommendedActivityDto
  ): RecommendedActivityDto
  {
    return {
      ...planned,
      weather: live.weather ?? planned.weather,
      athleteStatus: live.athleteStatus ?? planned.athleteStatus
    };
  }

  private async onActivityLoadedForSelectedDay(
    activity: RecommendedActivityDto,
    isToday: boolean,
    allowSnapshotRestore: boolean
  ): Promise<void>
  {
    if (activity.type === SessionType.RUN)
    {
      if (isToday && allowSnapshotRestore && this.restoreTodayRouteSnapshot())
      {
        this.refitPreviewMap();
        return;
      }

      this.committedStops = [];

      const start =
        this.userLocationMarker?.getLatLng()
        ?? this.originalStart
        ?? this.pendingInitialLocation;

      if (start)
      {
        await this.generateRouteFromLocationAsync(start, false, true);
      }
    }
    else
    {
      this.latlngs = null;
      this.routeLine = null;
      this.routeBounds = null;
      this.committedStops = [];
      this.rebuildLayers();
    }
  }

  async onRegenerateWeekClicked(): Promise<void>
  {
    if (this.isLoadingWeek)
    {
      return;
    }

    const startLocation = this.resolvePlanLocation();

    this.committedStops = [];
    this.todayRouteSnapshot = null;

    this.loadWeekPlan(startLocation, true);
  }

  isToday(dateValue: string | Date): boolean {
    const todayDate = new Date();

    const compareDate = new Date(dateValue);

    return (
      todayDate.getFullYear() === compareDate.getFullYear()
      && todayDate.getMonth() === compareDate.getMonth()
      && todayDate.getDate() === compareDate.getDate()
    );
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


