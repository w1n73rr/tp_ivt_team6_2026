import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MaterialUpload } from './upload';
import { MaterialsMock } from '../materials.mock';
import { firstValueFrom, timer } from 'rxjs';
describe('Mock upload form', () => {
  beforeEach(() =>
    TestBed.configureTestingModule({ imports: [MaterialUpload], providers: [provideRouter([])] }),
  );
  function prepare() {
    const f = TestBed.createComponent(MaterialUpload);
    f.detectChanges();
    const page = f.componentInstance;
    page.accept([
      new File(['lecture'], 'lecture.txt', { type: 'text/plain' }),
    ] as unknown as FileList);
    page.form.patchValue({ name: 'Лекция', subject: 'Базы данных', description: 'Описание' });
    return { f, page, store: TestBed.inject(MaterialsMock) };
  }
  it('rejects unsupported and empty files without upload', () => {
    const { page, store } = prepare();
    const spy = vi.spyOn(store, 'upload');
    page.accept([new File(['data'], 'bad.exe')] as unknown as FileList);
    expect(page.file()).toBeNull();
    expect(page.fileError()).toContain('PDF, DOCX и TXT');
    page.submit();
    expect(spy).not.toHaveBeenCalled();
    page.accept([new File([], 'empty.txt')] as unknown as FileList);
    expect(page.fileError()).toContain('пустой');
  });
  it('blocks blank metadata and a blank new subject', () => {
    const { page, store } = prepare();
    const spy = vi.spyOn(store, 'upload');
    page.form.patchValue({ name: '  ' });
    page.submit();
    expect(spy).not.toHaveBeenCalled();
    page.form.patchValue({ name: 'Лекция', subject: '__new', customSubject: '  ' });
    page.submit();
    expect(spy).not.toHaveBeenCalled();
  });
  it('adds only one mock record and blocks repeat submission while sending', async () => {
    const { f, page, store } = prepare();
    const initial = store.records().length;
    const spy = vi.spyOn(store, 'upload');
    page.tagInput = 'SQL';
    page.addTag();
    page.tagInput = 'SQL';
    page.addTag();
    page.submit();
    page.submit();
    expect(page.loading()).toBe(true);
    expect(spy).toHaveBeenCalledTimes(1);
    await firstValueFrom(timer(650));
    f.detectChanges();
    expect(store.records().length).toBe(initial + 1);
    expect(store.records()[0].tags).toEqual(['SQL']);
    expect(store.records()[0].description).toBe('Описание');
    expect(page.loading()).toBe(false);
    expect(f.nativeElement.textContent).toContain('Серверного сохранения не было');
  });
  it('preserves file, tags and form after a simulated error, then permits retry', async () => {
    const { page, store } = prepare();
    const initial = store.records().length;
    page.tagInput = 'SQL';
    page.addTag();
    page.failUpload = true;
    page.submit();
    await firstValueFrom(timer(650));
    expect(store.records().length).toBe(initial);
    expect(page.form.controls.description.value).toBe('Описание');
    expect(page.file()?.name).toBe('lecture.txt');
    expect(page.tags()).toEqual(['SQL']);
    expect(page.error()).toContain('Данные формы сохранены');
    page.failUpload = false;
    page.submit();
    await firstValueFrom(timer(650));
    expect(store.records().length).toBe(initial + 1);
    expect(page.success()).toBe(true);
  });
  it('accepts a dropped supported file and pre-fills its name', () => {
    const { page } = prepare();
    page.form.controls.name.setValue('');
    const preventDefault = vi.fn();
    page.drop({
      preventDefault,
      dataTransfer: { files: [new File(['x'], 'Лекция.PDF', { type: 'application/pdf' })] },
    } as unknown as DragEvent);
    expect(preventDefault).toHaveBeenCalled();
    expect(page.form.controls.name.value).toBe('Лекция');
  });
});
