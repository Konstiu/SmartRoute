import { Component, inject, OnInit } from '@angular/core';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { IonicModule } from "@ionic/angular";
import { PersonalDataDto } from 'src/app/dtos/user';
import { UserService } from 'src/services/user.service';

@Component({
  selector: 'app-user-data',
  templateUrl: './user-data.component.html',
  styleUrls: ['./user-data.component.scss'],
  standalone: true,
  imports: [IonicModule, FormsModule, ReactiveFormsModule],
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

  errorToastOpen = false;

  onSubmit() {
    if (this.userDataControl.invalid) return;
    this.userService.updatePersonalData(this.userDataControl.value as PersonalDataDto).subscribe({
      next: () => this.router.navigateByUrl("/"),
      error: (error) => {
        console.log("ERROR: When updating personal user data: ", error);
        this.errorToastOpen = true;
      }
    });
  }

  setErrorToastOpen(open: boolean) {
    this.errorToastOpen = open;
  }

  ngOnInit() { }
}
