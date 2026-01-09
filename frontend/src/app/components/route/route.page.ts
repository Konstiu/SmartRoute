import {Component, inject} from '@angular/core';
import {Router} from "@angular/router";
import {GymWorkoutDto} from "../../dtos/gymworkout";
import {ViewRouteDto} from "../../dtos/recommended-activity";
import {RouteService} from "../../../services/route.service";
import {formatDistance, formatElevation} from "../../util/formatters";

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
}
