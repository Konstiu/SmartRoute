import { Component, OnInit } from '@angular/core';
import { Platform } from '@ionic/angular';
import { AuthService } from 'src/services/auth.service';
import { KeyManagementService } from 'src/services/key-management.service';

@Component({
  selector: 'app-root',
  templateUrl: 'app.component.html',
  styleUrls: ['app.component.scss'],
  standalone: false,
})
export class AppComponent {

  constructor(
    private platform: Platform,
    private keyManagementService: KeyManagementService,
    private authService: AuthService 
  ) {
    this.initializeApp();
  }


  initializeApp() {
    this.platform.ready().then(async () => {
      // if the user is logged in, ensure that the signed pre-key is up to date
      // and that there are enough one-time pre-keys available
      if (this.authService.isLoggedIn()) {
        const updated = await this.keyManagementService.updateSignedPreKeyIfNecessary();
        if (updated) {
          await this.keyManagementService.uploadPublicSignedPreKey();
        };
        await this.keyManagementService.generateStoreAndUploadOneTimePreKeysIfNecessary();
      }
    });
  }

}
