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
import { ModalController } from '@ionic/angular';
import { WeatherInfoComponent } from '../weather/weather.component';
import { MapModalComponent } from '../map/mapModal.component'
import { Polyline, Marker, LatLngBounds } from "leaflet";
import { MAP_MARKER_COLORS, coloredMarker } from '../map/map-icon';
import { StopsService } from 'src/services/add-stops.service';
import { firstValueFrom } from 'rxjs';
import { GeoJsonPosition, AddStopsRequest } from '../../dtos/add-stops';
type RouteUpdate = { layers: Layer[]; bounds: LatLngBounds | null };

@Component({
  selector: 'app-trainingplan',
  templateUrl: 'trainingPlan.page.html',
  styleUrls: ['trainingPlan.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, MapComponent]
})
export class TrainingPlanPage implements OnInit {

  private readonly router: Router = inject(Router);
  private readonly service: TrainingPlanService = inject(TrainingPlanService);
  private routeLine: Polyline | null = null;
  private userLocationMarker: Marker | null = null;
  private routeBounds: LatLngBounds | null = null;
  private stopsService = inject(StopsService);
  private committedStops: LatLng[] = [];

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

  async onGeolocationError(_error: GeolocationPositionError) {

    let alert = await this.alertController.create({
      header: "Unable to determine location.",
      message: "Please add a marker to the map to select the starting point of the route.",
      buttons: ["Okay"]
    });

    await alert.present();
  }

  @ViewChild(MapComponent) mapComponent!: MapComponent;


  onLocationSelected(location: LatLng) {
    if (this.userLocationMarker) return;

    this.userLocationMarker = marker(location, {
      icon: coloredMarker(MAP_MARKER_COLORS.start)});

    this.generateRouteFromLocation(location);
  }

  private generateRouteFromLocation(location: LatLng) {
    this.committedStops = [];

    this.routeService
      .getGeneratedRoute(
        location.lat,
        location.lng,
        this.recommendedActivity!.route!.distance
      )
      .subscribe(e => {

        this.recommendedActivity!.route!.distance = e.distance;
        this.recommendedActivity!.route!.elevation = e.elevation;

        this.routeLine = polyline(
          convertPolylineToCoordinateList(e.polyline)
            .map(p => latLng(p[0], p[1]))
        );

        this.routeBounds = this.routeLine.getBounds();
        this.rebuildLayers();

        this.mapComponent?.map?.fitBounds(this.routeBounds, {
          padding: [30, 30]
        });
      });
  }

  private rebuildLayers() {
    const layers: Layer[] = [];

    if (this.userLocationMarker) {
      layers.push(this.userLocationMarker);
    }

    if (this.routeLine) {
      layers.push(this.routeLine);
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
      layers: this.cloneLayersForModal(),
      routeBounds: this.routeBounds,
      committedStops: this.committedStops,

      // modal calls this, waits for backend, receives updated layers + bounds
      onConfirm: (points: LatLng[], mode: 'KEEP_SHAPE' | 'KEEP_LENGTH') => this.handleAdditionalPoints(points, mode),
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
       marker(
         this.userLocationMarker.getLatLng(), {
           icon: coloredMarker(MAP_MARKER_COLORS.start)}
       )
     );
   }

   if (this.routeLine) {
     cloned.push(
       polyline(
         this.routeLine.getLatLngs() as LatLng[],
         (this.routeLine as any).options
       )
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
    originalRoute: this.routeLineToGeoJson(this.routeLine),
    newPoints: (mode === 'KEEP_LENGTH' ? this.committedStops : points).map(p => this.toGeoJsonPosition(p)),
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

  this.routeBounds = this.routeLine.getBounds();

  // update the UI stats
  if (this.recommendedActivity?.route) {
    this.recommendedActivity.route.distance = e.distance;
    //this.recommendedActivity.route.elevation = e.elevation; // @TODO add elevation
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
