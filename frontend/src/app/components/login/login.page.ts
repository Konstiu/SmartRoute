import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators} from '@angular/forms';
import {IonicModule, LoadingController, ToastController} from '@ionic/angular';
import {Router} from '@angular/router';
import {AuthService} from '../../../services/auth.service';
import {KeyManagementService} from "../../../services/key-management.service";

@Component({
  selector: 'app-login',
  templateUrl: './login.page.html',
  styleUrls: ['./login.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule
  ]
})
export class LoginPage {
  loginForm: FormGroup;
  showPassword = false;

  constructor(
    private authService: AuthService,
    private router: Router,
    private loadingCtrl: LoadingController,
    private toastCtrl: ToastController,
    private fb: FormBuilder,
    private keyManagementService: KeyManagementService
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required]]
    });
  }

  async login() {

    const loading = await this.loadingCtrl.create({
      message: 'Logging in...',
      spinner: 'crescent'
    });
    await loading.present();

    const {email, password} = this.loginForm.value;

    this.authService.loginUser({email, password}).subscribe({
      next: async () => {
        await loading.dismiss();
        this.showToast('Login successful!', 'success');
        await this.keyManagementService.generateAndStoreIdentityKey();

        try {
          await this.keyManagementService.uploadPublicIdentityKey();
          // now generate and upload the signed pre-key
          const updated = await this.keyManagementService.updateSignedPreKeyIfNecessary();
          if (updated) {
            await this.keyManagementService.uploadPublicSignedPreKey();
          }
          // now generate and upload one-time pre-keys
          await this.keyManagementService.generateStoreAndUploadOneTimePreKeysIfNecessary();
        } catch (uploadErr) {
          // this should never happen, but in case it does, inform the user
          console.error('Failed to upload communication keys', uploadErr);
          const toast = await this.toastCtrl.create({
            message: 'Registration succeeded, but uploading communication keys failed.',
            color: 'warning',
            duration: 3000
          });
          await toast.present();
        }
        this.redirectBasedOnRole();
      },
      error: async (error) => {
        await loading.dismiss();
        const message = error.error?.message || 'Login failed. Please check your credentials.';
        this.showToast(message, 'danger');
      }
    });
  }

  private redirectBasedOnRole() {
    this.router.navigate(['/tabs/trainingPlan']);
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  goToRegister() {
    this.router.navigate(['/register']);
  }

  goToPasswordReset() {
    this.router.navigate(['/request-password-reset']);
  }

  private async showToast(message: string, color: string) {
    const toast = await this.toastCtrl.create({
      message,
      duration: 3000,
      color,
      position: 'top'
    });
    await toast.present();
  }
}
