import {Component, inject, OnInit, OnDestroy} from '@angular/core';
import {IonicModule} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {ActivitiesService} from '../../../services/activities.service';
import {Activity} from '../../dtos/Activity';
import {Router} from "@angular/router";
import {ToastController} from '@ionic/angular';
import {formatDistance, formatDuration, formatElevation, formatHeartRate, formatPace} from "../../util/formatters";
import {ActivitySyncNotificationService} from "../../../services/ActivitySyncNotificationService";


@Component({
  selector: 'app-recent-runs',
  templateUrl: './recent-runs.page.html',
  styleUrls: ['./recent-runs.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class RecentRunsPage implements OnInit {
  activities: Activity[] = [];
  isLoading = false;
  error: string | null = null;
  private syncSubscription: any;

  constructor(private stravaService: ActivitiesService,
              private router: Router,
              private syncNotificationService: ActivitySyncNotificationService
  ) {}

  private activitiesService: ActivitiesService = inject(ActivitiesService);
  private toastCtrl: ToastController = inject(ToastController);

  ngOnInit() {
    this.loadActivities();
    this.syncSubscription = this.syncNotificationService.syncCompleted.subscribe(() => {
      this.loadActivities();
    });
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

  async refreshActivities(event: any) {
    if (!this.activitiesService.canRefresh()) {
      await this.showToast("Refresh limit reached. Try again in a few minutes.", "warning")
      event.target.complete();
      return;
    }

    this.activitiesService.incrementRefreshCount();

    this.isLoading = true;
    this.error = null;

    this.activitiesService.syncWithValidation(10).subscribe({
      next: async ({ outcome }) => {
        this.isLoading = false;
        event.target.complete();

        if (outcome.kind === 'success') {
          this.loadActivities();
          await this.showToast("Activities synchronized successfully.", "success");
          return;
        }

        if (outcome.kind === 'running') {
          await this.showToast("Sync is still running. Please check again shortly.", "warning");
          return;
        }

        if (outcome.kind === 'failed') {
          await this.showToast(
            outcome.message ? `Sync failed: ${outcome.message}` : "Sync failed. Please retry.",
            "danger"
          );
          return;
        }

        // unknown
        await this.showToast(
          "Connection interrupted. We couldn't confirm the sync. Please check again or retry if needed.",
          "warning"
        );
      },
      error: async (err) => {
        console.error('Sync failed:', err);
        this.error = 'Failed to sync activities. Please try again.';
        this.isLoading = false;
        event.target.complete();
        await this.showToast("Sync failed. Please try again.", "danger");
      }
    });
  }

  private async showToast(message: string, color: "success" | "warning" | "danger") {
    const toast = await this.toastCtrl.create({
      message,
      color,
      duration: 2500,
      position: "top"
    });
    await toast.present();
  }

  async doRefresh(event: any) {
    await this.refreshActivities(event);
  }

  formatDate(dateString: string): string {
    const cleanString = dateString.replace('Z', '');
    const date = new Date(cleanString);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    const timeString = date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false // Use 24-hour format, change to true for 12-hour format
    });

    if (diffDays === 1) return `Today at ${timeString}`;
    if (diffDays === 2) return `Yesterday at ${timeString}`;
    if (diffDays < 8) return `${diffDays - 1} days ago at ${timeString}`;

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

  openActivity(activity: Activity) {
    this.router.navigate(['/activity/', activity.id]);
  }

  importGpx() {
    this.router.navigate(['/import-gpx']);
  }

  protected readonly formatElevation = formatElevation;
  protected readonly formatHeartRate = formatHeartRate;
  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }

}
