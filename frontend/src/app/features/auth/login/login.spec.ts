import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../../app.routes';
import { AuthService } from '../../../core/auth/auth.service';
import { Login } from './login';

describe('Login navigation', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let login: Login;
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
    login = await harness.navigateByUrl('/login', Login);
    login.loginForm.setValue({ username: 'student', password: 'secret123' });
  });
  afterEach(() => http.verify());

  it('navigates to dashboard after HTTP 200 and retains the service and JWT', async () => {
    const session = TestBed.inject(AuthService);
    login.submit();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ username: 'student', password: 'secret123' });
    request.flush({ token: 'test-jwt' });
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/dashboard');
    expect(TestBed.inject(AuthService)).toBe(session);
    // Inspect existing private storage without extending the public session API for this task.
    expect((session as unknown as { accessToken: string }).accessToken).toBe('test-jwt');
    expect(harness.routeNativeElement).not.toBeNull();
  });

  for (const status of [400, 401, 403, 500, 503]) {
    it(`stays on login and displays an error for HTTP ${status}`, async () => {
      login.submit();
      http.expectOne('/api/auth/login').flush({}, { status, statusText: 'Error' });
      await harness.fixture.whenStable();
      harness.detectChanges();
      expect(TestBed.inject(Router).url).toBe('/login');
      expect(login.isError).toBe(true);
      expect(login.loading).toBe(false);
      expect(harness.routeNativeElement?.querySelector('.form-message.error')?.textContent?.trim()).toBe(login.message);
      expect(login.message.length).toBeGreaterThan(0);
    });
  }

  it('does not navigate when HTTP 200 has no token', async () => {
    login.submit();
    http.expectOne('/api/auth/login').flush({});
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(login.isError).toBe(true);
  });

  it('does not send an invalid form', () => {
    login.loginForm.controls.password.setValue('');
    login.submit();
    http.expectNone('/api/auth/login');
    expect(TestBed.inject(Router).url).toBe('/login');
  });
});