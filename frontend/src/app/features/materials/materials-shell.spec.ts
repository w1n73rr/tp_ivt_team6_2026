import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { routes } from '../../app.routes';
import { AuthService } from '../../core/auth/auth.service';

describe('Account logout', () => {
  beforeEach(() => TestBed.configureTestingModule({providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()]}));
  it('clears the JWT and opens login from the shared sidebar', async () => {
    const auth = TestBed.inject(AuthService);
    auth.login({username: 'VA', password: 'password'}).subscribe();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/login').flush({token: 'test-session-token'});
    expect((auth as unknown as {accessToken: string | null}).accessToken).toBe('test-session-token');
    const harness = await RouterTestingHarness.create('/dashboard');
    expect(harness.routeNativeElement!.querySelector('.account b')!.textContent).toBe('VA');
    expect(harness.routeNativeElement!.querySelector('.avatar')!.textContent).toBe('V');
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('.logout')!.click();
    await harness.fixture.whenStable();
    expect((auth as unknown as {accessToken: string | null}).accessToken).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(harness.routeNativeElement!.querySelector('form')).not.toBeNull();
    expect(auth.username()).toBeNull();
    http.verify();
  });
  it('shows a neutral profile without a session and does not adopt a failed login', async () => {
    const auth = TestBed.inject(AuthService);
    const harness = await RouterTestingHarness.create('/dashboard');
    expect(harness.routeNativeElement!.querySelector('.account b')!.textContent).toBe('Пользователь');
    auth.login({username: 'WrongUser', password: 'wrong'}).subscribe({error: () => {}});
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/auth/login').flush({}, {status: 401, statusText: 'Unauthorized'});
    expect(auth.username()).toBeNull();
    harness.detectChanges();
    expect(harness.routeNativeElement!.querySelector('.account b')!.textContent).toBe('Пользователь');
    http.verify();
  });
});
