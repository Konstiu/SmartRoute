import {AlertController, IonicModule} from '@ionic/angular';
import {Component, EventEmitter, inject, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {
  formatDistance,
  formatElevation, formatInjuryIndex,
  formatPace,
  formatPrecipitation,
  formatTemperature,
  formatWindDirection,
  formatWindSpeed,
} from "../../util/formatters";
import {RecommendedActivityDto, SessionType, SaveRouteDto} from "../../dtos/recommended-activity";
import {Router} from "@angular/router";
import {BodyPart, getBodyPartLabel, getSeverityColor} from "../../dtos/injuries";
import {TrainingPlanService} from "../../../services/training-plan.service";
import {MapComponent} from '../map/map.component';
import {Icon, icon, latLng, LatLng, Layer, marker, polyline} from 'leaflet';
import {RouteService} from 'src/services/route.service';
import {convertPolylineToCoordinateList} from 'src/services/utils';
import {ModalController} from '@ionic/angular';
import {WeatherInfoComponent} from '../weather/weather.component';
import {encodePolyline} from "../../util/polyline-encode-decode";

@Component({
  selector: 'app-trainingplan',
  templateUrl: 'trainingPlan.page.html',
  styleUrls: ['trainingPlan.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, MapComponent]
})
export class TrainingPlanPage implements OnInit {
  isRouteSaved = false;
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
  error: string | null = null;
  isLoading: boolean = true;
  latlngs: LatLng[] | null = null;
  layers: Layer[] = [];
  routeService = inject(RouteService);
  alertController = inject(AlertController);
  markerOptions = {
    icon: icon({
      ...Icon.Default.prototype.options,
      iconUrl: 'assets/marker-icon.png',
      iconRetinaUrl: 'assets/marker-icon-2x.png',
      shadowUrl: 'assets/marker-shadow.png'
    })
  };
  hasLocation = false;
  @ViewChild(MapComponent) mapComponent!: MapComponent;
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
  private readonly router: Router = inject(Router);
  private readonly service: TrainingPlanService = inject(TrainingPlanService);
  private modalCtrl = inject(ModalController);

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

  async onGeolocationError(_error: GeolocationPositionError) {

    let alert = await this.alertController.create({
      header: "Unable to determine location.",
      message: "Please add a marker to the map to select the starting point of the route.",
      buttons: ["Okay"]
    });

    await alert.present();
  }

  handleNewLocation(location: LatLng) {
    if (this.recommendedActivity?.route?.distance) {
      this.layers.push(marker(location, this.markerOptions));
      this.routeService.getGeneratedRoute(location.lat, location.lng, this.recommendedActivity?.route?.distance).subscribe({
        next: (e) => {
          if (this.recommendedActivity?.route?.distance) {
            this.recommendedActivity.route.distance = e.distance;
          }
          if (this.recommendedActivity?.route?.elevation) {
            this.recommendedActivity.route.elevation = e.elevation;
          }
          let coords = polyline(convertPolylineToCoordinateList(e.polyline).map(x => latLng(x[0], x[1])));
          this.latlngs = coords.getLatLngs() as LatLng[];
          this.layers.push(coords)
          if (this.mapComponent['map']) {
            const bounds = coords.getBounds();
            this.mapComponent['map'].fitBounds(bounds, {padding: [50, 50]});
          }
        }
      });
    }
  }

  onNewLocationRegisterd(location: LatLng) {
    if (this.hasLocation) return;
    this.handleNewLocation(location);
    this.hasLocation = true;
  }

  geoLocation(location: LatLng) {
    this.handleNewLocation(location);
    this.hasLocation = true;
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
}


