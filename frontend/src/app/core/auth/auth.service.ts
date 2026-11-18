import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, Subject, map } from 'rxjs';

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
  private revision = 0;
  private expiryTimer?: ReturnType<typeof setTimeout>;
  private readonly changes = new Subject<void>();
  readonly sessionChanges = this.changes.asObservable();
  get sessionRevision(): number { return this.revision; }
  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.expiryTimer));
    try {
      const saved = JSON.parse(sessionStorage.getItem('exam-ai-session') ?? 'null');
      if (typeof saved?.token === 'string' && saved.token.trim() && typeof saved?.username === 'string' && saved.username.trim()) {
        this.accessToken = saved.token;
        this.sessionUsername.set(saved.username);
        this.scheduleExpiry();
      } else if (saved !== null) this.clearPrivateStorage();
    } catch { this.clearPrivateStorage(); }
  }
  get token(): string | null { return this.accessToken; }
  private saveSession(token: string, username: string): void {
    this.clearPrivateStorage();
    this.accessToken = token;
    this.sessionUsername.set(username);
    try { sessionStorage.setItem('exam-ai-session', JSON.stringify({ token, username })); }
    catch { /* При запрете хранилища сессия остаётся только в памяти. */ }
    this.revision++;
    this.changes.next();
    this.scheduleExpiry();
  }

  private clearPrivateStorage(): void {
    try {
      for (const key of Object.keys(sessionStorage)) {
        if (key === 'exam-ai-session' || key.startsWith('generation:')) sessionStorage.removeItem(key);
      }
    } catch { /* Очистка состояния в памяти работает и без sessionStorage. */ }
  }

  private scheduleExpiry(): void {
    clearTimeout(this.expiryTimer);
    // exp нужен только для очистки интерфейса. Подпись и права проверяет backend.
    try {
      const payload = this.accessToken?.split('.')[1];
      if (!payload) return;
      const exp = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))).exp;
      if (typeof exp !== 'number' || !Number.isFinite(exp)) return;
      const remaining = exp * 1000 - Date.now();
      if (remaining <= 0) this.logout();
      else this.expiryTimer = setTimeout(() => this.scheduleExpiry(), Math.min(remaining, 2_147_483_647));
    } catch { /* Непрозрачный токен проверяется сервером; 401 завершит сессию. */ }
  }

  private readonly sessionUsername = signal<string | null>(null);
  readonly username = this.sessionUsername.asReadonly();

  logout(): void {
    clearTimeout(this.expiryTimer);
    this.accessToken = null;
    this.clearPrivateStorage();
    this.sessionUsername.set(null);
    this.revision++;
    this.changes.next();
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    const revision = this.revision;
    return this.http.post<JwtResponse>(
      '/api/auth/login',
      data
    ).pipe(
      map(response => {
        if (revision !== this.revision) throw new Error('Сессия изменилась. Повторите вход.');
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
    const revision = this.revision;
    return this.http.post<JwtResponse>(
      '/api/auth/register',
      data
    ).pipe(
      map(response => {
        if (revision !== this.revision) throw new Error('Сессия изменилась. Повторите регистрацию.');
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
