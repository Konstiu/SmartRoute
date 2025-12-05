import { Component, inject, OnInit } from '@angular/core';
import { UserDetailDto } from 'src/app/dtos/user';
import { UserService } from 'src/services/user.service';
import { IonicModule } from "@ionic/angular";
import { Router } from '@angular/router';

@Component({
  selector: 'app-user-data-display',
  templateUrl: './user-data-display.component.html',
  styleUrls: ['./user-data-display.component.scss'],
  imports: [IonicModule]
})
export class UserDataDisplayComponent implements OnInit {

  userData: UserDetailDto | null = null;
  userService = inject(UserService);
  router = inject(Router);

  errorToastOpen = false;

  constructor() { }

  ngOnInit() {
    this.userService.getUserData().subscribe({
      next: (data) => this.userData = data,
      error: (error) => {
        console.log("ERROR: When retrieving personal user data: ", error);
        this.setErrorToastOpen(true);
      }
    });
    this.userService.watchPersonalData()?.subscribe((data) => {
      this.userData = data;
    });
  }

  setErrorToastOpen(open: boolean) {
    this.errorToastOpen = open;
  }

  updateUserData() {
    this.router.navigate(["/user-data", true]);
  }

  displaySex() {
    if (!this.userData) return;
    if (this.userData.sex == "FEMALE") return "Female";
    if (this.userData.sex == "MALE") return "Male";
    if (this.userData.sex == "OTHER") return "Other";
    return;
  }

  displayBirthdate() {
    if (this.userData?.birthdate == undefined) {
      return;
    }
    return new Date(this.userData?.birthdate).toLocaleDateString()
  }

  displayExperienceLevel() {
    if (this.userData?.experienceLevel == "BEGINNER") return "Beginner";
    if (this.userData?.experienceLevel == "CASUAL") return "Casual";
    if (this.userData?.experienceLevel == "INTERMEDIATE") return "Intermediate";
    if (this.userData?.experienceLevel == "ADVANCED") return "Advanced";
    if (this.userData?.experienceLevel == "COMPETITIVE_ATHLETE") return "Competitive athlete";
    return;
  }

  displayActiveWeekdays() {
    if (this.userData?.activeWeekdays == undefined) return;
    return this.userData.activeWeekdays.map(this.displayWeekday).join(", ");
  }

  displayWeekday(weekday: string) {
    if (weekday == "MONDAY") return "Monday";
    if (weekday == "TUESDAY") return "Tuesday";
    if (weekday == "WEDNESDAY") return "Wednesday";
    if (weekday == "THURSDAY") return "Thursday";
    if (weekday == "FRIDAY") return "Friday";
    if (weekday == "SATURDAY") return "Saturday";
    if (weekday == "SUNDAY") return "Sunday";
    return;
  }
}
