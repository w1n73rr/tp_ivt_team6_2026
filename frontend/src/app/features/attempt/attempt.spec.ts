import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { AttemptPage } from './attempt';

describe('AttemptPage', () => {
  beforeEach(() => TestBed.configureTestingModule({
    providers: [
      provideRouter([]), provideHttpClient(), provideHttpClientTesting(),
      { provide: ActivatedRoute, useValue: { paramMap: of(convertToParamMap({ attemptId: 'attempt-1' })) } },
    ],
  }));

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
});
