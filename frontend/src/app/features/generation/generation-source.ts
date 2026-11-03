import { Injectable, InjectionToken } from '@angular/core';
import { Observable, defer, delay, of } from 'rxjs';

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
// UI boundary, not a confirmed HTTP contract. Replace the provider after FE/BE agreement.
export interface GenerationSource {
  readonly demo: boolean;
  options(): Observable<GenerationOptions>;
  start(materialId: string, settings: GenerationSettings): Observable<{ state: 'DEMO_WAITING' }>;
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
  providedIn: 'root', factory: () => new DemoGenerationSource(),
});
