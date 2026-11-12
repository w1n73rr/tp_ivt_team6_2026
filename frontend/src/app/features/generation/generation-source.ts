import { generationRequestKey } from './generation-request-key';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { Observable, defer, delay, of, map } from 'rxjs';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
export interface GenerationSettings {
  questionCount: 5 | 10;
  difficulty: Difficulty;
  questionType: QuestionType;
}
export interface GenerationOptions {
  counts: (5 | 10)[];
  difficulties: Difficulty[];
  types: QuestionType[];
  defaults: GenerationSettings;
}
export interface GenerationJob { id: string; materialId: string; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED'; version: number; stage?: string; detailStage?: string; error?: { code?: string }; quizId?: string; }
export interface GenerationSource {
  readonly demo: boolean;
  options(): Observable<GenerationOptions>;
  start(materialId: string, settings: GenerationSettings, name?: string, key?: string): Observable<GenerationJob | { state: 'DEMO_WAITING' }>;
  status?(id: string): Observable<GenerationJob>;
  saved?(id: string, materialId: string): Observable<{ id: string; name: string; questionCount: number }>;
}
@Injectable({ providedIn: 'root' })
export class DemoGenerationSource implements GenerationSource {
  readonly demo = true;
  options(): Observable<GenerationOptions> {
    return defer(() => of<GenerationOptions>({
      counts: [5, 10], difficulties: ['EASY', 'MEDIUM', 'HARD'],
      types: ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'],
      defaults: { questionCount: 10, difficulty: 'MEDIUM', questionType: 'MULTIPLE_CHOICE' },
    })).pipe(delay(200));
  }
  start(_materialId: string, _settings: GenerationSettings): Observable<{ state: 'DEMO_WAITING' }> {
    // Never invent a server job, quiz ID, percentage, or saved test.
    return of({ state: 'DEMO_WAITING' as const }).pipe(delay(300));
  }
}
export const GENERATION_SOURCE = new InjectionToken<GenerationSource>('GENERATION_SOURCE', {
  providedIn: 'root', factory: () => inject(ApiGenerationSource),
});

@Injectable({ providedIn: 'root' })
export class ApiGenerationSource implements GenerationSource {
  readonly demo = false;
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private headers(): HttpHeaders { return new HttpHeaders({ Authorization: `Bearer ${this.auth.token ?? ''}` }); }
  options(): Observable<GenerationOptions> {
    type Option<T> = { value: T; available: boolean };
    return this.http.get<{ questionCounts: Option<5 | 10>[]; difficulties: Option<Difficulty>[]; questionTypes: Option<QuestionType>[]; defaults: GenerationSettings; questionTypeSelection: string }>('/api/generation/parameters', { headers: this.headers() }).pipe(map(value => {
      if (value.questionTypeSelection !== 'SINGLE') throw new Error('Unsupported selection');
      return { counts: value.questionCounts.filter(v => v.available).map(v => v.value), difficulties: value.difficulties.filter(v => v.available).map(v => v.value), types: value.questionTypes.filter(v => v.available).map(v => v.value), defaults: value.defaults };
    }));
  }
  start(materialId: string, settings: GenerationSettings, name = 'Тест', key: string = generationRequestKey()): Observable<GenerationJob> {
    return this.http.post<GenerationJob>('/api/quizzes', { materialId, ...settings, name, language: 'ru' }, { headers: this.headers().set('Idempotency-Key', key) });
  }
  status(id: string): Observable<GenerationJob> { return this.http.get<GenerationJob>(`/api/generation-jobs/${encodeURIComponent(id)}`, { headers: this.headers() }); }
  saved(id: string, materialId: string): Observable<{ id: string; name: string; questionCount: number }> {
    return this.http.get<{ items: { id: string; materialId: string; name: string; questionCount: number }[] }>('/api/quizzes', { headers: this.headers() }).pipe(map(value => {
      const item = value.items.find(item => item.id === id && item.materialId === materialId);
      if (!item || !Number.isInteger(item.questionCount) || item.questionCount <= 0) throw new Error('Saved test missing');
      return item;
    }));
  }
}
