import { InjectionToken } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, defer, delay, of, switchMap, throwError } from 'rxjs';

export interface TestSummary {
  id: string;
  name: string;
  materialName: string;
  format: 'PDF' | 'DOCX' | 'TXT';
  questionCount: 5 | 10;
  difficulty: 'EASY' | 'MEDIUM' | 'HARD';
  questionType: 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE';
  createdAt: string;
}
export type DemoScenario = 'list' | 'empty' | 'error' | 'auth';
// UI model only. Resolve /api/tests vs /api/quizzes with BE before implementing HTTP.
export interface TestsSource {
  readonly demo: boolean;
  list(): Observable<{ items: TestSummary[] }>;
  setScenario?(scenario: DemoScenario): void;
}
export class DemoTestsSource implements TestsSource {
  readonly demo = true;
  private scenario: DemoScenario = 'list';
  setScenario(scenario: DemoScenario): void { this.scenario = scenario; }
  list(): Observable<{ items: TestSummary[] }> {
    return of(null).pipe(delay(250), switchMap(() => defer(() => {
      if (this.scenario === 'auth') return throwError(() => new HttpErrorResponse({ status: 401 }));
      if (this.scenario === 'error') return throwError(() => new HttpErrorResponse({ status: 503 }));
      const names = ['Базы данных 1', 'Базы данных 2', 'SQL: запросы 1', 'Индексы 1', 'Конспект: ключи 1', 'Операционные системы 1', 'Нормализация 1', 'Соединения таблиц 1'];
      const materials = ['Базы данных. Лекция 3 — Нормализация', 'Базы данных. Лекция 3 — Нормализация', 'SQL: запросы и соединения таблиц', 'Индексы и производительность', 'Конспект: ключи и связи таблиц', 'Операционные системы. Лекция 1', 'Базы данных. Нормализация', 'SQL: запросы и соединения таблиц'];
      const items = names.map((name, index): TestSummary => ({
        id: `demo-test-${index + 1}`, name, materialName: materials[index],
        format: (['PDF', 'PDF', 'TXT', 'DOCX', 'TXT', 'PDF', 'DOCX', 'TXT'] as const)[index],
        questionCount: index % 2 === 0 ? 10 : 5,
        difficulty: (['MEDIUM', 'HARD', 'EASY'] as const)[index % 3],
        questionType: (['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'] as const)[index % 3],
        createdAt: `2026-09-${String(28 - index * 2).padStart(2, '0')}T12:00:00Z`,
      }));
      return of({ items: this.scenario === 'empty' ? [] : items });
    })));
  }
}
export const TESTS_SOURCE = new InjectionToken<TestsSource>('TESTS_SOURCE', {
  providedIn: 'root', factory: () => new DemoTestsSource(),
});
