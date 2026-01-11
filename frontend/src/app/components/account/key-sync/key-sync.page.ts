import { Component, OnInit, OnDestroy} from '@angular/core';
import { KeySyncService, SyncSession } from '../../../../services/key-sync-service';
import {AlertController, IonicModule, LoadingController, ToastController} from '@ionic/angular';
import { Html5Qrcode } from 'html5-qrcode';
import QRCode from 'qrcode';
import { CommonModule } from '@angular/common';
import { interval, Subscription } from 'rxjs';

type SyncMode = 'choose' | 'generate' | 'scan' | 'waiting';

@Component({
  selector: 'app-key-sync',
  templateUrl: './key-sync.page.html',
  imports: [
    IonicModule,
    CommonModule
  ],
  styleUrls: ['./key-sync.page.scss']
})
export class KeySyncPage implements OnInit, OnDestroy {
  mode: SyncMode = 'choose';
  qrCodeDataUrl: string | null = null;
  sessionData: SyncSession | null = null;
  hasKeys: boolean = false;
  cameraError: string | null = null;

  private html5QrCode: Html5Qrcode | null = null;
  private readonly QR_READER_ID = 'qr-reader';
  private pollingSubscription: Subscription | null = null;
  private scannerStarted: boolean = false;

  constructor(
    private keySyncService: KeySyncService,
    private alertController: AlertController,
    private loadingController: LoadingController,
    private toastController: ToastController
  ) {}

  async ngOnInit() {
    // Check if this device already has keys
    this.hasKeys = await this.keySyncService.hasEncryptionKeys();
  }

  ngOnDestroy() {
    this.stopScanner();
    this.stopPolling();
  }

  /**
   * DEVICE A (no keys): Generate QR code to REQUEST keys from another device
   * DEVICE B (has keys): Generate QR code to PROVIDE keys to another device
   */
  async generateQRCode() {
    const loading = await this.loadingController.create({
      message: 'Generating sync QR code...'
    });
    await loading.present();

    try {
      if (this.hasKeys) {
        // DEVICE B: Upload encrypted keys and create session
        this.sessionData = await this.keySyncService.generateSyncSession();
        await this.showToast('QR code ready - scan from device that needs keys', 'success');
      } else {
        // DEVICE A: Create empty session to request keys
        this.sessionData = await this.keySyncService.createEmptySession();
        await this.showToast('QR code ready - scan from device with keys', 'success');
      }

      // Generate QR code image
      const qrPayload = JSON.stringify(this.sessionData);
      this.qrCodeDataUrl = await QRCode.toDataURL(qrPayload, {
        width: 300,
        margin: 2,
        errorCorrectionLevel: 'H'
      });

      this.mode = 'generate';

      // Show expiration warning
      await this.showExpirationAlert();

      // If device doesn't have keys, start polling for uploaded keys
      if (!this.hasKeys) {
        this.startPollingForKeys();
      }

    } catch (error) {
      console.error('Error generating QR code:', error);

      if (error && typeof error === 'object' && 'status' in error) {
        const status = (error as any).status;
        if (status === 405) {
          await this.showToast('Server endpoint not configured (405)', 'danger');
        } else if (status === 404) {
          await this.showToast('Sync endpoint not found (404)', 'danger');
        } else {
          await this.showToast(`Server error (${status})`, 'danger');
        }
      } else {
        await this.showToast('Failed to generate QR code', 'danger');
      }
    } finally {
      await loading.dismiss();
    }
  }

  /**
   * Start polling backend to check if keys have been uploaded
   */
  private startPollingForKeys() {
    if (!this.sessionData?.sessionId) return;

    const sessionId = this.sessionData.sessionId;

    // Poll every 2 seconds for up to 5 minutes
    this.pollingSubscription = interval(2000).subscribe(async () => {
      try {
        const hasKeys = await this.keySyncService.checkSessionHasKeys(sessionId);

        if (hasKeys) {
          // Keys are available! Download them
          await this.downloadKeys(sessionId);
          this.stopPolling();
        }
      } catch (error) {
        console.error('Error polling for keys:', error);
      }
    });

    // Auto-stop after 5 minutes
    setTimeout(() => {
      this.stopPolling();
    }, 5 * 60 * 1000);
  }

