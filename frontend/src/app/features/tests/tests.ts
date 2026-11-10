import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { TestDialog, TestDialogResult } from './test-dialog';
import { TEST_SORTS, TestSort, sortTests } from './tests-sort';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
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
  private readonly dialogs = inject(MatDialog);
  private dialogOpen = false;
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
    if (action === 'play') { this.notice.set('Прохождение тестов будет доступно в следующих спринтах.'); return; }
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
}
