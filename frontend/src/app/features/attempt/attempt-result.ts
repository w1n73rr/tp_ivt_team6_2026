import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, combineLatest, finalize, map, of, startWith, Subject, switchMap, takeUntil } from 'rxjs';
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
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly routeChanged = new Subject<void>();
  readonly repeating = signal(false);
  readonly repeatError = signal(false);
  readonly activeAttempt = signal<string | null>(null);

  repeat(quizId: string): void {
    if (!this.auth.token || this.repeating() || this.activeAttempt()) return;
    this.repeatError.set(false);
    this.repeating.set(true);
    this.http.post<{ attempt: { id: string } }>('/api/quizzes/' + encodeURIComponent(quizId) + '/attempts', null, {
      observe: 'response', headers: { 'Idempotency-Key': crypto.randomUUID() },
    }).pipe(
      takeUntil(this.routeChanged),
      takeUntil(this.auth.sessionChanges),
      takeUntilDestroyed(this.destroyRef),
      finalize(() => this.repeating.set(false)),
    ).subscribe({
      next: response => {
        const id = response.body?.attempt?.id;
        if (!id) this.repeatError.set(true);
        else if (response.status === 200) this.activeAttempt.set(id);
        else this.openAttempt(id);
      },
      error: () => this.repeatError.set(true),
    });
  }

  openAttempt(id: string): void {
    const revision = this.auth.sessionRevision;
    this.activeAttempt.set(null);
    void this.router.navigate(['/attempts', id]).catch(() => false).then(ok => {
      if (!ok && revision === this.auth.sessionRevision && this.state().kind === 'ready') {
        this.repeatError.set(true);
      }
    });
  }

  readonly state = signal<State>({ kind: 'loading' });
  readonly retry = new Subject<void>();
  readonly formatDuration = formatDuration;
  readonly difficulties: Record<string, string> = { EASY: 'Лёгкая', MEDIUM: 'Средняя', HARD: 'Сложная' };
  readonly outcomes = { CORRECT: 'Верно', INCORRECT: 'Неверно', SKIPPED: 'Пропущено' };
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
    ).subscribe(state => {
      this.routeChanged.next();
      this.activeAttempt.set(null);
      this.repeatError.set(false);
      this.state.set(state);
    });
  }
}
