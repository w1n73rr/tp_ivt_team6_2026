import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { routes } from '../../app.routes';
import { Dashboard } from './dashboard';
import { MaterialsApi } from '../materials/materials.api';
import { AuthService } from '../../core/auth/auth.service';
import { TESTS_SOURCE } from '../tests/tests-source';
import { of } from 'rxjs';
describe('Dashboard', () => {
  it('opens the upload form from the dashboard action', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dashboard', Dashboard);
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('.actions button')!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/materials/upload');
    expect(harness.routeNativeElement?.querySelector('h1')?.textContent).toBe('Загрузка материала');
  });
  beforeEach(() =>
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), { provide: TESTS_SOURCE, useValue: { demo: true, list: () => of({ items: [] }) } }] }),
  );
  it('redirects the entry route to login', async () => {
    await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/login');
  });
  it('opens material selection to create a test and keeps honest empty statistics', async () => {
    vi.spyOn(TestBed.inject(MaterialsApi), 'list').mockReturnValue(of({ items: [] }));
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dashboard', Dashboard);
    const root = harness.routeNativeElement!;
    expect(root.querySelector('h1')?.textContent).toBe('Здравствуйте!');
    expect(root.querySelectorAll('.stats article').length).toBe(3);
    root.querySelectorAll<HTMLButtonElement>('.actions button')[1].click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/materials');
  });
  it('opens tests from the training button without a weak-topics promise', async () => {
    vi.spyOn(TestBed.inject(AuthService), 'token', 'get').mockReturnValue('test-session');
    const h = await RouterTestingHarness.create('/dashboard');
    expect(h.routeNativeElement!.textContent).not.toContain('По вашим слабым темам');
    h.routeNativeElement!.querySelectorAll<HTMLButtonElement>('.actions button')[2].click(); await h.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/tests');
  });
});
