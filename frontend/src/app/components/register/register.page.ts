import { Component, inject } from '@angular/core';
import { CreateUserDto } from "../../dtos/user";
import { FormsModule, NgForm } from "@angular/forms";
import { UserService } from "../../../services/user.service";
import { Router } from "@angular/router";
import { IonicModule, ToastController } from "@ionic/angular";
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../services/auth.service';
import { AuthRequest } from 'src/app/dtos/auth-request';

@Component({
  selector: 'app-register',
  templateUrl: './register.page.html',
  styleUrls: ['./register.page.scss'],
  standalone: true,
  imports: [
    IonicModule,
    FormsModule,
    CommonModule
  ]
})


export class RegisterPage {
  private userService = inject(UserService);
  private router = inject(Router);
  private authService = inject(AuthService);
  private toastCtrl = inject(ToastController);
  isSubmitting = false;
  errorMessage: string | null = null;
  successMessage: string | null = null;
  termsAccepted = false;

  createUser: CreateUserDto = {
    firstname: "",
    lastname: "",
    email: "",
    password: ""
  }


  onSubmit() {
    this.errorMessage = null;
    this.successMessage = null;
    this.isSubmitting = true;

    this.userService.createUser(this.createUser).subscribe({
      next: () => {
        // attempt automatic login
        const authRequest: AuthRequest = {
          email: this.createUser.email,
          password: this.createUser.password
        }
        this.authService.loginUser(authRequest).subscribe({
          next: async () => {
            this.isSubmitting = false;
            this.successMessage = 'Registration successful.';
            const toast = await this.toastCtrl.create({ message: 'Registration successful', color: 'success', duration: 2000 });
            await toast.present();
            // continue to enter personal user data
            this.router.navigate(['/user-data', false]);
          },
          error: async (loginErr) => {
            this.isSubmitting = false;
            console.error('Auto-login failed', loginErr);
            this.errorMessage = loginErr?.error?.message || 'Registration succeeded but automatic login failed. Please sign in manually.';
            const toast = await this.toastCtrl.create({ message: 'Registration succeeded, login failed', color: 'warning', duration: 3000 });
            await toast.present();
          }
        });
      },
      error: async (error) => {
        this.isSubmitting = false;
        console.error("Error when creating user", error);
        const msg = error?.error || 'Registration failed.';
        this.errorMessage = msg;
      }
    });
  }

  openAgb(event: Event) {
    event.preventDefault();
    this.router.navigate(['/TandC']);
  }

  openPrivacyPolicy(event: Event) {
    event.preventDefault();
    this.router.navigate(['/privacy']);
  }

  goToLogin() {
    this.router.navigate(['/login']);
  }


}
