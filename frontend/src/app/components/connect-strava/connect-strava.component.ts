import {Component, inject, Input, OnInit, Output, EventEmitter} from '@angular/core';
import {StravaService} from "../../../services/strava.service";
import {StravaAccountConnectionStateDto} from "../../dtos/strava-account-connection-state";
import {IonicModule} from '@ionic/angular';
import { CommonModule } from '@angular/common';


@Component({
  selector: 'app-connect-strava',
  templateUrl: './connect-strava.component.html',
  standalone: true,
  styleUrls: ['./connect-strava.component.scss'],
  imports: [IonicModule, CommonModule]
})
export class ConnectStravaComponent implements OnInit{
  @Output() connectionChanged = new EventEmitter<boolean>();

  alertButtons = [
    {
      text: 'Cancel',
      role: 'cancel',
      handler: () => {
        console.log('Alert canceled');
      },
    },
    {
      text: 'OK',
      role: 'confirm',
      handler: () => {
        console.log('Alert confirmed');
        this.disconnectStravaAccount();
      },
    },
  ];
  protected connectionState: StravaAccountConnectionStateDto | undefined;

  requiredScopes = [
    'read',
    'activity:read_all',
    'profile:read_all'
  ];

  parsedScopes: string[] = [];
  missingScopes: string[] = [];

  @Input()
  public origin: "register" | "tabs/account" | "sync-activities" = "sync-activities";
  private stravaService: StravaService = inject(StravaService);

  ngOnInit(): void {
    this.stravaService.getConnectionState().subscribe({
      next: result => {
        this.connectionState = result;
        console.log(result);

        this.parsedScopes = this.connectionState.scopes?.split(',') || [];

        this.missingScopes = this.requiredScopes.filter(
          required => !this.parsedScopes.includes(required)
        );
        this.connectionChanged.emit(result?.connected || false);
      },
      error: error => {
        console.error("Failed to load Strava connection state: " + error);
        this.connectionChanged.emit(false);
      }
    })
  }

  connectStravaAccount(): void {
    this.stravaService.connectStravaAccount(this.origin);
  }

  disconnectStravaAccount(): void {
    this.stravaService.disconnectStravaAccount().subscribe({
      next: res => {
        this.connectionState = res;
        this.connectionChanged.emit(res?.connected || false);
      },
      error: err => {
        console.error(err);
      }
    });
  }
}
