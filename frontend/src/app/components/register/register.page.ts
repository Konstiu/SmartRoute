import { Component } from '@angular/core';
import {CreateUserDto} from "../../dtos/user";

@Component({
  selector: 'app-register',
  templateUrl: 'register.page.html',
  styleUrls: ['register.page.scss'],
  standalone: false,
})
export class RegisterPage {

  createUser:  CreateUserDto = {
    firstname: "",
    lastname: "",
    email: "",
    password: ""
  }

  constructor() {}

}
