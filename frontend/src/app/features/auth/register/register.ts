import { ChangeDetectorRef, Component, inject } from '@angular/core';

import {
  AbstractControl,
  ValidationErrors,
  FormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { AuthShell } from '../auth-shell';
import { RouterLink } from '@angular/router';

import { MatSnackBar } from '@angular/material/snack-bar';

import { AuthService } from '../../../core/auth/auth.service';

export function passwordsMatch(control: AbstractControl): ValidationErrors | null {
  const password = control.get('password')?.value;
  const confirmation = control.get('confirmPassword')?.value;
  return !confirmation || password === confirmation ? null : { passwordMismatch: true };
}

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, AuthShell],
  templateUrl: './register.html',
  styleUrl: './register.scss',
})
export class Register {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly snackBar = inject(MatSnackBar);

  showPassword = false;
  showConfirmPassword = false;
  loading = false;
  message = '';
  isError = false;

  registerForm = this.fb.nonNullable.group(
    {
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],

      email: ['', [Validators.required, Validators.email]],

      password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(100)]],
      confirmPassword: ['', Validators.required],
    },
    { validators: passwordsMatch },
  );

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

    const { username, email, password } = this.registerForm.getRawValue();
    this.authService.register({ username, email, password }).subscribe({
      next: (response) => {
        this.loading = false;
        this.isError = !response.success;
        this.message = response.message;

        if (response.success) {
          this.snackBar.open('Регистрация выполнена успешно', 'Закрыть', { duration: 4000 });
        }

        this.cdr.markForCheck();
      },

      error: (error) => {
        this.loading = false;
        this.isError = true;
        this.message = this.authService.getErrorMessage(error);

        this.cdr.markForCheck();
      },
    });
  }
}
