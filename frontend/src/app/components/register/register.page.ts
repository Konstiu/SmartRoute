import { Component, inject } from '@angular/core';
import { CreateUserDto } from "../../dtos/user";
import { FormsModule, NgForm } from "@angular/forms";
import { UserService } from "../../../services/user.service";
import { Router } from "@angular/router";
import { IonicModule, ToastController } from "@ionic/angular";
import { CommonModule } from '@angular/common';
import { AuthService } from '../../../services/auth.service';
import { AuthRequest } from 'src/app/dtos/auth-request';
import { KeyManagementService } from 'src/services/key-management.service';

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
  private keyManagementService = inject(KeyManagementService);
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
      next: async () => {
        // the user is now registered, create the public identity key
        const generated = await this.keyManagementService.generateAndStoreIdentityKey();
        console.log("Generated identity key on registration:", generated);

        // attempt automatic login
        const authRequest: AuthRequest = {
          email: this.createUser.email,
          password: this.createUser.password
        }
        this.authService.loginUser(authRequest).subscribe({
          next: async () => {
            // the user is now logged in, proceed to upload the public identity key
            try {
              await this.keyManagementService.uploadPublicIdentityKey();
              // now generate and upload the signed pre-key
              const updated = await this.keyManagementService.updateSignedPreKeyIfNecessary();
              if (updated) {
                await this.keyManagementService.uploadPublicSignedPreKey();
              };
              // now generate and upload one-time pre-keys
              await this.keyManagementService.generateStoreAndUploadOneTimePreKeysIfNecessary();
            } catch (uploadErr) {
              // this should never happen, but in case it does, inform the user
              console.error('Failed to upload communication keys', uploadErr);
              const toast = await this.toastCtrl.create({ message: 'Registration succeeded, but uploading communication keys failed.', color: 'warning', duration: 3000 });
              await toast.present();
            }

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
