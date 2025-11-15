import {Component, OnInit, AfterViewInit, ViewChild, ElementRef} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {IonicModule} from '@ionic/angular';
import {CommonModule} from '@angular/common';
import {StravaViewService} from '../../../../services/strava.view.service';
import {StravaActivity} from '../../../dtos/StravaActivity';
import * as L from 'leaflet';

@Component({
  selector: 'app-activity-detail',
  templateUrl: './activity-details.page.html',
  styleUrls: ['./activity-details.page.scss'],
  standalone: true,
  imports: [IonicModule, CommonModule]
})
export class ActivityDetailPage implements OnInit, AfterViewInit {
  @ViewChild('map', {static: false}) mapElement!: ElementRef;

  activity: StravaActivity | null = null;
  isLoading = true;
  error: string | null = null;
  map: L.Map | null = null;

  // Test encoded routes - replace with actual data from your activity
  encodedRoutes = [
    //"{zkrIm`inANPD?BDXGPKLATHNRBRFtAR~AFjAHl@D|ALtATj@HHJBL?`@EZ?NQ\\Y^MZURGJKR]RMXYh@QdAWf@[~@aAFGb@?j@YJKBU@m@FKZ[NSPKTCRJD?`@Wf@Wb@g@HCp@Qh@]z@SRMRE^EHJZnDHbBGPHb@NfBTxBN|DVbCBdA^lBFl@Lz@HbBDl@Lr@Bb@ApCAp@Ez@g@bEMl@g@`B_AvAq@l@QF]Rs@Nq@CmAVKCK?_@Nw@h@UJIHOZa@xA]~@UfASn@U`@_@~@[d@Sn@s@rAs@dAGN?NVhAB\\Ox@@b@S|A?Tl@jBZpAt@vBJhATfGJn@b@fARp@H^Hx@ARGNSTIFWHe@AGBOTAP@^\\zBMpACjEWlEIrCKl@i@nAk@}@}@yBOWSg@kAgBUk@Mu@[mC?QLIEUAuAS_E?uCKyCA{BH{DDgF`AaEr@uAb@oA~@{AE}AKw@g@qAU[_@w@[gAYm@]qAEa@FOXg@JGJ@j@o@bAy@NW?Qe@oCCc@SaBEOIIEQGaAe@kC_@{De@cE?KD[H[P]NcAJ_@DGd@Gh@UHI@Ua@}Bg@yBa@uDSo@i@UIICQUkCi@sCKe@]aAa@oBG{@G[CMOIKMQe@IIM@KB]Tg@Nw@^QL]NMPMn@@\\Lb@P~@XT",
    "u}krIq_inA_@y@My@Yu@OqAUsA]mAQc@CS@o@FSHSp@e@n@Wl@]ZCFEBK?OC_@Qw@?m@CSK[]]EMBeAA_@m@qEAg@UoCAaAMs@IkBMoACq@SwAGOYa@IYIyA_@kEMkC]{DEaAScC@yEHkGA_ALsCBiA@mCD{CCuAZcANOH@HDZl@Z`@RFh@\\TDT@ZVJBPMVGLM\\Mz@c@NCPMXERO|@a@^Ut@s@p@KJAJ Bd@EHEXi@f@a@\\g@b@[HUD_B@uADg@DQLCLD~@l@`@J^TF?JANQ\\UbAyABEZIFG`@o@RAJEl@_@ZENDDIA[Ki@BURQZaARODKVs@LSdAiAz@G`BU^A^GT@PRp@zARXRn@`BlDHt@ZlAFh@^`BX|@HHHEf@i@FAHHp@bBd@v@DRAVMl@i@v@SROXm@tBILOTOLs@NON_@t@KX]h@Un@k@\\c@h@Ud@]ZGNKp@Sj@KJo@b@W`@UPOX]XWd@UF]b@WPOAIBSf@QVi@j@_@V[b@Uj@YtAEFCCELARBn@`@lBjAzD^vB^hB?LENURkAv@[Ze@Xg@Py@p@QHONMA[HGAWE_@Em@Hg@AMCG@QHq@Cm@M[Jy@?UJIA{@Ae@KI@GFKNIX[QGAcAT[JK?OVMFK@IAIUKAYJI?QKUCGFIZCXDtAHl@@p@LjBCZS^ERAn@Fj@Br@Hn@HzAHh@RfD?j@TnCTlANjANb@\\z@TtARr@P`AFnAGfBG`@CFE?"
  ];

  constructor(
    private route: ActivatedRoute,
    private stravaService: StravaViewService,
  ) {
  }

  ngOnInit() {
    const activityId = this.route.snapshot.paramMap.get('id');
    if (activityId) {
      this.loadActivity(Number(activityId));
    }
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

  loadActivity(id: number) {
    this.isLoading = true;
    this.stravaService.getActivityById(id).subscribe({
      next: (data) => {
        this.activity = data;
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error fetching activity:', err);
        this.error = 'Failed to load activity details.';
        this.isLoading = false;
      }
    });
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
        attribution: '© <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> | Data from <a href="https://www.strava.com">Strava</a>'
      }).addTo(this.map);

      // Decode and add polylines
      this.addEncodedRoutes();
    } catch (error) {
      console.error('Error initializing map:', error);
    }
  }

  addEncodedRoutes() {
    if (!this.map) return;

    const allCoordinates: L.LatLng[] = [];

    for (const encoded of this.encodedRoutes) {
      const coordinates = this.decodePolyline(encoded);

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
    }

    // Fit map to show all routes
    if (allCoordinates.length > 0) {
      const bounds = L.latLngBounds(allCoordinates);
      this.map.fitBounds(bounds, {padding: [50, 50]});
    }
  }

  // Polyline decoding algorithm (Google's encoded polyline format)
  decodePolyline(encoded: string): L.LatLng[] {
    const points: L.LatLng[] = [];
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

      points.push(L.latLng(lat / 1e5, lng / 1e5));
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
}
