import {Component, OnInit} from '@angular/core';
import {IonicModule} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {StravaViewService} from '../../../services/strava.view.service';
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

  constructor(private stravaService: StravaViewService, private router: Router) {}

  ngOnInit() {
    this.loadActivities();
  }

  loadActivities(event?: any) {
    this.isLoading = true;
    this.error = null;

    this.stravaService.getRecentActivities().subscribe({
      next: (data) => {
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
    const date = new Date(dateString);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    const timeString = date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false // Use 24-hour format, change to true for 12-hour format
    });

    if (diffDays === 0) return `Today at ${timeString}`;
    if (diffDays === 1) return `Yesterday at ${timeString}`;
    if (diffDays < 7) return `${diffDays} days ago at ${timeString}`;

    const dateStr = date.toLocaleDateString('en-US', {
      month: 'short',
      day: 'numeric',
      year: date.getFullYear() !== now.getFullYear() ? 'numeric' : undefined
    });

    return `${dateStr} at ${timeString}`;
  }

  formatDuration(seconds: number): string {
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    return `${h}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}`;
  }

  formatDistance(dist: number) : string{
    return dist.toFixed(2)
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

  openActivity(activity:StravaActivity){
    this.router.navigate(['/activity/', activity.id]);
  }
}
