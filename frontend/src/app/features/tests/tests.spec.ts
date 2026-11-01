import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { TestsPage } from './tests';
import { TESTS_SOURCE, TestSummary } from './tests-source';
import { MaterialsShell } from '../materials/materials-shell';
import { App } from '../../app';
import { AuthService } from '../../core/auth/auth.service';
import { MatDialog } from '@angular/material/dialog';
import { TestDialogResult } from './test-dialog';

const item: TestSummary = { id: 'secret-internal-id', name: 'Мой тест', materialName: 'Моя лекция', format: 'PDF', questionCount: 10, difficulty: 'MEDIUM', questionType: 'MULTIPLE_CHOICE', createdAt: '2026-10-01T20:00:00Z' };
describe('Tests table prototype', () => {
  let source: { demo: boolean; list: ReturnType<typeof vi.fn>; setScenario: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    source = { demo: true, list: vi.fn(() => of({ items: [item] })), setScenario: vi.fn() };
    TestBed.configureTestingModule({ imports: [TestsPage, MaterialsShell], providers: [provideRouter([{ path: 'tests', component: TestsPage }]), provideHttpClient(), { provide: TESTS_SOURCE, useValue: source }] });
    vi.spyOn(TestBed.inject(AuthService), 'token', 'get').mockReturnValue('test-session');
  });
  it('renders agreed fields and hides the internal id', () => {
    const f = TestBed.createComponent(TestsPage); f.detectChanges(); const text = f.nativeElement.textContent;
    for (const field of ['Мой тест', 'Моя лекция', '10', 'Средняя', 'Несколько ответов', '01.10.2026']) expect(text).toContain(field);
    expect(text).not.toContain(item.id);
    expect(f.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(f.nativeElement.querySelector('a[aria-current=page]').textContent).toContain('Тесты');
  });
  it('keeps loading distinct from empty data', () => {
    const reply = new Subject<{ items: TestSummary[] }>(); source.list.mockReturnValue(reply);
    const f = TestBed.createComponent(TestsPage); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Загружаем тесты');
    expect(f.nativeElement.textContent).not.toContain('Тестов пока нет');
    reply.next({ items: [] }); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Тестов пока нет'); expect(f.nativeElement.querySelector('table')).toBeNull();
  });
  it('shows an error and permits an actual retry', () => {
    source.list.mockReturnValue(throwError(() => new Error()));
    const f = TestBed.createComponent(TestsPage); f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]').textContent).toContain('Не удалось загрузить');
    expect(f.nativeElement.querySelector('.state').textContent).not.toContain('Тестов пока нет');
    source.list.mockReturnValue(of({ items: [item] })); f.nativeElement.querySelector('.state button').click(); f.detectChanges();
    expect(source.list).toHaveBeenCalledTimes(2); expect(f.nativeElement.querySelectorAll('tbody tr')).toHaveLength(1);
  });
  it('shows login rather than an empty list on 401', () => {
    source.list.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    const f = TestBed.createComponent(TestsPage); f.detectChanges();
    expect(f.nativeElement.querySelector('a[href^="/login?"]')).toBeTruthy(); expect(f.nativeElement.querySelector('table')).toBeNull();
  });
  it('does not mutate data when the play placeholder is clicked', () => {
    const f = TestBed.createComponent(TestsPage); f.detectChanges();
    const buttons = [f.nativeElement.querySelector('.row-actions button')];
    for (const button of buttons) { button.click(); f.detectChanges(); expect(f.nativeElement.querySelector('[role=status]')).toBeTruthy(); }
    expect(f.componentInstance.items()).toEqual([item]); expect(source.list).toHaveBeenCalledTimes(1);
    expect(f.nativeElement.textContent).toContain('Прохождение тестов будет доступно');
  });
  it('cancels an old request when a new load begins', () => {
    const old = new Subject<{ items: TestSummary[] }>(); source.list.mockReturnValue(old);
    const f = TestBed.createComponent(TestsPage); f.detectChanges();
    source.list.mockReturnValue(of({ items: [] })); f.componentInstance.load(); old.next({ items: [item] });
    expect(f.componentInstance.state()).toBe('empty'); expect(f.componentInstance.items()).toEqual([]);
  });
  it('navigates to the page through the shared menu', async () => {
    const f = TestBed.createComponent(MaterialsShell); f.detectChanges();
    const harness = await RouterTestingHarness.create();
    f.nativeElement.querySelector('a[href="/tests"]').click(); await f.whenStable();
    expect(TestBed.inject(Router).url).toBe('/tests');
    expect(harness.routeNativeElement!.textContent).toContain('Мой тест');
  });
  it('uses one navigation shell without the legacy top navbar', async () => {
    const f = TestBed.createComponent(App); f.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/tests'); await f.whenStable(); f.detectChanges();
    expect(f.nativeElement.querySelector('app-navbar')).toBeNull();
    expect(f.nativeElement.querySelectorAll('app-materials-shell')).toHaveLength(1);
  });
  it('does not request the list without a session', () => {
    vi.spyOn(TestBed.inject(AuthService), 'token', 'get').mockReturnValue(null);
    const f = TestBed.createComponent(TestsPage); f.detectChanges();
    expect(source.list).not.toHaveBeenCalled(); expect(f.nativeElement.querySelector('table')).toBeNull();
  });
  it('keeps the chosen sort after rename and delete, including the final empty list', () => {
    let result = new Subject<TestDialogResult>();
    const f = TestBed.createComponent(TestsPage); f.detectChanges(); const page = f.componentInstance;
    const open = vi.spyOn(f.debugElement.injector.get(MatDialog), 'open').mockImplementation(() => ({ afterClosed: () => result }) as any);
    const other = { ...item, id: 'two', name: 'Я' }; page.items.set([item, other]); page.chooseSort('name:desc');
    page.action('rename', item); result.next({ action: 'rename', item: { ...item, name: 'ЯЯ' } });
    expect(open).toHaveBeenCalledTimes(1);
    expect(page.sort()).toBe('name:desc'); expect(page.sortedItems()[0].name).toBe('ЯЯ');
    result = new Subject(); page.action('delete', other); result.next({ action: 'delete', id: 'two' });
    result = new Subject(); page.action('delete', item); result.next({ action: 'delete', id: item.id });
    expect(page.state()).toBe('empty'); expect(page.sort()).toBe('name:desc'); expect(page.sortedItems()).toEqual([]);
  });
  it('searches only names without case, combines with sorting and leaves source data unchanged', () => {
    const f = TestBed.createComponent(TestsPage); f.detectChanges(); const p = f.componentInstance;
    const rows = [item, { ...item, id: 'two', name: 'МОЙ тест 2' }, { ...item, id: 'three', name: 'Другой', materialName: 'Мой тест' }];
    p.items.set(rows); p.query.set(' мой ТЕСТ '); p.chooseSort('name:desc');
    expect(p.visibleItems().map(row => row.id)).toEqual(['two', item.id]); expect(p.items()).toEqual(rows);
    p.query.set('ничего'); f.detectChanges(); expect(f.nativeElement.textContent).toContain('По вашему запросу тесты не найдены');
    expect(f.nativeElement.textContent).not.toContain('Тестов пока нет');
    f.nativeElement.querySelector('.state button').click(); expect(p.visibleItems()).toHaveLength(3); expect(p.sort()).toBe('name:desc');
    expect(source.list).toHaveBeenCalledTimes(1);
  });
});
