import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, startWith, Subject, switchMap, takeUntil } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MaterialsShell } from '../materials/materials-shell';

interface Result {
  attemptId: string;
  quiz: { id: string; name: string; subjectName: string; difficulty: string; questionType: string };
  completedAt: string;
  items: QuestionResult[];
  score: number;
  scorePercent: number;
  totalQuestions: number;
  correctCount: number;
  incorrectCount: number;
  skippedCount: number;
  durationSeconds: number;
}
interface QuestionResult {
  question: { id: string; position: number; text: string; options: { id: string; text: string }[] };
  selectedOptionIds: string[];
  correctOptionIds: string[];
  outcome: 'CORRECT' | 'INCORRECT' | 'SKIPPED';
  score: number;
}
type State = { kind: 'loading' | 'error' | 'unavailable' | 'unfinished' } |
  { kind: 'ready'; result: Result };

export function formatDuration(seconds: number): string {
  return [Math.floor(seconds / 3600), Math.floor(seconds / 60) % 60, seconds % 60]
    .map(value => String(value).padStart(2, '0')).join(':');
}

@Component({
  imports: [MaterialsShell, RouterLink],
  templateUrl: './attempt-result.html',
  styleUrl: './attempt-result.scss',
})
export class AttemptResultPage {
  readonly state = signal<State>({ kind: 'loading' });
  readonly retry = new Subject<void>();
  readonly formatDuration = formatDuration;
  readonly difficulties: Record<string, string> = { EASY: 'Лёгкая', MEDIUM: 'Средняя', HARD: 'Сложная' };
  readonly types: Record<string, string> = {
    SINGLE_CHOICE: 'Один правильный ответ', MULTIPLE_CHOICE: 'Несколько правильных ответов', TRUE_FALSE: 'Верно / неверно',
  };

  answers(item: QuestionResult, ids: string[]): string[] {
    return item.question.options.filter(option => ids.includes(option.id)).map(option => option.text);
  }

  formatDate(value: string): string {
    return value ? new Intl.DateTimeFormat('ru-RU', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)) : '—';
  }

  constructor() {
    const auth = inject(AuthService);
    const route = inject(ActivatedRoute);
    const http = inject(HttpClient);
    auth.sessionChanges.pipe(takeUntilDestroyed()).subscribe(() => this.state.set({ kind: 'unavailable' }));
    combineLatest([route.paramMap, this.retry.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => {
        const id = params.get('attemptId');
        return http.get<Result>('/api/attempts/' + encodeURIComponent(id ?? '') + '/result').pipe(
          map((result): State => result.attemptId === id
            ? { kind: 'ready', result: { ...result, items: [...(result.items ?? [])]
              .sort((a, b) => a.question.position - b.question.position) } }
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
