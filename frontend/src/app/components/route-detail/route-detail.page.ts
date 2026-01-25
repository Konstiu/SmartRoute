import {AfterViewInit, Component, ElementRef, inject, OnInit, ViewChild} from "@angular/core";
import {AlertController, IonicModule, ModalController, ToastController} from "@ionic/angular";
import {CommonModule} from "@angular/common";
import {ActivatedRoute, Router} from "@angular/router";
import {RouteService} from "../../../services/route.service";
import {ViewRouteDto} from "../../dtos/recommended-activity";
import * as L from "leaflet";
import {formatDistance, formatPace} from "../../util/formatters";
import {decodePolyline} from "../../util/polyline-encode-decode";
import {trash} from "ionicons/icons";
import {FriendshipDetailDto} from "../../dtos/friendship";
import {ChatMessageService} from "../../../services/chat-message.service";
import {ShareRouteFriendsPageModule} from "../share-route-friends/share-route-friends.module";
import {ShareRouteFriendsPage} from "../share-route-friends/share-route-friends.page";


@Component({
  selector: 'app-route-detail',
  templateUrl: './route-detail.page.html',
  styleUrls: ['./route-detail.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule],
})
export class RouteDetailPage implements OnInit, AfterViewInit {
  @ViewChild('map', {static: false}) mapElement!: ElementRef;

  savedRoute: ViewRouteDto | null = null;
  isLoading = true;
  error: string | null = null;
  map: L.Map | null = null;
  protected readonly formatDistance = formatDistance;
  protected readonly formatPace = formatPace;
  protected readonly trash = trash;
  private alertCtrl = inject(AlertController);
  private router = inject(Router);
  private toastCtrl = inject(ToastController);

  constructor(private route: ActivatedRoute,
              private routeService: RouteService,
              private modalCtrl: ModalController,
              private chatMessageService: ChatMessageService,
              private modalController: ModalController
  ) {
  }

  ngOnInit() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) {
      this.error = 'Invalid route ID.';
      this.isLoading = false;
      return;
    }

    this.routeService.getRoute(id).subscribe({
      next: (data) => {
        this.savedRoute = data;
        this.isLoading = false;
        this.tryInitMap();

      },
      error: (err) => {
        console.error(err);
        this.error = 'Failed to load route details.';
        this.isLoading = false;
      },
    });
  }

  ngAfterViewInit() {
    // Wait a bit for the view to be ready and check if element exists
    setTimeout(() => {
      if (this.mapElement && this.mapElement.nativeElement) {
        this.initMap();
      } else {
        console.warn('Map element not ready, retrying...');
        setTimeout(() => this.initMap(), 500);
      }
    }, 100);
  }

  initMap() {
    if (!this.mapElement?.nativeElement) {
      console.error('Map element not found');
      return;
    }

    this.map = L.map(this.mapElement.nativeElement, {attributionControl: true});

    // Add tile layer
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 18}).addTo(this.map);

    if (this.savedRoute?.route) {
      this.addEncodedRoutes(this.savedRoute.route);
      console.log("Success");
    } else {
      console.warn('No route data to display.');
    }
  }

  addEncodedRoutes(encodedRoute: string) {
    if (!this.map) return;

    const latLngs = decodePolyline(encodedRoute);

    if (latLngs.length === 0) return;

    // Add polyline
    L.polyline(latLngs, {
      color: '#FC4C02',
      weight: 3,
      opacity: 0.8,
      lineJoin: 'round'
    }).addTo(this.map);

    // Fit map to bounds
    this.map.fitBounds(L.latLngBounds(latLngs), {padding: [50, 50]});
  }

  deleteRoute() {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!id) return;

    this.routeService.delete(id).subscribe({
      next: () => {
        this.showToast()
        this.router.navigate(['/tabs/route']);
      },
      error: (err) => {
        console.error(err);
        this.error = 'Failed to delete route.';
      }
    });
  }

  async showCancelRequestDialog() {
    const alert = await this.alertCtrl.create({
      header: 'Remove Route from Favorites',
      message: `Are you sure you want to delete Route ${this.savedRoute?.name}? \n
                You might not be able to retrieve it later`,
      buttons: [
        {
          text: 'Cancel',
          role: 'cancel'
        },
        {
          text: 'Delete',
          role: 'destructive',
          handler: () => {
            this.deleteRoute();
          }
        }
      ]
    });

    await alert.present();
  }

  private tryInitMap() {
    if (!this.mapElement?.nativeElement) return;
    if (!this.savedRoute?.route) return;
    if (this.map) return; // prevent double init

    this.initMap();
  }

  private async showToast() {
    const toast = await this.toastCtrl.create({
      message: "Route deleted successfully",
      duration: 3000,
      color: 'success',
      position: 'top'
    });
    await toast.present();
  }

  protected async shareRoute() {
    const modal = await this.modalController.create({
      component: ShareRouteFriendsPage,
      componentProps: {
        routeName: this.savedRoute?.name,
        routeId: this.savedRoute?.id
      },
      initialBreakpoint: 1, // Takes up 75% of screen
      breakpoints: [0, 0.75, 1],
      cssClass: 'share-modal'
    });
    await modal.present();
  }
}
