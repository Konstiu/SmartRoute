import {IonicModule} from '@ionic/angular';
import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {
  formatDistance,
  formatElevation,
  formatPace,
  formatPrecipitation,
  formatTemperature,
  formatWindDirection,
  formatWindSpeed,
} from "../../util/formatters";
import {RecommendedActivityDto, SessionType} from "../../dtos/recommended-activity";
import {BodyPart, getBodyPartLabel, getSeverityColor} from "../../dtos/injuries";

@Component({
  selector: 'app-trainingplan',
  templateUrl: 'trainingPlan.page.html',
  styleUrls: ['trainingPlan.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class TrainingPlanPage {
  date: string = new Date().toLocaleDateString();
  recommendedActivity: RecommendedActivityDto | undefined = {
    title: "Gym Session",
    type: SessionType.GYM,
    route: {
      distance: 5421,
      pace: 2.6,
      description: "",
      elevation: 22,
    },
    weather: {
      weatherScore: .9,
      temperature: 16,
      windSpeed: 4,
      windDirection: "N",
      precipitation: 0,
      relativeHumidity: 50,
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
      id: 0,
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
    if (tsb >= 15) {
      return `${tsb} - Very Fresh, optimal for competition`;
    }
    if (tsb >= 5) {
      return `${tsb} - Fresh, good training readiness`;
    }
    if (tsb >= -5) {
      return `${tsb} - Neutral, normal training`;
    }
    if (tsb >= -15) {
      return `${tsb} - Fatigued, reduce intensity`;
    }
    if (tsb >= -30) {
      return `${tsb} - Very fatigued - high risk of overtraining`;
    }
    return `${tsb} - Extremely fatigued - rest required`;
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
      return "battery-empty-outline";
    }
    if (tsb >= -30) {
      return "battery-empty-outline";
    }
    return "battery-empty-outline";
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
}
