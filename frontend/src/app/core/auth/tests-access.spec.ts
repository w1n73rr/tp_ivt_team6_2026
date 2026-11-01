import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { RouterTestingHarness } from '@angular/router/testing';
import { of } from 'rxjs';
import { routes } from '../../app.routes';
import { Login } from '../../features/auth/login/login';
import { Register } from '../../features/auth/register/register';
import { TestsPage } from '../../features/tests/tests';
import { TESTS_SOURCE } from '../../features/tests/tests-source';

describe('Tests access through existing auth', () => {
  let list: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    sessionStorage.clear(); list = vi.fn(() => of({ items: [] }));
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting(), { provide: TESTS_SOURCE, useValue: { demo: true, list } }] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });
  it('redirects direct access and never loads tests before login', async () => {
    const h = await RouterTestingHarness.create('/tests');
    expect(TestBed.inject(Router).url).toBe('/login?returnTo=%2Ftests');
    expect(list).not.toHaveBeenCalled(); expect(h.routeNativeElement!.querySelector('table')).toBeNull();
  });
  it('returns to tests only after successful login and blocks browser back after logout', async () => {
    const h = await RouterTestingHarness.create();
    const page = await h.navigateByUrl('/login?returnTo=%2Ftests', Login);
    page.loginForm.setValue({ username: 'student', password: 'secret123' }); page.submit();
    expect(list).not.toHaveBeenCalled();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({ token: 'server-test-token' });
    await h.fixture.whenStable(); h.detectChanges();
    expect(TestBed.inject(Router).url).toBe('/tests'); expect(list).toHaveBeenCalledTimes(1);
    h.routeNativeElement!.querySelector<HTMLButtonElement>('.logout')!.click(); await h.fixture.whenStable();
    await h.navigateByUrl('/tests'); expect(TestBed.inject(Router).url).toContain('/login'); expect(list).toHaveBeenCalledTimes(1);
  });
  it('preserves the destination when switching to registration and returns after signup', async () => {
    const h = await RouterTestingHarness.create('/login?returnTo=%2Ftests');
    h.routeNativeElement!.querySelector<HTMLAnchorElement>('.auth-link a')!.click(); await h.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/register?returnTo=%2Ftests');
    const page = await h.navigateByUrl('/register?returnTo=%2Ftests', Register);
    page.registerForm.setValue({ username: 'new-student', email: 'student@example.com', password: 'secret123', confirmPassword: 'secret123' });
    page.submit(); TestBed.inject(HttpTestingController).expectOne('/api/auth/register').flush({ token: 'registered-test-token' });
    await h.fixture.whenStable(); expect(TestBed.inject(Router).url).toBe('/tests'); expect(list).toHaveBeenCalledTimes(1);
  });
  it('keeps tests blocked after failed login', async () => {
    const h = await RouterTestingHarness.create(); const page = await h.navigateByUrl('/login?returnTo=%2Ftests', Login);
    page.loginForm.setValue({ username: 'student', password: 'wrong' }); page.submit();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    await h.fixture.whenStable(); expect(list).not.toHaveBeenCalled(); expect(TestBed.inject(Router).url).toContain('/login');
  });
  it('restores an existing tab session on refresh', async () => {
    sessionStorage.setItem('exam-ai-session', JSON.stringify({ token: 'server-test-token', username: 'student' }));
    const h = await RouterTestingHarness.create(); await h.navigateByUrl('/tests', TestsPage);
    expect(list).toHaveBeenCalledTimes(1); expect(TestBed.inject(Router).url).toBe('/tests');
  });
  it('ignores an external return destination', async () => {
    const h = await RouterTestingHarness.create(); const page = await h.navigateByUrl('/login?returnTo=https://example.com', Login);
    page.loginForm.setValue({ username: 'student', password: 'secret123' }); page.submit();
    TestBed.inject(HttpTestingController).expectOne('/api/auth/login').flush({ token: 'server-test-token' });
    await h.fixture.whenStable(); expect(TestBed.inject(Router).url).toBe('/dashboard');
  });
});
