import { inject } from '@angular/core';
import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { EMPTY, catchError, filter, takeUntil, throwError } from 'rxjs';
import { AuthService } from './auth.service';

export const sessionInterceptor: HttpInterceptorFn = (request, next) => {
  // Не передаём JWT внешним адресам, в URL или в запросы входа/регистрации.
  const path = request.url.split('?')[0];
  if (!request.url.startsWith('/api/') || ['/api/auth/login', '/api/auth/register'].includes(path)) return next(request);
  const auth = inject(AuthService);
  const token = auth.token;
  if (!token) return throwError(() => new HttpErrorResponse({ status: 401 }));
  const revision = auth.sessionRevision;
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })).pipe(
    // Отмена подписки блокирует также поздние ответы старой сессии.
    takeUntil(auth.sessionChanges),
    filter(() => revision === auth.sessionRevision),
    catchError(error => {
      if (revision !== auth.sessionRevision) return EMPTY;
      if (error instanceof HttpErrorResponse && error.status === 401) auth.logout();
      return throwError(() => error);
    }),
  );
};
