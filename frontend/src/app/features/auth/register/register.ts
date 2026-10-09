import {
  ChangeDetectorRef,
  Component,
  inject
} from '@angular/core';

import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';

import { RouterLink } from '@angular/router';

import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';

import { AuthService } from '../../../core/auth/auth.service';

@Component({
  selector: 'app-register',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule
  ],
  templateUrl: './register.html',
  styleUrl: './register.scss'
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly snackBar = inject(MatSnackBar);

  loading = false;
  message = '';
  isError = false;

  registerForm = this.fb.nonNullable.group({
    username: ['', [
      Validators.required,
      Validators.minLength(3),
      Validators.maxLength(50)
    ]],

    email: ['', [
      Validators.required,
      Validators.email
    ]],

    password: ['', [
      Validators.required,
      Validators.minLength(6),
      Validators.maxLength(100)
    ]]
  });

  submit(): void {
    if (this.loading) {
      return;
    }

    this.message = '';
    this.isError = false;

    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.loading = true;

    this.authService.register(
      this.registerForm.getRawValue()
    ).subscribe({
      next: response => {
        this.loading = false;
        this.isError = !response.success;
        this.message = response.message;

        if (response.success) {
          this.snackBar.open(
            'Регистрация выполнена успешно',
            'Закрыть',
            { duration: 4000 }
          );
        }

        this.cdr.markForCheck();
      },

      error: error => {
        this.loading = false;
        this.isError = true;
        this.message =
          this.authService.getErrorMessage(error);

        this.cdr.markForCheck();
      }
    });
  }
}
