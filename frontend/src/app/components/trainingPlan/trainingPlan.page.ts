import {IonicModule} from '@ionic/angular';
import {Component, inject, OnInit} from '@angular/core';
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
import {RecommendedActivityDto, SessionType} from "../../dtos/recommended-activity";
import {Router} from "@angular/router";
import {BodyPart, getBodyPartLabel, getSeverityColor} from "../../dtos/injuries";
import {TrainingPlanService} from "../../../services/training-plan.service";
import { ModalController } from '@ionic/angular';
import { WeatherInfoComponent } from '../weather/weather.component';

@Component({
  selector: 'app-trainingplan',
  templateUrl: 'trainingPlan.page.html',
  styleUrls: ['trainingPlan.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class TrainingPlanPage implements OnInit {

  private readonly router: Router = inject(Router);
  private readonly service: TrainingPlanService = inject(TrainingPlanService);

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
        {name: "Exercise 1", exerciseId: "1", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: []},
        {name: "Exercise 2", exerciseId: "2", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: []},
        {name: "Exercise 3", exerciseId: "3", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: []},
        {name: "Exercise 4", exerciseId: "4", bodyParts: ["core"], equipments: [], gifUrl: "", instructions: [], secondaryMuscles: [], targetMuscles: []},
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

  getTsbColor(tsb: number):string {
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
    if (value > 1 && value < 10) {
      return "rainy-outline"; // rain
    }
    return "rainy-heavy"; // heavy rain
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
