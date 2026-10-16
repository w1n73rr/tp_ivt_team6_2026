import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Materials } from './materials';
import { MaterialsMock } from './materials.mock';
import { of, throwError } from 'rxjs';
describe('Materials prototype', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ imports: [Materials], providers: [provideRouter([])] }),
  );
  it('renders contract fields from mock items', () => {
    const service = TestBed.inject(MaterialsMock);
    vi.spyOn(service, 'list').mockReturnValue(of({ items: service.records() }));
    const fixture = TestBed.createComponent(Materials);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelectorAll('article').length).toBe(5);
    expect(fixture.nativeElement.textContent).toContain('Демонстрационный режим');
    expect(fixture.nativeElement.textContent).toContain('01.10.2026');
  });
  it('shows empty state on successful empty response', () => {
    vi.spyOn(TestBed.inject(MaterialsMock), 'list').mockReturnValue(of({ items: [] }));
    const f = TestBed.createComponent(Materials);
    f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Материалы не найдены');
    expect(f.nativeElement.querySelector('[role=alert]')).toBeNull();
  });
  it('keeps errors distinct from empty state and permits retry', () => {
    const spy = vi
      .spyOn(TestBed.inject(MaterialsMock), 'list')
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
