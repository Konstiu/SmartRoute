import {Component, OnInit} from '@angular/core';
import {PushNotificationService} from "../../services/push-notification.service";
import {Globals} from "../../global/globals";

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent implements OnInit{
  constructor(private pushService: PushNotificationService,
              private global: Globals) {}

  ngOnInit() {
    // Listen for incoming notifications
    this.pushService.listenToNotifications();
  }

  requestNotificationPermission() {
    this.pushService.subscribeToNotifications();
  }
}
