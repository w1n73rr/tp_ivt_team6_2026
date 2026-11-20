import { Component, DestroyRef, computed, inject, signal, TemplateRef, viewChild } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TestDialog, TestDialogResult } from './test-dialog';
import { TEST_SORTS, TestSort, sortTests } from './tests-sort';
import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize, firstValueFrom, Subscription, takeUntil } from 'rxjs';
import { MaterialsShell } from '../materials/materials-shell';
import { FigmaIcon } from '../materials/figma-icon';
import { DemoScenario, TESTS_SOURCE, TestSummary } from './tests-source';
import { AuthService } from '../../core/auth/auth.service';

@Component({ selector: 'app-tests', imports: [MaterialsShell, FigmaIcon, RouterLink, DatePipe, MatDialogModule],
  templateUrl: './tests.html', styleUrl: './tests.scss' })
export class TestsPage {
  readonly source = inject(TESTS_SOURCE);
  readonly selectedTest = inject(ActivatedRoute).snapshot.queryParamMap.get('test');
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  readonly startingTest = signal<string | null>(null);
  private readonly dialogs = inject(MatDialog);
  private dialogOpen = false;
  private readonly resumeDialog = viewChild.required<TemplateRef<unknown>>('resumeDialog');
  readonly state = signal<'loading' | 'list' | 'empty' | 'error' | 'auth'>('loading');
  readonly items = signal<TestSummary[]>([]);
  readonly sort = signal<TestSort>('name:asc');
  readonly sortedItems = computed(() => sortTests(this.items(), this.sort()));
  readonly query = signal('');
  readonly visibleItems = computed(() => {
    const query = this.query().trim().toLocaleLowerCase('ru');
    return this.sortedItems().filter(item => item.name.toLocaleLowerCase('ru').includes(query));
  });
  chooseSort(value: string): void { if (TEST_SORTS.includes(value as TestSort)) this.sort.set(value as TestSort); }
  ariaSort(key: string): string { return this.sort().startsWith(key + ':') ? this.sort().endsWith(':asc') ? 'ascending' : 'descending' : 'none'; }
  readonly notice = signal('');
  readonly scenario = signal<DemoScenario>('list');
  readonly difficulties = { EASY: 'Лёгкая', MEDIUM: 'Средняя', HARD: 'Сложная' };
  readonly types = { SINGLE_CHOICE: 'Один ответ', MULTIPLE_CHOICE: 'Несколько ответов', TRUE_FALSE: 'Верно / неверно' };
  constructor() { this.load(); }
  load(): void {
    this.request?.unsubscribe(); this.items.set([]); this.notice.set(''); this.state.set('loading');
    if (!this.auth.token?.trim()) { this.state.set('auth'); return; }
    this.request = this.source.list().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: response => {
        if (!response || !Array.isArray(response.items)) { this.state.set('error'); return; }
        this.items.set(response.items); this.state.set(response.items.length ? 'list' : 'empty');
      },
      error: error => this.state.set(error instanceof HttpErrorResponse && error.status === 401 ? 'auth' : 'error'),
    });
  }
  demo(scenario: DemoScenario): void {
    if (!this.source.demo || !this.source.setScenario) return;
    this.scenario.set(scenario); this.source.setScenario(scenario); this.load();
  }
  action(action: 'rename' | 'delete' | 'play', item: TestSummary): void {
    if (action === 'play') { this.start(item.id); return; }
    if (this.dialogOpen || this.state() !== 'list') return;
    this.dialogOpen = true;
    this.dialogs.open<TestDialog, { action: 'rename' | 'delete'; item: TestSummary }, TestDialogResult>(TestDialog, { data: { action, item }, width: '640px', maxWidth: 'calc(100vw - 32px)', autoFocus: action === 'rename' ? '#test-rename' : 'first-tabbable' }).afterClosed().pipe(takeUntilDestroyed(this.destroyRef)).subscribe(result => {
      this.dialogOpen = false;
      if (!result) return;
      this.items.update(rows => result.action === 'rename' ? rows.map(row => row.id === result.item.id ? result.item : row) : rows.filter(row => row.id !== result.id));
      this.state.set(this.items().length ? 'list' : 'empty');
      this.notice.set(result.action === 'rename' ? 'Название теста изменено.' : 'Тест удалён.');
    });
  }

  private start(quizId: string): void {
    if (this.dialogOpen || this.startingTest() || this.state() !== 'list' || this.source.demo) return;
    if (!this.auth.token) {
      this.state.set('auth');
      return;
    }
    const revision = this.auth.sessionRevision;
    this.notice.set('');
    this.startingTest.set(quizId);
    this.http.post<{ attempt: { id: string } }>(
      '/api/quizzes/' + encodeURIComponent(quizId) + '/attempts',
      null,
      { observe: 'response', headers: { 'Idempotency-Key': crypto.randomUUID() } },
    ).pipe(
      takeUntil(this.auth.sessionChanges),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.startingTest.set(null)),
    ).subscribe({
      next: async response => {
        if (revision !== this.auth.sessionRevision) return;
        const id = response.body?.attempt?.id;
        if (id && response.status === 200) {
          this.dialogOpen = true;
          const dialog = this.dialogs.open(this.resumeDialog(), { width: '440px', autoFocus: 'first-tabbable' });
          const confirmed = await firstValueFrom(dialog.afterClosed(), { defaultValue: false });
          this.dialogOpen = false;
          if (!confirmed || revision !== this.auth.sessionRevision) return;
        }
        if (id && await this.router.navigate(['/attempts', id]).catch(() => false)) return;
        if (revision === this.auth.sessionRevision) this.notice.set('Не удалось открыть попытку. Попробуйте ещё раз.');
      },
      error: (error: HttpErrorResponse) => {
        if (error.status === 401) {
          this.state.set('auth');
          return;
        }
        this.notice.set(error.status === 403 || error.status === 404
          ? 'Тест недоступен.'
          : 'Не удалось запустить тест. Попробуйте ещё раз.');
      },
    });
  }
}
