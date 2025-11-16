// Create: src/app/pages/login/login.page.ts
import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators} from '@angular/forms';
import {IonicModule, LoadingController, ToastController} from '@ionic/angular';
import {Router} from '@angular/router';
import {AuthService} from '../../../services/auth.service';

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
    private fb: FormBuilder
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]]
    });
  }

  async login() {
    if (this.loginForm.invalid) {
      this.showToast('Please fill in all fields correctly', 'warning');
      return;
    }

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
