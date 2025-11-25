import { Component } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { IonicModule, ToastController } from '@ionic/angular';
import { CommonModule } from "@angular/common";
import { UserService } from "../../../../services/user.service";
import { SendPasswordResetDto } from "../../../dtos/passwordReset";

@Component({
  selector: 'app-reset-password',
  templateUrl: './request-reset-password.page.html',
  styleUrls: ['./request-reset-password.page.scss'],
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    IonicModule
  ]
})
export class RequestResetPasswordPage {
  resetForm: FormGroup;
  submitted = false;
  isLoading = false;

  constructor(
    private formBuilder: FormBuilder,
    private router: Router,
    private toastController: ToastController,
    private userService: UserService
  ) {
    this.resetForm = this.formBuilder.group({
      email: ['', [Validators.required, Validators.email]]
    });
  }

  async resetPassword() {
    if (this.resetForm.valid) {
      this.isLoading = true;

      const emailDto: SendPasswordResetDto = {
        email: this.resetForm.value.email
      };

      this.userService.requestPasswordReset(emailDto).subscribe({
        next: async () => {
          this.isLoading = false;
          this.submitted = true;

          const toast = await this.toastController.create({
            message: 'Password reset link sent to your email!',
            duration: 3000,
            color: 'success',
            position: 'top'
          });
          await toast.present();

          // Navigate back to login after a delay
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 2000);
        },
        error: async (error) => {
          this.isLoading = false;
          console.error('Error sending password reset email:', error);

          const toast = await this.toastController.create({
            message: error?.error?.message || 'Failed to send password reset email. Please try again.',
            duration: 4000,
            color: 'danger',
            position: 'top'
          });
          await toast.present();
        }
      });
    } else {
      // Mark all fields as touched to show validation errors
      Object.keys(this.resetForm.controls).forEach(key => {
        this.resetForm.get(key)?.markAsTouched();
      });
    }
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }

  // Getter for easy access in template
  get email() {
    return this.resetForm.get('email');
  }
}
