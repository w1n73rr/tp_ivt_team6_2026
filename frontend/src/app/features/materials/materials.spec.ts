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
  it('combines name and format, preserves source data, clears query independently and resets all', () => {
    const items = [
      { id: 'a', name: 'Лекция SQL', format: 'PDF' as const, sizeBytes: 1, subjectId: 's', subjectName: 'SQL', uploadedAt: '2026-10-01' },
      { id: 'b', name: 'лекция Java', format: 'TXT' as const, sizeBytes: 1, subjectId: 's', subjectName: 'SQL', uploadedAt: '2026-10-01' },
      { id: 'c', name: 'Практика', format: 'DOCX' as const, sizeBytes: 1, subjectId: 's', subjectName: 'SQL', uploadedAt: '2026-10-01' },
    ];
    const original = structuredClone(items);
    const list = vi.spyOn(TestBed.inject(MaterialsApi), 'list').mockReturnValue(of({items}));
    const f = TestBed.createComponent(Materials); f.detectChanges();
    const p = f.componentInstance;
    const input: HTMLInputElement = f.nativeElement.querySelector('input[type=search]');
    const select: HTMLSelectElement = f.nativeElement.querySelector('select');
    input.value = '  ЛЕКЦИЯ  '; input.dispatchEvent(new Event('input')); f.detectChanges();
    expect(f.nativeElement.querySelectorAll('article')).toHaveLength(2);
    select.value = 'PDF'; select.dispatchEvent(new Event('change')); f.detectChanges();
    expect(p.visibleItems().map(x => x.id)).toEqual(['a']);
    p.setQuery('SQL'); expect(p.visibleItems()).toHaveLength(1);
    p.setQuery('Java'); f.detectChanges();
    expect(f.nativeElement.textContent).toContain('Нет материалов, соответствующих');
    expect(f.nativeElement.querySelector('.list-head input').disabled).toBe(true);
    p.setQuery(''); expect(p.visibleItems().map(x => x.id)).toEqual(['a']);
    p.setFormat('TXT'); expect(p.visibleItems().map(x => x.id)).toEqual(['b']);
    p.setFormat('DOCX'); expect(p.visibleItems().map(x => x.id)).toEqual(['c']);
    p.resetFilters(); f.detectChanges();
    expect(p.visibleItems()).toEqual(original); expect(items).toEqual(original);
    expect(input.value).toBe(''); expect(select.value).toBe(''); expect(list).toHaveBeenCalledTimes(1);
  });
  it('confirms a dropped document before navigating and rejects unsupported files', () => {
    vi.spyOn(TestBed.inject(MaterialsApi), 'list').mockReturnValue(of({items: []}));
    const p = TestBed.createComponent(Materials).componentInstance;
    const event = (files: File[]) => ({preventDefault: vi.fn(), dataTransfer: {types: ['Files'], files}} as unknown as DragEvent);
    p.dragOver(event([])); expect(p.dragging()).toBe(true);
    p.dropFile(event([new File(['text'], 'lecture.TXT')]));
    expect(p.dragging()).toBe(false); expect(p.dropped()?.name).toBe('lecture.TXT');
    p.cancelDrop(); expect(p.dropped()).toBeNull();
    p.dropFile(event([new File(['x'], 'old.doc')])); expect(p.dropped()).toBeNull(); expect(p.message()).toContain('PDF');
    p.dropFile(event([new File([], 'empty.pdf')])); expect(p.dropped()).toBeNull();
    p.dropFile(event([new File(['a'], 'a.txt'), new File(['b'], 'b.txt')])); expect(p.message()).toContain('один');
  });

});
