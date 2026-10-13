import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { Register } from './register/register';
import { Login } from './login/login';

describe('Auth forms', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });
  afterEach(() => http.verify());

  async function registration() {
    const page = await harness.navigateByUrl('/register', Register);
    page.registerForm.setValue({
      username: 'student',
      email: 'student@example.com',
      password: 'secret123',
      confirmPassword: 'secret123',
    });
    harness.detectChanges();
    return page;
  }

  it('sends only the three backend registration fields, preserves JWT and prevents duplicate submissions', async () => {
    const page = await registration();
    page.submit();
    page.submit();
    const request = http.expectOne('/api/auth/register');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      username: 'student',
      email: 'student@example.com',
      password: 'secret123',
    });
    request.flush({ token: 'registered-jwt' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(page.isError).toBe(false);
    expect(harness.routeNativeElement?.querySelector('.success')?.textContent).toContain(
      'Регистрация выполнена успешно',
    );
  });

  it('rejects mismatch, then accepts corrected confirmation without stale errors', async () => {
    const page = await registration();
    page.registerForm.controls.confirmPassword.setValue('different');
    page.submit();
    harness.detectChanges();
    http.expectNone('/api/auth/register');
    expect(harness.routeNativeElement?.textContent).toContain('Пароли не совпадают');
    page.registerForm.controls.confirmPassword.setValue('secret123');
    expect(page.registerForm.valid).toBe(true);
    page.registerForm.controls.password.setValue('changed123');
    expect(page.registerForm.hasError('passwordMismatch')).toBe(true);
  });

  it('keeps password fields independent with separate names and new-password autocomplete', async () => {
    const page = await registration();
    const password = harness.routeNativeElement!.querySelector<HTMLInputElement>('#password')!;
    const confirmation =
      harness.routeNativeElement!.querySelector<HTMLInputElement>('#confirmPassword')!;
    expect(password.name).not.toBe(confirmation.name);
    expect(password.autocomplete).toBe('new-password');
    expect(confirmation.autocomplete).toBe('new-password');
    password.value = 'autofilled123';
    password.dispatchEvent(new Event('input', { bubbles: true }));
    expect(page.registerForm.controls.password.value).toBe('autofilled123');
    expect(page.registerForm.controls.confirmPassword.value).toBe('secret123');
    confirmation.value = 'autofilled123';
    confirmation.dispatchEvent(new Event('input', { bubbles: true }));
    expect(page.registerForm.valid).toBe(true);
  });

  it('requires confirmation and blocks invalid email', async () => {
    const page = await registration();
    page.registerForm.patchValue({ confirmPassword: '', email: 'invalid' });
    page.submit();
    harness.detectChanges();
    http.expectNone('/api/auth/register');
    expect(harness.routeNativeElement?.textContent).toContain('Повторите пароль');
    expect(harness.routeNativeElement?.textContent).toContain('Введите корректный email');
  });

  it('toggles visibility independently without submitting or modifying either password', async () => {
    const page = await registration();
    const buttons = harness.routeNativeElement!.querySelectorAll<HTMLButtonElement>('.visibility');
    buttons[0].click();
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector<HTMLInputElement>('#password')!.type).toBe(
      'text',
    );
    expect(
      harness.routeNativeElement!.querySelector<HTMLInputElement>('#confirmPassword')!.type,
    ).toBe('password');
    expect(page.registerForm.controls.password.value).toBe('secret123');
    http.expectNone('/api/auth/register');
  });

  for (const status of [400, 401, 403, 500]) {
    it(`shows registration error for HTTP ${status}`, async () => {
      const page = await registration();
      page.submit();
      http.expectOne('/api/auth/register').flush({}, { status, statusText: 'Error' });
      await harness.fixture.whenStable();
      harness.detectChanges();
      expect(page.loading).toBe(false);
      expect(harness.routeNativeElement?.querySelector('.error')?.textContent?.trim()).toBe(
        page.message,
      );
      expect(TestBed.inject(Router).url).toBe('/register');
    });
  }

  it('keeps login API contract and displays server errors', async () => {
    const page = await harness.navigateByUrl('/login', Login);
    page.loginForm.setValue({ username: 'student', password: 'secret123' });
    page.submit();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.body).toEqual({ username: 'student', password: 'secret123' });
    request.flush({}, { status: 401, statusText: 'Unauthorized' });
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(harness.routeNativeElement?.textContent).toContain(
      'Неверное имя пользователя или пароль',
    );
  });

  it('switches forms using the router tab links', async () => {
    await harness.navigateByUrl('/login', Login);
    harness
      .routeNativeElement!.querySelector<HTMLAnchorElement>('.auth-tabs a[href="/register"]')!
      .click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/register');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Создайте аккаунт');
  });
});
