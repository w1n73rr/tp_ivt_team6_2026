import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MatDialog } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { routes } from '../../app.routes';
import { AuthService } from './auth.service';
import { sessionInterceptor } from './session-interceptor';
import { PendingMaterial } from '../../features/materials/pending-material';
import { GenerationDrafts } from '../../features/generation/generation';

const materialId = 'b32b20bc-143c-4a72-a833-2797896e3926';
const unsafeName = '<img src=x onerror=alert(1)>Личный тест';
const quiz = { id: materialId, name: unsafeName, materialName: '<script>private()</script>', format: 'TXT', questionCount: 5,
  difficulty: 'EASY', questionType: 'SINGLE_CHOICE', createdAt: '2026-11-18T00:00:00Z' };

describe('Private session boundary', () => {
  let http: HttpTestingController;
  let auth: AuthService;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(withInterceptors([sessionInterceptor])), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); vi.useRealTimers(); });
  function login(username = 'A', token = 'fixture-jwt-A'): void {
    auth.login({ username, password: 'password' }).subscribe();
    const request = http.expectOne('/api/auth/login');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ token });
  }
  async function openTests() {
    const harness = await RouterTestingHarness.create('/tests');
    http.expectOne('/api/quizzes').flush({ items: [quiz] });
    harness.detectChanges();
    return harness;
  }
  it.each(['/dashboard', '/materials', '/materials/upload', '/tests', `/materials/${materialId}/generate`])('blocks direct URL %s before any private request', async path => {
    const harness = await RouterTestingHarness.create(path);
    expect(TestBed.inject(Router).url).toBe('/login?returnTo=' + encodeURIComponent(path));
    expect(harness.routeNativeElement!.querySelector('table')).toBeNull();
    http.expectNone(request => request.url.startsWith('/api/'));
  });
  it('renders untrusted names as text and closes an editing dialog on logout', async () => {
    login();
    const harness = await openTests();
    expect(harness.routeNativeElement!.textContent).toContain(unsafeName);
    expect(harness.routeNativeElement!.querySelector('img, script')).toBeNull();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('button[title="Переименовать"]')!.click();
    await harness.fixture.whenStable();
    const dialogs = TestBed.inject(MatDialog);
    expect(dialogs.openDialogs).toHaveLength(1);
    const closed = firstValueFrom(dialogs.openDialogs[0].afterClosed());
    auth.logout(); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).not.toContain(unsafeName);
    await closed;
    await harness.fixture.whenStable();
    expect(dialogs.openDialogs).toHaveLength(0);
    expect(TestBed.inject(Router).url).toBe('/login');
    await harness.navigateByUrl('/tests');
    expect(TestBed.inject(Router).url).toContain('/login');
    http.expectNone('/api/quizzes');
  });
  it('cancels the old HTTP request and loads only the next account after logout', async () => {
    login();
    const harness = await RouterTestingHarness.create('/tests');
    const old = http.expectOne('/api/quizzes');
    expect(old.request.headers.get('Authorization')).toBe('Bearer fixture-jwt-A');
    auth.logout();
    expect(old.cancelled).toBe(true);
    expect(() => old.flush({ items: [quiz] })).toThrow();
    await harness.fixture.whenStable();
    login('B', 'fixture-jwt-B');
    await harness.navigateByUrl('/tests');
    const fresh = http.expectOne('/api/quizzes');
    expect(fresh.request.headers.get('Authorization')).toBe('Bearer fixture-jwt-B');
    fresh.flush({ items: [] }); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).toContain('Тестов пока нет');
    expect(harness.routeNativeElement!.textContent).not.toContain(unsafeName);
  });
  it('clears a loaded private screen on account replacement without an intermediate logout', async () => {
    login(); const harness = await openTests();
    login('B', 'fixture-jwt-B'); harness.detectChanges();
    expect(harness.routeNativeElement!.textContent).not.toContain(unsafeName);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
  });
  it('cancels an in-flight rename when the session ends and closes the busy dialog', async () => {
    login(); const harness = await openTests();
    harness.routeNativeElement!.querySelector<HTMLButtonElement>('button[title="Переименовать"]')!.click();
    await harness.fixture.whenStable();
    const input = document.querySelector<HTMLInputElement>('#test-rename')!;
    input.value = 'Не сохранять после выхода'; input.dispatchEvent(new Event('input'));
    document.querySelector<HTMLFormElement>('app-test-dialog form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne('/api/quizzes/' + materialId);
    const dialogs = TestBed.inject(MatDialog), closed = firstValueFrom(dialogs.openDialogs[0].afterClosed());
    auth.logout(); expect(request.cancelled).toBe(true);
    await closed; await harness.fixture.whenStable();
    expect(document.querySelector('app-test-dialog')).toBeNull();
    expect(harness.routeNativeElement!.textContent).not.toContain(unsafeName);
  });
  it('treats protected API 401 as the end of the session and cancels other requests', async () => {
    login(); const harness = await RouterTestingHarness.create('/tests');
    const client = TestBed.inject(HttpClient);
    client.get('/api/materials').subscribe({ error: () => {} });
    const pending = http.expectOne('/api/materials');
    http.expectOne('/api/quizzes').flush({ message: '<script>internal error</script>' }, { status: 401, statusText: 'Unauthorized' });
    expect(auth.token).toBeNull(); expect(pending.cancelled).toBe(true);
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/login');
    expect(harness.routeNativeElement!.textContent).not.toContain('internal error');
  });
  it.each([403, 404])('keeps %i distinct from expired authentication and hides server details', async status => {
    login(); const harness = await RouterTestingHarness.create('/tests');
    http.expectOne('/api/quizzes').flush({ message: 'Чужое секретное название' }, { status, statusText: 'Unavailable' });
    harness.detectChanges();
    expect(auth.token).toBe('fixture-jwt-A');
    expect(harness.routeNativeElement!.textContent).toContain('Не удалось загрузить тесты');
    expect(harness.routeNativeElement!.textContent).not.toContain('Чужое секретное название');
  });
  it('never attaches a JWT to external URLs or sends private requests without a session', () => {
    const client = TestBed.inject(HttpClient); const failed = vi.fn();
    client.get('/api/quizzes').subscribe({ error: failed });
    expect(failed.mock.calls[0][0]).toBeInstanceOf(HttpErrorResponse); http.expectNone('/api/quizzes');
    login();
    client.get('https://example.com/api/data').subscribe();
    const external = http.expectOne('https://example.com/api/data');
    expect(external.request.headers.has('Authorization')).toBe(false); external.flush({});
  });
  it('removes private drafts and pending files synchronously while keeping unrelated storage', () => {
    login();
    const drafts = TestBed.inject(GenerationDrafts), pending = TestBed.inject(PendingMaterial);
    drafts.names.set(materialId, 'Личное название');
    drafts.values.set(materialId, { questionCount: 5, difficulty: 'EASY', questionType: 'TRUE_FALSE' });
    pending.file = new File(['Секретный текст'], 'lecture.txt');
    sessionStorage.setItem('generation:A:' + materialId, 'private draft');
    sessionStorage.setItem('unrelated-key', 'keep');
    auth.logout();
    expect(drafts.names.size).toBe(0); expect(drafts.values.size).toBe(0); expect(pending.file).toBeNull();
    expect(Object.keys(sessionStorage)).toEqual(['unrelated-key']);
  });
  it('expires a server JWT and removes the private snapshot without waiting for an API call', () => {
    vi.useFakeTimers();
    const token = 'header.' + btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + 2 })) + '.signature';
    login('A', token); sessionStorage.setItem('generation:A:one', 'private');
    vi.advanceTimersByTime(2100);
    expect(auth.token).toBeNull(); expect(auth.username()).toBeNull(); expect(sessionStorage.length).toBe(0);
  });
  it('does not let a late login response restore a session after logout', () => {
    const failed = vi.fn();
    auth.login({ username: 'A', password: 'password' }).subscribe({ error: failed });
    const request = http.expectOne('/api/auth/login'); auth.logout(); request.flush({ token: 'old-token' });
    expect(failed).toHaveBeenCalledOnce(); expect(auth.token).toBeNull(); expect(auth.username()).toBeNull();
  });
});

describe('Expired tab session', () => {
  afterEach(() => sessionStorage.clear());
  it('cleans an expired saved JWT before the first private route', async () => {
    const token = 'header.' + btoa(JSON.stringify({ exp: 1 })) + '.signature';
    sessionStorage.setItem('exam-ai-session', JSON.stringify({ token, username: 'A' }));
    sessionStorage.setItem('generation:A:one', 'private');
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient()] });
    await RouterTestingHarness.create('/materials');
    expect(TestBed.inject(AuthService).token).toBeNull();
    expect(TestBed.inject(Router).url).toBe('/login?returnTo=%2Fmaterials');
    expect(sessionStorage.length).toBe(0);
  });
});
