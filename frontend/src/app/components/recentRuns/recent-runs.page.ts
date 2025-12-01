import {Component, OnInit} from '@angular/core';
import {IonicModule} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {ActivitiesService} from '../../../services/activities.service';
import {StravaActivity} from '../../dtos/StravaActivity';
import {Router} from "@angular/router";

@Component({
  selector: 'app-recent-runs',
  templateUrl: './recent-runs.page.html',
  styleUrls: ['./recent-runs.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class RecentRunsPage implements OnInit {
  activities: StravaActivity[] = [];
  isLoading = false;
  error: string | null = null;

  constructor(private stravaService: ActivitiesService, private router: Router) {
  }

  ngOnInit() {
    this.loadActivities();
  }

  loadActivities(event?: any) {
    this.isLoading = true;
    this.error = null;

    this.stravaService.getRecentActivities().subscribe({
      next: (data) => {
        this.activities = data.sort((a, b) =>
          new Date(b.startDateLocal).getTime() - new Date(a.startDateLocal).getTime()
        );
        this.activities = data;
        this.isLoading = false;
        if (event) {
          event.target.complete();
        }
      },
      error: (err) => {
        console.error('Error fetching activities:', err);
        this.error = 'Failed to load activities. Please try again.';
        this.isLoading = false;
        if (event) {
          event.target.complete();
        }
      }
    });
  }

  doRefresh(event: any) {
    this.loadActivities(event);
  }


  formatDate(dateString: string): string {
    if (!dateString) {
      return '';
    }

    const date = new Date(dateString);
    const now = new Date();

    if (isNaN(date.getTime())) {
      console.error('Invalid date string:', dateString);
      return dateString;
    }

    const diffTime = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    let rawTime: string;

    const match = dateString.match(/(\d{2}:\d{2})/);
    if (match) {
      rawTime = match[1];
    } else {
      rawTime = date.toISOString().substring(11, 16);
    }

    if (diffDays === 0) return `Today at ${rawTime}`;
    if (diffDays === 1) return `Yesterday at ${rawTime}`;
    if (diffDays < 7) return `${diffDays} days ago at ${rawTime}`;

    const dateStr = date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined
    });

    return `${dateStr} at ${rawTime}`;
  }

  formatDuration(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
  }

  formatDistance(dist: number): string {
    dist = dist / 1000; // convert meters to km
    return dist.toFixed(2)
  }

  formatPace(averageSpeed: number): string {
    if (averageSpeed <= 0) return "0:00";
    const paceInKmh = averageSpeed * 3.6; // Convert m/s to km/h
    const paceInMinutesPerKm = 60 / paceInKmh;
    let minutes = Math.floor(paceInMinutesPerKm);
    let seconds = Math.round((paceInMinutesPerKm - minutes) * 60);
    // Handle edge case where seconds round up to 60
    if (seconds === 60) {
      minutes += 1;
      seconds = 0;
    }
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  }

  getActivityIcon(sportType: string): string {
    const icons: { [key: string]: string } = {
      'Run': 'footsteps-outline',
      'Ride': 'bicycle-outline',
      'Swim': 'water-outline',
      'Walk': 'walk-outline',
      'Hike': 'trail-sign-outline',
      'default': 'fitness-outline'
    };
    return icons[sportType] || icons['default'];
  }

  openActivity(activity: StravaActivity) {
    this.router.navigate(['/activity/', activity.id]);
  }

  importGpx() {
    this.router.navigate(['/import-gpx']);
  }

}
