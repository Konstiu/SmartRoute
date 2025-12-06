import { Location } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { IonicModule, ToastController } from "@ionic/angular";
import { PersonalDataDto, UserDetailDto } from 'src/app/dtos/user';
import { UserService } from 'src/services/user.service';

@Component({
  selector: 'app-user-data-form',
  templateUrl: './user-data-form.component.html',
  styleUrls: ['./user-data-form.component.scss'],
  standalone: true,
  imports: [IonicModule, FormsModule, ReactiveFormsModule],
})
export class UserDataFormComponent implements OnInit {
  userDataControl = new FormGroup({
    sex: new FormControl(""),
    height: new FormControl(0),
    weight: new FormControl(0),
    birthdate: new FormControl(new Date(), (control) => {
      let date = new Date(control.value);
      if (date == null) return null;
      const currentDate = new Date();
      const maxDate = new Date(currentDate);
      maxDate.setFullYear(maxDate.getFullYear() - 6);
      if (date >= maxDate) return { past: true };
      const minDate = new Date(currentDate);
      minDate.setFullYear(minDate.getFullYear() - 120);
      if (date <= minDate) return { unrealisticallyOld: true };
      return null;
    }),
    experienceLevel: new FormControl(""),
    activeWeekdays: new FormControl([""]),
  });
  today = new Date().toISOString().split("T")[0];

  userService = inject(UserService);
  router = inject(Router);
  toastCtrl = inject(ToastController);

  sendErrorToastOpen = false;
  getErrorToastOpen = false;
  isLoading = false;

  location
  activatedRoute

  constructor(location: Location, route: ActivatedRoute) {
    this.location = location;
    this.activatedRoute = route;
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
        console.log("ERROR: When retrieving personal user data: ", error);
        this.setGetErrorToastOpen(true);
      }
    });
  }

  convertToPersonalDataDto(dto: UserDetailDto): PersonalDataDto {
    return {
      sex: dto.sex,
      height: dto.height,
      weight: dto.weight,
      birthdate: dto.birthdate,
      experienceLevel: dto.experienceLevel,
      activeWeekdays: dto.activeWeekdays,
    };
  }

  onSubmit() {
    if (this.userDataControl.invalid) return
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
        if (this.activatedRoute.snapshot.paramMap.get("returnToCallsite") == "true") {
          this.location.back();
        } else {
          this.router.navigate(["/"]);
        }
      },
      error: (error) => {
        console.log("ERROR: When updating personal user data: ", error);
        this.isLoading = false;
        this.userDataControl.enable({ emitEvent: false })
        this.setSendErrorToastOpen(true);
      }
    });
  }

  setSendErrorToastOpen(open: boolean) {
    this.sendErrorToastOpen = open;
  }

  setGetErrorToastOpen(open: boolean) {
    this.getErrorToastOpen = open;
  }
}
