import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, combineLatest, map, of, startWith, Subject, switchMap, takeUntil } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { MaterialsShell } from '../materials/materials-shell';

type Question = {
  id: string;
  position: number;
  type: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  text: string;
  options: { id: string; text: string }[];
};
type AttemptDetail = {
  attempt: { status: string; quiz: { name: string } };
  questions: Question[];
  answers: { questionId: string; selectedOptionIds: string[] }[];
  currentPosition: number;
};
type State = { kind: 'loading' | 'error' | 'unavailable' } |
  { kind: 'ready'; title: string; question: Question; total: number; selected: string[] };

@Component({
  selector: 'app-attempt',
  imports: [MaterialsShell, RouterLink],
  templateUrl: './attempt.html',
  styleUrl: './attempt.scss',
})
export class AttemptPage {
  readonly state = signal<State>({ kind: 'loading' });
  readonly selected = signal<string[]>([]);
  private readonly retry = new Subject<void>();

  constructor() {
    const route = inject(ActivatedRoute);
    const http = inject(HttpClient);
    const auth = inject(AuthService);
    auth.sessionChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.state.set({ kind: 'unavailable' });
      this.selected.set([]);
    });
    combineLatest([route.paramMap, this.retry.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => http.get<AttemptDetail>(
        '/api/attempts/' + encodeURIComponent(params.get('attemptId')!),
        { headers: { Authorization: 'Bearer ' + auth.token } },
      ).pipe(
        map((detail): State => {
          const question = detail.questions.find(item => item.position === detail.currentPosition);
          if (detail.attempt.status !== 'IN_PROGRESS' || !question?.options.length) {
            return { kind: 'unavailable' };
          }
          const { id, position, type, text } = question;
          return {
            kind: 'ready',
            title: detail.attempt.quiz.name,
            total: detail.questions.length,
            question: { id, position, type, text, options: question.options.map(({ id, text }) => ({ id, text })) },
            selected: detail.answers.find(answer => answer.questionId === id)?.selectedOptionIds ?? [],
          };
        }),
        catchError((error: HttpErrorResponse) => of<State>({
          kind: [401, 403, 404, 410].includes(error.status) ? 'unavailable' : 'error',
        })),
        startWith<State>({ kind: 'loading' }),
      )),
      takeUntil(auth.sessionChanges),
      takeUntilDestroyed(),
    ).subscribe(state => {
      this.selected.set(state.kind === 'ready' ? state.selected : []);
      this.state.set(state);
    });
  }

  reload(): void {
    this.retry.next();
  }

  choose(id: string, multiple: boolean, checked: boolean): void {
    this.selected.update(ids => multiple
      ? checked ? [...new Set([...ids, id])] : ids.filter(value => value !== id)
      : [id]);
  }
}
