import {IonicModule} from '@ionic/angular';
import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {
  formatDistance,
  formatWindDirection,
  formatElevation,
  formatPace,
  formatWindSpeed,
  formatPrecipitation,
  formatTemperature,
} from "../../util/formatters";
import {RecommendedActivityDto, SessionType} from "../../dtos/recommended-activity";

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
    title: "Easy Run",
    type: SessionType.RUN,
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
      tsb: -4,
      readinessScore: 65,
      injuryIndex: 1.0,
      injuries: [
        {title: "Leg injury"}
      ]
    }
  };

  interpretReadinessScore(readinessScore: number): string {
    if (readinessScore >= 90) {
      return `${readinessScore} - Excellent - peak readiness`;
    }
    if (readinessScore >= 75) {
      return `${readinessScore} - Good - you can train hard`;
    }
    if (readinessScore >= 60) {
      return `${readinessScore} - Moderate - normal training OK`;
    }
    if (readinessScore >= 40) {
      return `${readinessScore} - Low - consider easier training`;
    }
    if (readinessScore >= 20) {
      return `${readinessScore} - Very low - recovery recommended`;
    }
    return `${readinessScore} - Extremely low - rest!`;
  }

  interpretTSB(tsb: number): string {
    if (tsb >= 15) {
      return `${tsb} - Very Fresh - optimal for competition`;
    }
    if (tsb >= 5) {
      return `${tsb} - Fresh - good training readiness`;
    }
    if (tsb >= -5) {
      return `${tsb} - Neutral - normal training`;
    }
    if (tsb >= -15) {
      return `${tsb} - Fatigued - reduce intensity`;
    }
    if (tsb >= -30) {
      return `${tsb} - Very fatigued - high risk of overtraining`;
    }
    return `${tsb} - Extremely fatigued - rest required`;
  }

  tsbIcon(tsb: number): string {
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

  protected readonly SessionType = SessionType;
  protected readonly formatDistance = formatDistance;
  protected readonly formatPace = formatPace;
  protected readonly formatElevation = formatElevation;
  protected readonly formatTemperature = formatTemperature;
  protected readonly formatWindDirection = formatWindDirection;
  protected readonly formatWindSpeed = formatWindSpeed;
  protected readonly formatPrecipitation = formatPrecipitation;
}
