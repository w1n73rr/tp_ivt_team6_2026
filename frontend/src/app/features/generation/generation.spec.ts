import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, Subject, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Generation } from './generation';
import { GENERATION_SOURCE, GenerationOptions } from './generation-source';
import { MaterialsApi } from '../materials/materials.api';
import { Material } from '../materials/materials.models';
import { AuthService } from '../../core/auth/auth.service';
import { Materials } from '../materials/materials';

const material: Material = { id: 'one', name: 'Нормализация', format: 'PDF', sizeBytes: 2516582, subjectId: 'db', subjectName: 'Базы данных', uploadedAt: '2026-10-01' };
const options: GenerationOptions = { counts: [5, 10], difficulties: ['EASY', 'MEDIUM', 'HARD'], types: ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'], defaults: { questionCount: 10, difficulty: 'MEDIUM', questionType: 'MULTIPLE_CHOICE' } };
describe('Generation prototype', () => {
  let source: { demo: boolean; options: ReturnType<typeof vi.fn>; start: ReturnType<typeof vi.fn>; status: ReturnType<typeof vi.fn>; saved: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    sessionStorage.clear();
    source = { status: vi.fn(), saved: vi.fn(), demo: true, options: vi.fn(() => of(structuredClone(options))), start: vi.fn(() => of({ state: 'DEMO_WAITING' })) };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([
      { path: 'materials', component: Materials }, { path: 'materials/:materialId/generate', component: Generation },
    ]), { provide: GENERATION_SOURCE, useValue: source }, { provide: AuthService, useValue: { token: 'test', username: () => 'owner' } }] });
    vi.spyOn(TestBed.inject(MaterialsApi), 'detail').mockImplementation(id => of({ material: { ...material, id }, tags: [] }));
    vi.spyOn(TestBed.inject(MaterialsApi), 'list').mockReturnValue(of({ items: [material] }));
  });
  async function open() {
    const harness = await RouterTestingHarness.create();
    const page = await harness.navigateByUrl('/materials/one/generate', Generation);
    harness.detectChanges(); return { harness, page };
  }
  it('opens the exact material from the actual list action', async () => {
    const harness = await RouterTestingHarness.create('/materials');
    const buttons = Array.from(harness.routeNativeElement!.querySelectorAll('article button')) as HTMLButtonElement[];
    buttons.find(button => button.textContent!.includes('Создать тест'))!.click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/materials/one/generate');
    harness.detectChanges();
    expect(TestBed.inject(MaterialsApi).detail).toHaveBeenCalledWith('one');
    expect(harness.routeNativeElement!.textContent).toContain('Нормализация');
    expect(harness.routeNativeElement!.textContent).toContain('PDF');
  });
  it('selects exactly one type, both counts and all difficulties and sends the actual settings', async () => {
    const { harness, page } = await open();
    for (const count of options.counts) for (const difficulty of options.difficulties) for (const type of options.types) {
      page.choose({ questionCount: count, difficulty, questionType: type }); harness.detectChanges();
      expect(harness.routeNativeElement!.querySelectorAll('input[name=type]:checked').length).toBe(1);
      expect(harness.routeNativeElement!.querySelector('.summary')!.textContent).toContain(`${count} вопросов`);
      expect(harness.routeNativeElement!.querySelector('.summary')!.textContent).toContain(page.summaries[difficulty]);
    }
    page.start(); harness.detectChanges();
    expect(source.start).toHaveBeenCalledWith('one', { questionCount: 10, difficulty: 'HARD', questionType: 'TRUE_FALSE' });
    expect(harness.routeNativeElement!.textContent).toContain('Автоматического завершения и сохранения теста нет');
    expect(harness.routeNativeElement!.textContent).not.toContain('%');
    expect(harness.routeNativeElement!.textContent).not.toContain('К настройкам');
  });
  it('starts a real request on HTTP when randomUUID is unavailable', async () => {
    source.demo = false;
    source.start.mockReturnValue(new Subject());
    const { page } = await open();
    vi.stubGlobal('crypto', { getRandomValues: crypto.getRandomValues.bind(crypto) });
    try {
      page.start();
      expect(source.start).toHaveBeenCalledWith('one', options.defaults, expect.any(String),
        expect.stringMatching(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/));
    } finally {
      vi.unstubAllGlobals();
    }
  });
  it('blocks double submission and restores editable settings on failure', async () => {
    const sending = new Subject(); source.start.mockReturnValue(sending);
    const { page, harness } = await open(); page.start(); page.start(); harness.detectChanges();
    expect(source.start).toHaveBeenCalledTimes(1);
    expect(harness.routeNativeElement!.querySelector<HTMLButtonElement>('.primary')!.disabled).toBe(true);
    sending.error(new Error()); harness.detectChanges();
    expect(page.state()).toBe('settings'); expect(harness.routeNativeElement!.querySelector('[role=alert]')).toBeTruthy();
    expect(page.settings()!.questionCount).toBe(10);
  });
  it('preserves drafts when returning and does not reuse them for another material', async () => {
    const { harness, page } = await open(); page.choose({ questionCount: 5, questionType: 'TRUE_FALSE' });
    await harness.navigateByUrl('/materials');
    const returned = await harness.navigateByUrl('/materials/one/generate', Generation);
    expect(returned.settings()!.questionCount).toBe(5);
    await harness.navigateByUrl('/materials/two/generate', Generation);
    expect(returned.material()!.id).toBe('two'); expect(returned.settings()!.questionCount).toBe(10);
  });
  it('rejects a substituted material', async () => {
    vi.mocked(TestBed.inject(MaterialsApi).detail).mockReturnValue(of({ material: { ...material, id: 'other' }, tags: [] }));
    const { page } = await open(); page.start(); expect(page.state()).toBe('error'); expect(source.start).not.toHaveBeenCalled();
  });
  it('validates the name and preserves it through demo failure and success', async () => {
    const { page } = await open();
    page.setName('   '); page.start(); expect(source.start).not.toHaveBeenCalled();
    page.setName('x'.repeat(101)); page.start(); expect(source.start).not.toHaveBeenCalled();
    page.setName(' Мой тест '); page.start(); page.demoResult(false);
    expect(page.state()).toBe('failed'); page.backToSettings(); expect(page.name()).toBe('Мой тест');
    page.start(); page.demoResult(true); expect(page.state()).toBe('ready'); expect(page.name()).toBe('Мой тест');
  });
  it('rejects invalid settings before sending', async () => {
    const { page } = await open();
    page.settings.set({ ...options.defaults, questionCount: 7 as 5 }); page.start();
    expect(source.start).not.toHaveBeenCalled(); expect(page.error()).toContain('допустимые');
  });
  it('preserves settings on repeated demo failure, supports retry and rejects a zero result', async () => {
    const { page } = await open(); page.setName('Повтор'); page.choose({ questionCount: 10, difficulty: 'HARD', questionType: 'TRUE_FALSE' });
    page.start(); page.demoResult(false); page.retryGeneration();
    expect(source.start).toHaveBeenLastCalledWith('one', { questionCount: 10, difficulty: 'HARD', questionType: 'TRUE_FALSE' });
    expect(page.state()).toBe('waiting'); page.demoResult(false); page.backToSettings(); expect(page.name()).toBe('Повтор');
    page.start(); page.demoResult(true, 0); expect(page.state()).toBe('failed');
    page.retryGeneration(); page.demoResult(true, 3); expect(page.actualCount()).toBe(3); expect(page.state()).toBe('failed');
  });
  it('handles unavailable settings and allows retry without losing the route', async () => {
    source.options.mockReturnValue(throwError(() => new Error()));
    const { page, harness } = await open(); expect(page.state()).toBe('error');
    source.options.mockReturnValue(of(options)); page.retry(); harness.detectChanges();
    expect(page.state()).toBe('settings'); expect(page.material()!.id).toBe('one');
  });
  it('shows a disabled test action and an available list link after generation', async () => {
    const { page, harness } = await open();
    page.start(); page.demoResult(true); harness.detectChanges();
    const root = harness.routeNativeElement!;
    const button = Array.from(root.querySelectorAll('button')).find(item => item.textContent?.trim() === 'Перейти к тесту');
    expect(button?.disabled).toBe(true);
    expect(Array.from(root.querySelectorAll('a')).some(item => item.textContent?.trim() === 'Все тесты')).toBe(true);
  });
  it('handles empty settings without showing an editable form', async () => {
    source.options.mockReturnValue(of({ ...options, types: [] }));
    const { page, harness } = await open(); expect(page.state()).toBe('error');
    expect(harness.routeNativeElement!.textContent).toContain('Нет доступных параметров');
    expect(harness.routeNativeElement!.querySelector('.primary')).toBeNull();
  });
  it.each([401, 404, 500])('shows a material load error for HTTP %i', async status => {
    vi.mocked(TestBed.inject(MaterialsApi).detail).mockReturnValue(throwError(() => new HttpErrorResponse({ status })));
    const { page, harness } = await open(); page.start();
    expect(harness.routeNativeElement!.querySelector('[role=alert]')).toBeTruthy(); expect(source.start).not.toHaveBeenCalled();
  });
  it('keeps the same key on an unknown POST outcome and creates a new key only for a new request', async () => {
    source.demo = false; source.start.mockReturnValue(throwError(() => new Error('network')));
    const { page } = await open(); page.start(); page.start();
    expect(source.start.mock.calls[0][3]).toBe(source.start.mock.calls[1][3]);
    expect(source.start.mock.calls[0][2]).toBe('Нормализация');
    page.setName('Другое название'); page.start();
    expect(source.start.mock.calls[2][3]).not.toBe(source.start.mock.calls[0][3]);
  });
  it('retains waiting after a temporary status error and confirms the saved test before ready', async () => {
    source.demo = false;
    const job = { id: 'job', materialId: 'one', status: 'QUEUED', version: 0 };
    source.start.mockReturnValue(of(job)); source.status.mockReturnValue(throwError(() => new Error('network')));
    const { page, harness } = await open(); vi.useFakeTimers();
    try {
      page.start(); await vi.advanceTimersByTimeAsync(1);
      expect(page.state()).toBe('waiting'); expect(page.connectionError()).toContain('статус');
      source.status.mockReturnValue(of({ ...job, status: 'COMPLETED', version: 2, quizId: 'quiz' }));
      source.saved.mockReturnValue(throwError(() => new Error('list unavailable')));
      await vi.advanceTimersByTimeAsync(2000); expect(page.state()).toBe('waiting');
      source.saved.mockReturnValue(of({ id: 'quiz', name: 'Мой тест', questionCount: 10 }));
      await vi.advanceTimersByTimeAsync(2000);
      expect(page.state()).toBe('ready'); expect(page.quizId()).toBe('quiz'); expect(page.actualCount()).toBe(10);
      harness.detectChanges(); expect(harness.routeNativeElement!.textContent).not.toContain('демонстрация');
      expect(source.start).toHaveBeenCalledTimes(1);
    } finally { vi.useRealTimers(); }
  });
  it('restores the accepted job after leaving and returning without a duplicate POST', async () => {
    source.demo = false;
    const job = { id: 'job', materialId: 'one', status: 'QUEUED', version: 0 };
    source.start.mockReturnValue(of(job)); source.status.mockReturnValue(of(job));
    const { page, harness } = await open(); page.start();
    await harness.navigateByUrl('/materials');
    const restored = await harness.navigateByUrl('/materials/one/generate', Generation);
    expect(restored.state()).toBe('waiting'); expect(source.start).toHaveBeenCalledTimes(1);
  });
  it('uses a new key after terminal failure and retains settings and name', async () => {
    source.demo = false;
    const job = { id: 'job', materialId: 'one', status: 'QUEUED', version: 0 };
    source.start.mockReturnValue(of(job)); source.status.mockReturnValue(of({ ...job, status: 'FAILED', version: 1 }));
    const { page } = await open(); vi.useFakeTimers();
    try {
      page.setName('Повтор'); page.start(); await vi.advanceTimersByTimeAsync(1);
      expect(page.state()).toBe('failed'); page.retryGeneration();
      expect(source.start.mock.calls[1][3]).not.toBe(source.start.mock.calls[0][3]);
      expect(source.start.mock.calls[1][2]).toBe('Повтор');
    } finally { vi.useRealTimers(); }
  });

  it('stops HTTP polling on 401 without marking the accepted generation failed', async () => {
    source.demo = false;
    source.start.mockReturnValue(of({ id: 'job', materialId: 'one', status: 'QUEUED', version: 0 }));
    source.status.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const { page, harness } = await open(); vi.useFakeTimers();
    try {
      page.start(); await vi.advanceTimersByTimeAsync(1);
      expect(page.authRequired()).toBe(true); expect(page.state()).toBe('waiting');
      await vi.advanceTimersByTimeAsync(6000); expect(source.status).toHaveBeenCalledTimes(1);
      expect(sessionStorage.getItem('generation:owner:one')).toContain('job');
      harness.detectChanges(); expect(harness.routeNativeElement!.textContent).toContain('Войти и вернуться');
    } finally { vi.useRealTimers(); }
  });

  it('rejects a saved result with fewer questions than requested', async () => {
    source.demo=false;
    const job={id:'job',materialId:'one',status:'QUEUED',version:0};
    source.start.mockReturnValue(of(job));
    source.status.mockReturnValue(of({...job,status:'COMPLETED',version:1,quizId:'quiz'}));
    source.saved.mockReturnValue(of({id:'quiz',name:'Неполный',questionCount:3}));
    const {page}=await open(); vi.useFakeTimers();
    try { page.start(); await vi.advanceTimersByTimeAsync(1); expect(page.state()).toBe('failed'); expect(page.quizId()).toBeNull(); expect(page.failureMessage()).toContain('количество'); }
    finally { vi.useRealTimers(); }
  });

});
