import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ApiGenerationSource } from './generation-source';
import { ApiTestsSource } from '../tests/tests-source';
import { AuthService } from '../../core/auth/auth.service';

const settings = { questionCount: 5 as const, difficulty: 'MEDIUM' as const, questionType: 'TRUE_FALSE' as const };
describe('Generation HTTP contract', () => {
  let http: HttpTestingController;
  let source: ApiGenerationSource;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AuthService, useValue: { token: 'test-token' } }] });
    source = TestBed.inject(ApiGenerationSource); http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('uses the SQL catalogue, ignores unavailable choices and keeps server defaults', () => {
    let actual: unknown;
    source.options().subscribe(value => actual = value);
    const request = http.expectOne('/api/generation/parameters');
    expect(request.request.headers.get('Authorization')).toBe('Bearer test-token');
    request.flush({ questionTypeSelection: 'SINGLE', questionCounts: [{ value: 5, available: true }, { value: 10, available: false }], difficulties: [{ value: 'MEDIUM', available: true }], questionTypes: [{ value: 'TRUE_FALSE', available: true }], defaults: settings });
    expect(actual).toEqual({ counts: [5], difficulties: ['MEDIUM'], types: ['TRUE_FALSE'], defaults: settings });
  });
  it('sends the exact material/name/settings and caller idempotency key', () => {
    source.start('material', settings, 'Мой тест', 'same-key').subscribe();
    const request = http.expectOne('/api/quizzes');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('same-key');
    expect(request.request.body).toEqual({ materialId: 'material', ...settings, name: 'Мой тест', language: 'ru' });
    request.flush({ id: 'job', materialId: 'material', status: 'QUEUED', version: 0 });
  });
  it('reads the owned job and real list using auth', () => {
    source.status('job').subscribe();
    http.expectOne('/api/generation-jobs/job').flush({ id: 'job', status: 'RUNNING' });
    TestBed.inject(ApiTestsSource).list().subscribe(value => expect(value.items).toEqual([]));
    const list = http.expectOne('/api/quizzes');
    expect(list.request.headers.get('Authorization')).toBe('Bearer test-token'); list.flush({ items: [] });
  });
  it.each([{ items: [] }, { items: [{ id: 'quiz', materialId: 'other', questionCount: 5 }] }, { items: [{ id: 'quiz', materialId: 'material', questionCount: 0 }] }])('rejects unconfirmed saved results', ({ items }) => {
    const failed = vi.fn(); source.saved('quiz', 'material').subscribe({ error: failed });
    http.expectOne('/api/quizzes').flush({ items }); expect(failed).toHaveBeenCalledTimes(1);
  });
  it('returns the actual saved count including a partial result', () => {
    source.saved('quiz', 'material').subscribe(value => expect(value.questionCount).toBe(3));
    http.expectOne('/api/quizzes').flush({ items: [{ id: 'quiz', materialId: 'material', name: 'Тест', questionCount: 3 }] });
  });
});
