import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MaterialUpload } from './upload';
import { AuthService } from '../../../core/auth/auth.service';
describe('Server upload form', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [MaterialUpload],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    sessionStorage.clear();
  });
  function prepare() {
    const http = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthService).login({ username: 'VA', password: 'test' }).subscribe();
    http.expectOne('/api/auth/login').flush({ token: 'jwt' });
    const f = TestBed.createComponent(MaterialUpload);
    f.detectChanges();
    http.expectOne('/api/subjects').flush({ items: [{ id: 's', name: 'Базы данных' }] });
    const page = f.componentInstance;
    page.accept([
      new File(['lecture'], 'lecture.txt', { type: 'text/plain' }),
    ] as unknown as FileList);
    page.form.patchValue({ name: 'Лекция', subject: 'Базы данных', description: 'Описание' });
    return { page, f, http };
  }
  it('adds a typed tag when focus leaves its input and ignores duplicates', () => {
    const {page, f} = prepare();
    const input = f.nativeElement.querySelector('input[placeholder="Добавить тег"]') as HTMLInputElement;
    expect(input).toBeTruthy();
    input.value = ' SQL '; input.dispatchEvent(new Event('input')); input.dispatchEvent(new Event('blur'));
    expect(page.tags()).toEqual(['SQL']); expect(page.tagInput).toBe('');
    input.value = 'SQL'; input.dispatchEvent(new Event('input')); input.dispatchEvent(new Event('blur'));
    expect(page.tags()).toEqual(['SQL']);
  });
  it('rejects forbidden, empty and oversized files without a request', () => {
    const { page, http } = prepare();
    for (const file of [
      new File(['x'], 'bad.exe'),
      new File([], 'empty.txt'),
      new File([new Uint8Array(10485761)], 'big.txt'),
    ]) {
      page.accept([file] as unknown as FileList);
      page.submit();
      expect(page.fileError()).not.toBe('');
    }
    http.expectNone('/api/materials');
  });
  it('blocks blank and overlong metadata', () => {
    const { page, http } = prepare();
    for (const patch of [
      { name: ' ' },
      { name: 'x'.repeat(256) },
      { name: 'Лекция', subject: '__new', customSubject: ' ' },
    ]) {
      page.form.patchValue(patch);
      page.submit();
    }
    http.expectNone('/api/materials');
  });
  it('sends one multipart request with JSON metadata, JWT and the actual file', async () => {
    const { page, f, http } = prepare();
    page.tagInput = 'SQL';
    page.submit();
    page.submit();
    const req = http.expectOne('/api/materials');
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt');
    expect(req.request.headers.has('Content-Type')).toBe(false);
    const body = req.request.body as FormData;
    expect((body.get('file') as File).name).toBe('lecture.txt');
    const metadata = body.get('metadata') as Blob;
    expect(metadata.type).toBe('application/json');
    const text = await new Promise<string>((resolve) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.readAsText(metadata);
    });
    expect(JSON.parse(text)).toEqual({
      name: 'Лекция',
      subjectName: 'Базы данных',
      description: 'Описание',
      tagIds: [],
      newTagNames: ['SQL'],
    });
    req.flush({ material: { id: 'saved' } }, { status: 201, statusText: 'Created' });
    f.detectChanges();
    expect(page.savedId()).toBe('saved');
    expect(page.loading()).toBe(false);
    expect(f.nativeElement.textContent).toContain('сохранён на сервере');
  });
  it('keeps inputs on conflict and allows a corrected retry', () => {
    const { page, http } = prepare();
    page.submit();
    http.expectOne('/api/materials').flush({}, { status: 409, statusText: 'Conflict' });
    expect(page.error()).toContain('уже существует');
    expect(page.file()?.name).toBe('lecture.txt');
    expect(page.form.controls.description.value).toBe('Описание');
    page.form.controls.name.setValue('Другая лекция');
    page.submit();
    http.expectOne('/api/materials').flush({ material: { id: 'new' } });
    expect(page.success()).toBe(true);
  });
  it('does not treat storage failure as successful upload', () => {
    const { page, http } = prepare();
    page.submit();
    http.expectOne('/api/materials').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(page.success()).toBe(false);
    expect(page.error()).toContain('Хранилище');
    expect(page.loading()).toBe(false);
  });
});
