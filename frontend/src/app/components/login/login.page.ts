import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {IonicModule, LoadingController, ToastController} from '@ionic/angular';
import {Router} from '@angular/router';
import {AuthService} from '../../../services/auth.service';
import {PushNotificationService} from '../../../services/push-notification.service'

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
    private pushNotificationService: PushNotificationService
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
        await this.redirectBasedOnRole();
      },
      error: async (error) => {
        await loading.dismiss();
        const message = error.error?.message || 'Login failed. Please check your credentials.';
        this.showToast(message, 'danger');
      }
    });
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

  private async redirectBasedOnRole() {
    await this.router.navigate(['/tabs/trainingPlan']);
    this.pushNotificationService.autoInitialize().catch(err => {
      console.error('Push notification setup failed:', err);
    });  }

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
