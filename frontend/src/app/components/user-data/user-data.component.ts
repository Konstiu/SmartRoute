import { CommonModule } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IonicModule, ToastController } from "@ionic/angular";
import { PersonalDataDto } from 'src/app/dtos/user';
import { UserService } from 'src/services/user.service';

@Component({
  selector: 'app-user-data',
  templateUrl: './user-data.component.html',
  styleUrls: ['./user-data.component.scss'],
  standalone: true,
  imports: [IonicModule, FormsModule, ReactiveFormsModule, CommonModule],
})
export class UserDataComponent implements OnInit {
  userDataControl = new FormGroup({
    sex: new FormControl(""),
    height: new FormControl(0),
    weight: new FormControl(0),
    birthdate: new FormControl(new Date(), (control) => {
      let date = new Date(control.value);
      if (date == null) return null;
      if (date >= new Date()) return { past: true };
      return null;
    }),
    experienceLevel: new FormControl(""),
    activeWeekdays: new FormControl([""]),
  });
  today = new Date().toISOString().split("T")[0];

  userService = inject(UserService);
  router = inject(Router);
  toastCtrl = inject(ToastController);

  errorToastOpen = false;
  isLoading = false;

  onSubmit() {
    if (this.userDataControl.invalid) return;
    this.isLoading = true;
    // disable the reactive form controls programmatically to avoid using the
    // `disabled` attribute in the template (prevents changed-after-checked warnings)
    this.userDataControl.disable({ emitEvent: false });

    this.userService.updatePersonalData(this.userDataControl.value as PersonalDataDto).subscribe({
      next: async () => {
        this.isLoading = false;
        this.userDataControl.enable({ emitEvent: false });
        const toast = await this.toastCtrl.create({
          message: 'Personal data saved successfully.',
          color: 'success',
          duration: 3000,
          position: 'top'
        });
        await toast.present();
      },
      error: (error) => {
        console.log("ERROR: When updating personal user data: ", error);
        this.errorToastOpen = true;
        this.isLoading = false;
        this.userDataControl.enable({ emitEvent: false });
      }
    });
  }

  setErrorToastOpen(open: boolean) {
    this.errorToastOpen = open;
  }

  ngOnInit() {
    this.userService.getUserData().subscribe({
      next: (data) => {
        this.userDataControl.setValue({
          sex: data.sex,
          height: data.height,
          weight: data.weight,
          birthdate: data.birthdate,
          experienceLevel: data.experienceLevel,
          activeWeekdays: data.activeWeekdays
        });
      },
      error: (error) => {
        console.log("ERROR: When fetching personal user data: ", error);
      }
    });
  }
}
