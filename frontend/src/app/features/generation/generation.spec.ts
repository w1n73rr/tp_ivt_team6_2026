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
import { Materials } from '../materials/materials';

const material: Material = { id: 'one', name: 'Нормализация', format: 'PDF', sizeBytes: 2516582, subjectId: 'db', subjectName: 'Базы данных', uploadedAt: '2026-10-01' };
const options: GenerationOptions = { counts: [5, 10], difficulties: ['EASY', 'MEDIUM', 'HARD'], types: ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'], defaults: { questionCount: 10, difficulty: 'MEDIUM', questionType: 'MULTIPLE_CHOICE' } };
describe('Generation prototype', () => {
  let source: { demo: boolean; options: ReturnType<typeof vi.fn>; start: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    source = { demo: true, options: vi.fn(() => of(structuredClone(options))), start: vi.fn(() => of({ state: 'DEMO_WAITING' })) };
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([
      { path: 'materials', component: Materials }, { path: 'materials/:materialId/generate', component: Generation },
    ]), { provide: GENERATION_SOURCE, useValue: source }] });
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
  it('handles unavailable settings and allows retry without losing the route', async () => {
    source.options.mockReturnValue(throwError(() => new Error()));
    const { page, harness } = await open(); expect(page.state()).toBe('error');
    source.options.mockReturnValue(of(options)); page.retry(); harness.detectChanges();
    expect(page.state()).toBe('settings'); expect(page.material()!.id).toBe('one');
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
});
