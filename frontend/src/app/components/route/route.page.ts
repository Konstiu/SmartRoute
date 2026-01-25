import {Component, inject} from '@angular/core';
import {Router} from "@angular/router";
import {GymWorkoutDto} from "../../dtos/gymworkout";
import {ViewRouteDto} from "../../dtos/recommended-activity";
import {RouteService} from "../../../services/route.service";
import {formatDistance, formatDuration, formatElevation, formatPace} from "../../util/formatters";

@Component({
  selector: 'app-route',
  templateUrl: 'route.page.html',
  styleUrls: ['route.page.scss'],
  standalone: false,
})
export class RoutePage {
  savedRoutes: ViewRouteDto[] = [];
  isLoading = false;
  error: string | null = null;
  protected readonly formatDistance = formatDistance;
  protected readonly formatElevation = formatElevation;
  protected readonly formatDuration = formatDuration;
  protected readonly formatPace = formatPace;
  private router = inject(Router);
  private routeService = inject(RouteService);

  constructor() {
  }

  ngOnInit() {
    this.loadRoutes();
  }

  loadRoutes(event?: any) {
    this.clearCache();
    this.isLoading = true;
    this.error = null;

    this.routeService.getRoutes().subscribe({
      next: (data) => {
        this.savedRoutes = data
        this.isLoading = false;
        if (event) event.target.complete();
      },
      error: (err) => {
        console.error(err);
        this.error = 'Failed to load saved routes.';
        this.isLoading = false;
        if (event) event.target.complete();
      },
    });
  }

  doRefresh(event: any) {
    this.loadRoutes(event);
  }

  openRoute(savedRoute: ViewRouteDto) {
    this.router.navigate(['/tabs/route', savedRoute.id]);
  }

  clearCache() {
    this.savedRoutes = [];
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
}
