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
  { kind: 'ready'; title: string; questions: Question[]; index: number; answers: Record<string, string[]> };

@Component({
  selector: 'app-attempt',
  imports: [MaterialsShell, RouterLink],
  templateUrl: './attempt.html',
  styleUrl: './attempt.scss',
})
export class AttemptPage {
  readonly state = signal<State>({ kind: 'loading' });
  readonly selected = signal<string[]>([]);
  readonly reviewing = signal(false);
  private readonly retry = new Subject<void>();

  constructor() {
    const route = inject(ActivatedRoute);
    const http = inject(HttpClient);
    const auth = inject(AuthService);
    auth.sessionChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      this.state.set({ kind: 'unavailable' });
      this.selected.set([]);
      this.reviewing.set(false);
    });
    combineLatest([route.paramMap, this.retry.pipe(startWith(undefined))]).pipe(
      switchMap(([params]) => http.get<AttemptDetail>(
        '/api/attempts/' + encodeURIComponent(params.get('attemptId')!),
        { headers: { Authorization: 'Bearer ' + auth.token } },
      ).pipe(
        map((detail): State => {
          const questions = [...detail.questions].sort((a, b) => a.position - b.position)
            .map(({ id, position, type, text, options }) => ({
              id, position, type, text, options: options.map(({ id, text }) => ({ id, text })),
            }));
          const index = questions.findIndex(question => question.position === detail.currentPosition);
          if (detail.attempt.status !== 'IN_PROGRESS' || index < 0 || questions.some(q => !q.options.length)) {
            return { kind: 'unavailable' };
          }
          const answers = Object.fromEntries(questions.map(question => {
            const saved = detail.answers.find(answer => answer.questionId === question.id);
            return [question.id, question.options.filter(option => saved?.selectedOptionIds.includes(option.id))
              .map(option => option.id)];
          }));
          return { kind: 'ready', title: detail.attempt.quiz.name, questions, index, answers };
        }),
        catchError((error: HttpErrorResponse) => of<State>({
          kind: [401, 403, 404, 410].includes(error.status) ? 'unavailable' : 'error',
        })),
        startWith<State>({ kind: 'loading' }),
      )),
      takeUntil(auth.sessionChanges),
      takeUntilDestroyed(),
    ).subscribe(state => {
      this.reviewing.set(false);
      this.selected.set(state.kind === 'ready' ? state.answers[state.questions[state.index].id] : []);
      this.state.set(state);
    });
  }

  reload(): void {
    this.retry.next();
  }

  move(delta: number): void {
    const state = this.state();
    if (state.kind !== 'ready') return;
    const index = state.index + delta;
    if (index < 0 || index >= state.questions.length) return;
    const answers = { ...state.answers, [state.questions[state.index].id]: this.selected() };
    this.selected.set(answers[state.questions[index].id]);
    this.state.set({ ...state, index, answers });
  }

  choose(id: string, multiple: boolean, checked: boolean): void {
    this.selected.update(ids => multiple
      ? checked ? [...new Set([...ids, id])] : ids.filter(value => value !== id)
      : [id]);
  }
}