  /**
   * Download and import keys from backend
   */
  private async downloadKeys(sessionId: string) {
    const loading = await this.loadingController.create({
      message: 'Downloading encryption keys...'
    });
    await loading.present();

    try {
      if (!this.sessionData) {
        throw new Error('No session data available');
      }

      await this.keySyncService.downloadAndImportKeys(this.sessionData);

      await this.showToast('Keys received and imported successfully!', 'success');
      this.hasKeys = true;

      // Wait a bit then reset
      setTimeout(() => {
        this.reset();
      }, 2000);

    } catch (error) {
      console.error('Error downloading keys:', error);
      await this.showToast('Failed to download keys', 'danger');
    } finally {
      await loading.dismiss();
    }
  }

  /**
   * Stop polling for keys
   */
  private stopPolling() {
    if (this.pollingSubscription) {
      this.pollingSubscription.unsubscribe();
      this.pollingSubscription = null;
    }
  }

  /**
   * DEVICE B: Start camera to scan QR code
   */
  async startScanning() {
    this.mode = 'scan';
    this.cameraError = null;

    // Check if we're in a secure context (HTTPS or localhost)
    if (!window.isSecureContext && window.location.hostname !== 'localhost') {
      this.cameraError = 'Camera requires HTTPS. Please use a secure connection.';
      await this.showToast('Camera requires HTTPS', 'danger');
      this.mode = 'choose';
      return;
    }

    // Request camera permissions first (especially important for iOS)
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'environment' }
      });
      // Stop the stream immediately - we just wanted to get permission
      stream.getTracks().forEach(track => track.stop());
    } catch (permissionError) {
      console.error('Camera permission denied:', permissionError);
      this.cameraError = 'Camera permission denied. Please enable camera access in your browser settings.';
      await this.showCameraPermissionAlert();
      this.mode = 'choose';
      return;
    }

    // Wait for DOM to update and element to be rendered
    setTimeout(async () => {
      await this.initializeScanner();
    }, 150); // Slightly longer delay for iOS
  }

  /**
   * Initialize the QR code scanner
   */
  private async initializeScanner() {
    try {
      // Verify element exists
      const element = document.getElementById(this.QR_READER_ID);
      if (!element) {
        throw new Error('QR reader element not found in DOM');
      }

      // Initialize QR scanner
      this.html5QrCode = new Html5Qrcode(this.QR_READER_ID);

      // Get available cameras
      const devices = await Html5Qrcode.getCameras();

      if (!devices || devices.length === 0) {
        throw new Error('No cameras found on this device');
      }

      console.log('Available cameras:', devices);

      // Try to find back camera, fallback to first available
      let cameraId = devices[0].id;
      const backCamera = devices.find(device =>
        /back|rear|environment/i.test(device.label)
      );
      if (backCamera) {
        cameraId = backCamera.id;
      }

      // Start scanner with specific camera
      await this.html5QrCode.start(
        cameraId,
        {
          fps: 10,
          qrbox: { width: 250, height: 250 },
          aspectRatio: 1.0
        },
        this.onQRCodeScanned.bind(this),
        this.onQRCodeScanError.bind(this)
      );

      this.scannerStarted = true;
      console.log('Scanner started successfully');

    } catch (error) {
      console.error('Error starting scanner:', error);

      let errorMessage = 'Failed to start camera';

      if (error instanceof Error) {
        if (error.message.includes('NotAllowedError') || error.message.includes('Permission')) {
          errorMessage = 'Camera permission denied';
          await this.showCameraPermissionAlert();
        } else if (error.message.includes('NotFoundError')) {
          errorMessage = 'No camera found';
        } else if (error.message.includes('NotReadableError')) {
          errorMessage = 'Camera is in use by another app';
        }
      }

      this.cameraError = errorMessage;
      await this.showToast(errorMessage, 'danger');
      this.mode = 'choose';
    }
  }

  /**
   * Handle successful QR code scan
   */
  private async onQRCodeScanned(decodedText: string) {
    // Prevent multiple scans
    if (!this.scannerStarted) return;

    // Stop scanner immediately
    await this.stopScanner();

    const loading = await this.loadingController.create({
      message: 'Processing QR code...'
    });
    await loading.present();

    try {
      // Parse QR code data
      const sessionData: SyncSession = JSON.parse(decodedText);

      // Validate session data
      if (!sessionData.sessionId || !sessionData.sessionKey) {
        throw new Error('Invalid QR code data');
      }

      if (this.hasKeys) {
        // This device HAS keys - upload them to the session
        await loading.dismiss();
        const uploadLoading = await this.loadingController.create({
          message: 'Uploading encryption keys...'
        });
        await uploadLoading.present();

        try {
          await this.keySyncService.uploadKeysToSession(sessionData);
          await this.showToast('Keys uploaded successfully!', 'success');
        } finally {
          await uploadLoading.dismiss();
        }

      } else {
        // This device DOESN'T have keys - download them from the session
        await loading.dismiss();
        const downloadLoading = await this.loadingController.create({
          message: 'Downloading encryption keys...'
        });
        await downloadLoading.present();

        try {
          await this.keySyncService.downloadAndImportKeys(sessionData);
          await this.showToast('Keys imported successfully!', 'success');
          this.hasKeys = true;
        } finally {
          await downloadLoading.dismiss();
        }
      }

      // Show success and go back
      setTimeout(() => {
        this.mode = 'choose';
      }, 2000);

    } catch (error) {
      console.error('Error processing QR code:', error);

      if (error instanceof Error && error.message.includes('expired')) {
        await this.showToast('Sync session has expired', 'warning');
      } else if (error instanceof Error && error.message.includes('No keys available')) {
        await this.showToast('No keys available in session yet', 'warning');
      } else {
        await this.showToast('Failed to process QR code', 'danger');
      }

      this.mode = 'choose';
    } finally {
      await loading.dismiss();
    }
  }

  /**
   * Handle QR scan errors (usually not found)
   */
  private onQRCodeScanError(errorMessage: string) {
    // Ignore "No QR code found" messages (too noisy)
    if (!errorMessage.includes('NotFoundException')) {
      console.warn('QR scan error:', errorMessage);
    }
  }

  /**
   * Stop the QR scanner
   */
  private async stopScanner() {
    if (this.html5QrCode && this.scannerStarted) {
      try {
        await this.html5QrCode.stop();
        this.html5QrCode.clear();
        console.log('Scanner stopped successfully');
      } catch (error) {
        console.error('Error stopping scanner:', error);
      }
      this.html5QrCode = null;
      this.scannerStarted = false;
    }
  }

  /**
   * Cancel scanning and go back
   */
  async cancelScanning() {
    await this.stopScanner();
    this.mode = 'choose';
    this.cameraError = null;
  }

  /**
   * Reset to initial state
   */
  reset() {
    this.stopPolling();
    this.mode = 'choose';
    this.qrCodeDataUrl = null;
    this.sessionData = null;
    this.cameraError = null;
  }

  /**
   * Show camera permission alert
   */
  private async showCameraPermissionAlert() {
    const alert = await this.alertController.create({
      header: 'Camera Access Required',
      message: 'Please enable camera access in your device settings to scan QR codes. On iOS: Settings > Safari > Camera',
      buttons: ['OK']
    });
    await alert.present();
  }

  /**
   * Show expiration warning alert
   */
  private async showExpirationAlert() {
    const message = this.hasKeys
      ? 'This QR code will expire in 5 minutes. Scan it from the device that needs your encryption keys.'
      : 'This QR code will expire in 5 minutes. Scan it from a device that has your encryption keys to receive them.';

    const alert = await this.alertController.create({
      header: 'Security Notice',
      message,
      buttons: ['OK']
    });
    await alert.present();
  }

  /**
   * Show toast message
   */
  private async showToast(message: string, color: 'success' | 'warning' | 'danger') {
    const toast = await this.toastController.create({
      message,
      duration: 3000,
      color,
      position: 'bottom'
    });
    await toast.present();
  }
}
