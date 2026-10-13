import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { provideHttpClient } from '@angular/common/http';
import { routes } from '../../app.routes';
import { Dashboard } from './dashboard';
describe('Dashboard', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient()] }),
  );
  it('redirects the entry route to login', async () => {
    await RouterTestingHarness.create('/');
    expect(TestBed.inject(Router).url).toBe('/login');
  });
  it('renders the main screen with honest empty statistics and explains unavailable actions', async () => {
    const harness = await RouterTestingHarness.create();
    await harness.navigateByUrl('/dashboard', Dashboard);
    const root = harness.routeNativeElement!;
    expect(root.querySelector('h1')?.textContent).toBe('Здравствуйте!');
    expect(root.querySelectorAll('.stats article').length).toBe(3);
    root.querySelector<HTMLButtonElement>('.actions button')!.click();
    harness.detectChanges();
    expect(root.querySelector('[role=status]')?.textContent).toContain(
      'Загрузить материал: раздел пока в разработке',
    );
    expect(TestBed.inject(Router).url).toBe('/dashboard');
  });
});
