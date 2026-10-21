import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { Material, MaterialsResponse } from './materials.models';
@Injectable({ providedIn: 'root' })
export class MaterialsApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  list(): Observable<MaterialsResponse> {
    const token = this.auth.token;
    if (!token) return throwError(() => new HttpErrorResponse({ status: 401 }));
    return this.http.get<MaterialsResponse>('/api/materials', {
      headers: new HttpHeaders({ Authorization: `Bearer ${token}` }),
    });
  }
  private headers(): HttpHeaders {
    return new HttpHeaders(this.auth.token ? { Authorization: `Bearer ${this.auth.token}` } : {});
  }
  detail(id: string): Observable<{ material: Material; description?: string; tags: {id: string; name: string}[] }> {
    return this.http.get<{ material: Material; description?: string; tags: {id: string; name: string}[] }>(`/api/materials/${encodeURIComponent(id)}`, { headers: this.headers() });
  }
  update(id: string, data: { name: string; subjectName: string }): Observable<Material> {
    return this.http.patch<Material>(`/api/materials/${encodeURIComponent(id)}`, data, { headers: this.headers() });
  }
  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/materials/${encodeURIComponent(id)}`, { headers: this.headers() });
  }
  file(id: string, inline = false) {
    return this.http.get(`/api/materials/${encodeURIComponent(id)}/${inline ? 'file' : 'download'}`, {
      headers: this.headers(), responseType: 'blob', observe: 'response',
    });
  }
  actionError(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      switch (error.status) {
        case 0: return 'Нет соединения с сервером. Повторите попытку.';
        case 400: return 'Проверьте название и предмет материала.';
        case 401: return 'Сессия завершена. Войдите ещё раз.';
        case 403: return 'Доступ запрещён.';
        case 404: return 'Материал не найден или уже удалён. Обновите список.';
        case 409: return 'Название уже занято или материал сейчас обрабатывается.';
        case 415: return 'Просмотр этого формата недоступен. Можно скачать оригинал.';
        case 501: return 'Эта операция ещё не реализована на сервере.';
        case 503: return 'Хранилище временно недоступно. Повторите позже.';
      }
    }
    return 'Не удалось выполнить операцию. Повторите попытку.';
  }
  errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 501)
      return 'Получение списка материалов ещё не реализовано на сервере. Загрузка доступна отдельно.';
    if (error instanceof HttpErrorResponse && error.status === 401)
      return 'Сессия завершена. Войдите в приложение ещё раз.';
    return 'Не удалось загрузить материалы. Попробуйте ещё раз.';
  }
}
