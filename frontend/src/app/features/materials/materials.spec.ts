import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Materials } from './materials';
import { MaterialsApi } from './materials.api';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
describe('Materials API screen', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ imports: [Materials], providers: [provideRouter([]), provideHttpClient()] }),
  );
  it('renders contract fields from mock items', () => {
    const service = TestBed.inject(MaterialsApi);
    vi.spyOn(service, 'list').mockReturnValue(of({ items: [{ id: 'one', name: 'Лекция', format: 'PDF', sizeBytes: 100, subjectId: 's', subjectName: 'Предмет', uploadedAt: '2026-10-01T00:00:00Z' }] }));
    const fixture = TestBed.createComponent(Materials);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(1);
    expect(fixture.nativeElement.textContent).not.toContain('Демонстрационный режим');
    expect(fixture.nativeElement.textContent).toContain('01.10.2026');
  });
  it('shows empty state on successful empty response', () => {
    vi.spyOn(TestBed.inject(MaterialsApi), 'list').mockReturnValue(of({ items: [] }));
    const f = TestBed.createComponent(Materials);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Материалы не найдены');
    expect(f.nativeElement.querySelector('[role=alert]')).toBeNull();
  });
  it('keeps errors distinct from empty state and permits retry', () => {
    const spy = vi
      .spyOn(TestBed.inject(MaterialsApi), 'list')
      .mockReturnValue(throwError(() => new Error()));
    const f = TestBed.createComponent(Materials);
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]')).toBeTruthy();
    expect(f.nativeElement.textContent).not.toContain('Материалы не найдены');
    spy.mockReturnValue(of({ items: [] }));
    f.componentInstance.load();
    f.detectChanges();
    expect(f.nativeElement.querySelector('[role=alert]')).toBeNull();
  });
});
