import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AttemptResultPage, formatDuration } from './attempt-result';

it('formats elapsed time without wrapping hours', () => {
  expect(formatDuration(765)).toBe('00:12:45');
  expect(formatDuration(90061)).toBe('25:01:01');
});

it('shows server counts, clears on route changes and distinguishes result errors', () => {
  const params = new BehaviorSubject(convertToParamMap({ attemptId: 'a' }));
  TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
    { provide: ActivatedRoute, useValue: { paramMap: params } }] });
  const fixture = TestBed.createComponent(AttemptResultPage);
  const page = fixture.componentInstance;
  const http = TestBed.inject(HttpTestingController);
  const request = (id: string) => http.expectOne('/api/attempts/' + id + '/result');
  request('a').flush({
    attemptId: 'a', quiz: { name: 'Личный тест' }, score: 2, scorePercent: 40, totalQuestions: 5,
    correctCount: 2, incorrectCount: 1, skippedCount: 2, durationSeconds: 765,
    items: [{
      question: { id: 'q1', position: 1, text: 'Вопрос', options: [{ id: 'right', text: 'Верный вариант' }] },
      selectedOptionIds: [], correctOptionIds: ['right'], outcome: 'SKIPPED', score: 0,
    }],
  });
  fixture.detectChanges();
  expect([...fixture.nativeElement.querySelectorAll('dd')].map((node: Element) => node.textContent))
    .toEqual(['2', '1', '2', '5', '00:12:45', '—', '—', '—']);
  expect(fixture.nativeElement.querySelector('.question.skipped .selected')).toBeNull();
  expect(fixture.nativeElement.querySelector('.question.skipped .empty').textContent).toContain('Не отвечено');
  expect(fixture.nativeElement.querySelector('.question .expected').textContent).toContain('Верный вариант');
  params.next(convertToParamMap({ attemptId: 'b' }));
  expect(page.state()).toEqual({ kind: 'loading' });
  for (const [status, kind] of [[409, 'unfinished'], [404, 'unavailable'], [503, 'error']] as const) {
    request('b').flush({}, { status, statusText: kind });
    expect(page.state()).toEqual({ kind });
    if (status !== 503) page.retry.next();
  }
  http.verify();
});
