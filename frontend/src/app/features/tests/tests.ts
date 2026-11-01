import { Component, DestroyRef, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { MaterialsShell } from '../materials/materials-shell';
import { FigmaIcon } from '../materials/figma-icon';
import { DemoScenario, TESTS_SOURCE, TestSummary } from './tests-source';
import { AuthService } from '../../core/auth/auth.service';

@Component({ selector: 'app-tests', imports: [MaterialsShell, FigmaIcon, RouterLink, DatePipe],
  templateUrl: './tests.html', styleUrl: './tests.scss' })
export class TestsPage {
  readonly source = inject(TESTS_SOURCE);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  readonly state = signal<'loading' | 'list' | 'empty' | 'error' | 'auth'>('loading');
  readonly items = signal<TestSummary[]>([]);
  readonly notice = signal('');
  readonly scenario = signal<DemoScenario>('list');
  readonly difficulties = { EASY: 'Лёгкая', MEDIUM: 'Средняя', HARD: 'Сложная' };
  readonly types = { SINGLE_CHOICE: 'Single-choice', MULTIPLE_CHOICE: 'Multiple-choice', TRUE_FALSE: 'True/False' };
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
    const messages = { rename: 'Переименование будет подключено отдельной задачей', delete: 'Удаление будет подключено отдельной задачей', play: 'Прохождение тестов будет доступно в следующих спринтах' };
    this.notice.set(`${messages[action]}. Выбран тест «${item.name}».`);
  }
}
