import { Injectable, inject, signal } from '@angular/core';
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

  private accessToken: string | null = null;
  constructor() {
    try {
      const saved = JSON.parse(sessionStorage.getItem('exam-ai-session') ?? 'null');
      if (typeof saved?.token === 'string' && typeof saved?.username === 'string') {
        this.accessToken = saved.token;
        this.sessionUsername.set(saved.username);
      }
    } catch { /* Недоступное хранилище не мешает входу в текущей вкладке. */ }
  }
  get token(): string | null { return this.accessToken; }
  private saveSession(token: string, username: string): void {
    this.accessToken = token;
    this.sessionUsername.set(username);
    try { sessionStorage.setItem('exam-ai-session', JSON.stringify({ token, username })); }
    catch { /* При запрете хранилища сессия остаётся только в памяти. */ }
  }

  private readonly sessionUsername = signal<string | null>(null);
  readonly username = this.sessionUsername.asReadonly();

  logout(): void {
    this.accessToken = null;
    try { sessionStorage.removeItem('exam-ai-session'); } catch {}
    this.sessionUsername.set(null);
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http.post<JwtResponse>(
      '/api/auth/login',
      data
    ).pipe(
      map(response => {
        if (!response.token) {
          throw new Error('Сервер не вернул токен');
        }

        this.saveSession(response.token, data.username);

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

        this.saveSession(response.token, data.username);

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