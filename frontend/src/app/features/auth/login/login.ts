
import { ChangeDetectorRef, Component, inject } from '@angular/core';
import {
  FormBuilder,
  ReactiveFormsModule,
  Validators
} from '@angular/forms';
import { RouterLink } from '@angular/router';

import { MatSnackBar } from '@angular/material/snack-bar';

import { AuthService } from '../../../core/auth/auth.service';
import { AuthShell } from '../auth-shell';

@Component({
  selector: 'app-login',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    AuthShell,
  ],
  templateUrl: './login.html',
  styleUrl: './login.scss'
})
export class Login {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly snackBar = inject(MatSnackBar);

  showPassword = false;
  loading = false;
  message = '';
  isError = false;

  loginForm = this.fb.nonNullable.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  submit(): void {
    this.message = '';
    this.isError = false;

    if (this.loading) {
      return;
    }

    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.loading = true;

    this.authService.login(
      this.loginForm.getRawValue()
    ).subscribe({
      next: response => {
        this.loading = false;
        this.isError = !response.success;
        this.message = response.message;

        if (response.success) {
          this.snackBar.open(
            'Вход выполнен успешно',
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
