import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonicModule, ToastController } from '@ionic/angular';
import { CommonModule } from "@angular/common";
import { UserService } from "../../../services/user.service";
import { ResetPasswordDto } from "../../dtos/passwordReset";

@Component({
  selector: 'app-confirm-reset-password',
  templateUrl: './reset-password.page.html',
  styleUrls: ['./reset-password.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule
  ]
})
export class ResetPasswordPage implements OnInit {
  confirmForm: FormGroup;
  isLoading = false;
  token: string = '';
  showPassword = false;
  showConfirmPassword = false;

  constructor(
    private formBuilder: FormBuilder,
    private router: Router,
    private route: ActivatedRoute,
    private toastController: ToastController,
    private userService: UserService
  ) {
    this.confirmForm = this.formBuilder.group({
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required, this.matchPasswordValidator.bind(this)]]
    }, { validators: this.passwordMatchValidator });
  }

  ngOnInit() {
    // Get token from route params (matches /password_reset/:token)
    this.route.params.subscribe(params => {
      this.token = params['token'] || '';

      if (!this.token) {
        this.showErrorAndRedirect('Invalid or missing reset token. Please request a new password reset.');
      }
    });
  }

  /**
   * Custom validator to check if passwords match
   */
  passwordMatchValidator(group: FormGroup) {
    const password = group.get('password')?.value;
    const confirmPassword = group.get('confirmPassword')?.value;
    return password === confirmPassword ? null : { passwordMismatch: true };
  }

  /**
   * Submit the new password
   */
  async confirmReset() {
    if (this.confirmForm.valid && this.token) {
      this.isLoading = true;

      const resetDto: ResetPasswordDto = {
        token: this.token,
        password: this.confirmForm.value.password,
        repeatPassword: this.confirmForm.value.confirmPassword
      };

      this.userService.resetPasswordWithToken(resetDto).subscribe({
        next: async () => {
          this.isLoading = false;

          const toast = await this.toastController.create({
            message: 'Password reset successful! You can now login with your new password.',
            duration: 3000,
            color: 'success',
            position: 'top'
          });
          await toast.present();

          // Redirect to login after short delay
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 2000);
        },
        error: async (error) => {
          this.isLoading = false;
          console.error('Error resetting password:', error);

          const errorMessage = error?.error?.message ||
            error?.error ||
            'Failed to reset password. The link may have expired or is invalid.';

          const toast = await this.toastController.create({
            message: errorMessage,
            duration: 4000,
            color: 'danger',
            position: 'top'
          });
          await toast.present();
        }
      });
    } else {
      // Mark all fields as touched to show validation errors
      Object.keys(this.confirmForm.controls).forEach(key => {
        this.confirmForm.get(key)?.markAsTouched();
      });

      if (!this.token) {
        await this.showErrorAndRedirect('Invalid reset token.');
      }
    }
  }

  /**
   * Show error toast and redirect to login
   */
  async showErrorAndRedirect(message: string) {
    const toast = await this.toastController.create({
      message,
      duration: 3000,
      color: 'danger',
      position: 'top'
    });
    await toast.present();

    setTimeout(() => {
      this.router.navigate(['/login']);
    }, 3000);
  }

  /**
   * Toggle password visibility
   */
  togglePasswordVisibility(field: 'password' | 'confirmPassword') {
    if (field === 'password') {
      this.showPassword = !this.showPassword;
    } else {
      this.showConfirmPassword = !this.showConfirmPassword;
    }
  }

  /**
   * match passwords
   */
  matchPasswordValidator(control: any) {
    const password = this.confirmForm?.get('password')?.value;
    const confirmPassword = control.value;

    if (password && confirmPassword && password !== confirmPassword) {
      return { passwordMismatch: true };
    }
    return null;
  }

  /**
   * Navigate back to login
   */
  goToLogin() {
    this.router.navigate(['/login']);
  }

  // Getters for template
  get password() {
    return this.confirmForm.get('password');
  }

  get confirmPassword() {
    return this.confirmForm.get('confirmPassword');
  }

  get hasPasswordMismatch() {
    return this.confirmForm.errors?.['passwordMismatch'] &&
      this.confirmPassword?.touched;
  }
}
