import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AttemptPage } from './attempt';

describe('AttemptPage', () => {
  const params = new BehaviorSubject(convertToParamMap({ attemptId: 'attempt-1' }));
  beforeEach(() => {
    params.next(convertToParamMap({ attemptId: 'attempt-1' }));
    TestBed.configureTestingModule({
    providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { paramMap: params } },
    ],
    });
  });

  it.each([
    ['SINGLE_CHOICE', 'radio', ['b']],
    ['TRUE_FALSE', 'radio', ['b']],
    ['MULTIPLE_CHOICE', 'checkbox', ['a', 'b']],
  ])('renders and handles %s', (type, control, expected) => {
    const fixture = TestBed.createComponent(AttemptPage);
    fixture.detectChanges();
    const text = '<img src=x onerror=alert(1)>';
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/attempts/attempt-1').flush({
      attempt: { status: 'IN_PROGRESS', quiz: { name: 'Тест' } },
      questions: [{ id: 'q', type, position: 1, text, options: [{ id: 'a', text }, { id: 'b', text: 'Б' }] }],
      answers: [],
      currentPosition: 1,
    });
    fixture.detectChanges();
    const inputs = fixture.nativeElement.querySelectorAll('input');
    expect(inputs).toHaveLength(2);
    expect(inputs[0].type).toBe(control);
    inputs[0].click();
    inputs[1].click();
    expect(fixture.componentInstance.selected()).toEqual(expected);
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain(text);
    http.verify();
  });
  it.each([5, 10])('navigates %i questions, retains choice and resets on route change', count => {
    const fixture = TestBed.createComponent(AttemptPage);
    const http = TestBed.inject(HttpTestingController);
    const detail = {
      attempt: { status: 'IN_PROGRESS', quiz: { name: 'Тест' } },
      questions: Array.from({ length: count }, (_, i) => ({
        id: 'q' + i, position: i + 1, type: 'SINGLE_CHOICE', text: 'Вопрос ' + (i + 1),
        options: [{ id: 'a', text: 'А' }, { id: 'b', text: 'Б' }],
      })),
      answers: [],
      currentPosition: 1,
    };
    http.expectOne('/api/attempts/attempt-1').flush(detail);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    const back = () => fixture.nativeElement.querySelector('.attempt-actions button:first-child') as HTMLButtonElement;
    expect(back().disabled).toBe(true);
    page.choose('a', false, true);
    page.move(1);
    expect(page.selected()).toEqual([]);
    page.move(1);
    page.move(-1);
    page.move(-1);
    expect(page.selected()).toEqual(['a']);
    for (let i = 1; i <= count; i++) page.move(1);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('progress').value).toBe(count);
    expect(fixture.nativeElement.querySelector('.attempt-actions button:last-child').textContent).toContain('Завершить тест');

    params.next(convertToParamMap({ attemptId: 'attempt-2' }));
    expect(page.selected()).toEqual([]);
    http.expectOne('/api/attempts/attempt-2').flush(detail);
    expect(page.selected()).toEqual([]);
    http.verify();
  });
});
