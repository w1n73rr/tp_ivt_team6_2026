import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, startWith, Subject, switchMap, takeUntil } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MaterialsShell } from '../materials/materials-shell';

interface Result {
  attemptId: string;
  quiz: { name: string };
  score: number;
  scorePercent: number;
  totalQuestions: number;
  correctCount: number;
  incorrectCount: number;
  skippedCount: number;
  durationSeconds: number;
}
type State = { kind: 'loading' | 'error' | 'unavailable' | 'unfinished' } |
  { kind: 'ready'; result: Result };

export function formatDuration(seconds: number): string {
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map(value => String(value).padStart(2, '0')).join(':');
}

@Component({
  imports: [MaterialsShell, RouterLink],
  template: `
    <app-materials-shell active="tests">
      <section class="result">
        <a routerLink="/tests">← К тестам</a>
        <h1>Результат теста</h1>
        @let view = state();
        @switch (view.kind) {
          @case ('ready') {
            <h2>{{ view.result.quiz.name }}</h2>
            <dl>
              <div><dt>Балл</dt><dd>{{ view.result.score }}</dd></div>
              <div><dt>Результат</dt><dd>{{ view.result.scorePercent }}%</dd></div>
              <div><dt>Правильные</dt><dd>{{ view.result.correctCount }}</dd></div>
              <div><dt>Неправильные</dt><dd>{{ view.result.incorrectCount }}</dd></div>
              <div><dt>Пропущенные</dt><dd>{{ view.result.skippedCount }}</dd></div>
              <div><dt>Всего вопросов</dt><dd>{{ view.result.totalQuestions }}</dd></div>
              <div><dt>Длительность</dt><dd>{{ formatDuration(view.result.durationSeconds) }}</dd></div>
            </dl>
          }
          @case ('loading') { <p role="status">Загружаем результат…</p> }
          @case ('unfinished') { <p>Попытка ещё не завершена.</p><a routerLink="..">К попытке</a> }
          @case ('unavailable') { <p>Результат не найден или недоступен.</p> }
          @case ('error') {
            <p role="alert">Не удалось загрузить результат.</p>
            <button type="button" (click)="retry.next()">Повторить</button>
          }
        }
      </section>
    </app-materials-shell>
  `,
  styles: `
    .result { max-width: 900px; margin: 20px auto; overflow-wrap: anywhere; }
    dl { display: grid; grid-template-columns: repeat(auto-fit, minmax(155px, 1fr)); gap: 12px; }
    dl div { background: white; padding: 16px; border: 1px solid #d4def0; border-radius: 10px; }
    dd { margin: 8px 0 0; font-size: 1.4rem; font-weight: 650; }
  `,
})
export class AttemptResultPage {
  readonly state = signal<State>({ kind: 'loading' });
  readonly retry = new Subject<void>();
  readonly formatDuration = formatDuration;

  constructor() {
    const auth = inject(AuthService);
    const route = inject(ActivatedRoute);
    const http = inject(HttpClient);
    auth.sessionChanges.pipe(takeUntilDestroyed()).subscribe(() => this.state.set({ kind: 'unavailable' }));
    combineLatest([route.paramMap, this.retry.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => {
        const id = params.get('attemptId');
        return http.get<Result>('/api/attempts/' + encodeURIComponent(id ?? '') + '/result').pipe(
          map(({ attemptId, quiz: { name }, score, scorePercent, totalQuestions, correctCount,
            incorrectCount, skippedCount, durationSeconds }): State => attemptId === id
            ? { kind: 'ready', result: { attemptId, quiz: { name }, score, scorePercent, totalQuestions,
              correctCount, incorrectCount, skippedCount, durationSeconds } }
            : { kind: 'unavailable' }),
          catchError((error: HttpErrorResponse) => of<State>({
            kind: error.status === 409 ? 'unfinished' :
              [401, 403, 404, 410].includes(error.status) ? 'unavailable' : 'error',
          })),
          startWith<State>({ kind: 'loading' }),
        );
      }),
      takeUntil(auth.sessionChanges),
      takeUntilDestroyed(),
    ).subscribe(state => this.state.set(state));
  }
}
