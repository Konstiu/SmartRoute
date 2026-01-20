import {Component, OnInit, ViewChild, inject, ChangeDetectorRef} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {IonicModule, ModalController} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {ActivitiesService} from '../../../../services/activities.service';
import {Activity, DetailedActivity} from '../../../dtos/Activity';
import * as L from 'leaflet';
import {decodePolyline, encodePolyline} from "../../../util/polyline-encode-decode";
import {SaveRouteDto} from "../../../dtos/recommended-activity";
import {RouteService} from "../../../../services/route.service";
import {Layer, polyline} from 'leaflet';
import {MapComponent} from '../../../components/map/map.component';
import {RunTypeLabel} from "../../../dtos/run-classification";
import {ChangeClassificationComponent} from "../change-classification/change-classification.component";

@Component({
  selector: 'app-activity-detail',
  templateUrl: './activity-details.page.html',
  styleUrls: ['./activity-details.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule, MapComponent]
})
export class ActivityDetailPage implements OnInit {
  activity: DetailedActivity | null = null;
  isLoading = true;
  error: string | null = null;
  map: L.Map | null = null;
  isRouteSaved = false;
  private routeService = inject(RouteService);
  mapLayers: Layer[] = [];
  routePolyline: any = null;
  @ViewChild(MapComponent) mapComponent!: MapComponent;

  constructor(
    private route: ActivatedRoute,
    private stravaService: ActivitiesService,
    private modalController: ModalController,
    private cdr: ChangeDetectorRef
  ) {
  }

  ngOnInit() {
    const activityId = this.route.snapshot.paramMap.get('id');
    if (activityId) {
      this.loadActivity(Number(activityId));
    }
  }

  onMapReady(map: any) {
    // Fit bounds to route if we have one
    if (this.routePolyline) {
      setTimeout(() => {
        const bounds = this.routePolyline.getBounds();
        if (bounds.isValid()) {
          map.fitBounds(bounds, {padding: [50, 50]});
        }
      }, 200);
    }
  }

  loadActivity(id: number) {
    this.isLoading = true;


    this.stravaService.getActivityById(id).subscribe({
      next: (data) => {
        this.activity = data;
        // Create polyline layer from activity data
        if (this.activity?.summaryPolyline) {
          const coordinates = this.decodePolyline(this.activity.summaryPolyline);
          if (coordinates.length > 0) {
            this.routePolyline = polyline(coordinates, {
              color: '#FC4C02',
              weight: 3,
              opacity: 0.8,
              lineJoin: 'round'
            });
            this.mapLayers = [this.routePolyline];
            // Fit bounds after map is ready
            setTimeout(() => {
              if (this.mapComponent?.map) {
                const bounds = this.routePolyline.getBounds();
                if (bounds.isValid()) {
                  this.mapComponent.map.fitBounds(bounds, {padding: [50, 50]});
                }
              }
            }, 300);
          } else {
            console.warn('No coordinates decoded');
          }
        } else {
          console.warn('No summary polyline in activity');
        }
        this.isLoading = false;
        this.cdr.detectChanges(); // Trigger change detection
      },
      error: (err) => {
        console.error('Error fetching activity:', err);
        this.error = 'Failed to load activity details.';
        this.isLoading = false;
        this.cdr.detectChanges();
      }
    });
  }

  async editClassification(activity: Activity) {
    console.log("activity");
    const modal = await this.modalController.create({
      component: ChangeClassificationComponent,
      componentProps: {
        activityId: activity.id,
        dto: {...activity.runClassification}
      }
    });

    await modal.present();

    const {data} = await modal.onWillDismiss();
    if (data?.updatedClassification) {
      activity.runClassification = data.updatedClassification;
      this.stravaService.notifyActivityUpdate(activity.id);
    }
  }

  initMap() {
    if (!this.mapElement || !this.mapElement.nativeElement) {
      console.error('Map element not found');
      return;
    }

    try {
      // Initialize the map
      this.map = L.map(this.mapElement.nativeElement, {
        attributionControl: true
      }).setView([55.609818, 13.003286], 13);

      this.map.attributionControl.setPrefix(''); // Remove "Leaflet" prefix

      // Add tile layer
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        //attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> | Data from <a href="https://www.strava.com">Strava</a>'
      }).addTo(this.map);

      // Decode and add polylines
      if (this.activity) {
        this.addEncodedRoutes(this.activity.summaryPolyline);
      }
    } catch (error) {
      console.error('Error initializing map:', error);
    }
  }

  addEncodedRoutes(polyline: string | null) {
    if (!this.map) return;

    const allCoordinates: L.LatLng[] = [];
    if (polyline == null) {
      return;
    }

    const coordinates = decodePolyline(polyline);

    if (coordinates.length > 0) {
      allCoordinates.push(...coordinates);

      // Add polyline to map
      L.polyline(coordinates, {
        color: '#FC4C02', // Strava orange color
        weight: 3,
        opacity: 0.8,
        lineJoin: 'round'
      }).addTo(this.map);
    }


    // Fit map to show all routes
    if (allCoordinates.length > 0) {
      const bounds = L.latLngBounds(allCoordinates);
      this.map.fitBounds(bounds, {padding: [50, 50]});
    }
  }

  decodePolyline(encoded: string): [number, number][] {
    const points: [number, number][] = [];
    let index = 0;
    const len = encoded.length;
    let lat = 0;
    let lng = 0;

    while (index < len) {
      let b: number;
      let shift = 0;
      let result = 0;
      do {
        b = encoded.charCodeAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);

      const dlat = ((result & 1) !== 0 ? ~(result >> 1) : (result >> 1));
      lat += dlat;

      shift = 0;
      result = 0;

      do {
        b = encoded.charCodeAt(index++) - 63;
        result |= (b & 0x1f) << shift;
        shift += 5;
      } while (b >= 0x20);

      const dlng = ((result & 1) !== 0 ? ~(result >> 1) : (result >> 1));
      lng += dlng;
      points.push([lat / 1e5, lng / 1e5]);
    }

    return points;
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
    const paceInKmh = averageSpeed * 3.6;
    const paceInMinutesPerKm = 60 / paceInKmh;
    let minutes = Math.floor(paceInMinutesPerKm);
    let seconds = Math.round((paceInMinutesPerKm - minutes) * 60);
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

  formatDate(dateString: string): string {
    const cleanString = dateString.replace('Z', '');
    const date = new Date(cleanString);
    const now = new Date();
    const diffTime = Math.abs(now.getTime() - date.getTime());
    const diffDays = Math.ceil(diffTime / (1000 * 60 * 60 * 24));

    const timeString = date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
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

  protected readonly runTypeLabel = RunTypeLabel;

  saveRoute() {
    if (this.isRouteSaved) {
      return;
    }

    if (this.isLoading) {
      console.warn('Acitivity hasnt been loaded yet');
      return;
    }
    // Convert Leaflet LatLng objects to [lat, lng] for polyline encoding
    //const encodedRoute = encodePolyline(this.latlngs);

    const today = new Date();
    const formattedDate = today.toLocaleDateString("en-US", {day: "2-digit", month: "short", year: "numeric"}); // "09 Jan 2026"
    const name = `Activity, ${formattedDate}`;  //TODO: Rename it with Runtype Classification when branches are merged

    const dto: SaveRouteDto = {
      name: name,
      distance: this.activity?.distance ?? 0,
      pace: this.activity?.movingTime ?? 0,
      elevation: this.activity?.totalElevationGain ?? 0,
      route: this.activity?.summaryPolyline ?? ''
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
}
