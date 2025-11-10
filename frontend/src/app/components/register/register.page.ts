import {Component, inject} from '@angular/core';
import {CreateUserDto} from "../../dtos/user";
import {FormsModule, NgForm} from "@angular/forms";
import {UserService} from "../../../services/user.service";
import {Router} from "@angular/router";
import {IonicModule} from "@ionic/angular";

@Component({
  selector: 'app-register',
  templateUrl: './register.page.html',
  styleUrls: ['./register.page.scss'],
  standalone: true,
  imports: [
    IonicModule,
    FormsModule
  ]
})


export class RegisterPage {
  private userService = inject(UserService);
  private router = inject(Router);
  created: boolean =  false;



  createUser: CreateUserDto = {
    firstname: "",
    lastname: "",
    email: "",
    password: ""
  }


  onSubmit() {
      this.userService.createUser(this.createUser).subscribe({
        next: () => {
          this.created = true;
        },
        error: error => {
          console.error("Error when creating user", error);
        }
      });
    }


}
