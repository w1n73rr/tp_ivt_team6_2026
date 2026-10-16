import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, map } from 'rxjs';

import {
  AuthResponse,
  JwtResponse,
  LoginRequest,
  RegisterRequest
} from './auth.models';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly http = inject(HttpClient);

  // Пока храним токен только в памяти приложения.
  private accessToken: string | null = null;

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http.post<JwtResponse>(
      '/api/auth/login',
      data
    ).pipe(
      map(response => {
        if (!response.token) {
          throw new Error('Сервер не вернул токен');
        }

        this.accessToken = response.token;

        return {
          success: true,
          message: 'Вход выполнен успешно'
        };
      })
    );
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<JwtResponse>(
      '/api/auth/register',
      data
    ).pipe(
      map(response => {
        if (!response.token) {
          throw new Error('Сервер не вернул токен');
        }

        this.accessToken = response.token;

        return {
          success: true,
          message: 'Регистрация выполнена успешно'
        };
      })
    );
  }

  getErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {

      if (error.status === 0) {
        return 'Не удалось подключиться к серверу';
      }

      // Backend возвращает {"error": "..."}
      if (typeof error.error?.error === 'string') {
        return error.error.error;
      }

      if (error.status === 400) {
        return 'Проверьте введённые данные';
      }

      if (error.status === 401) {
        return 'Неверное имя пользователя или пароль';
      }

      if (error.status === 403) {
        return 'Доступ запрещён';
      }

      if (error.status >= 500) {
        return 'Ошибка сервера. Попробуйте позже';
      }
    }

    return 'Не удалось выполнить операцию';
  }
}