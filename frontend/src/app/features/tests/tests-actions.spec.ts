import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, of, Subject, throwError } from 'rxjs';
import { TestDialog } from './test-dialog';
import { DemoTestsSource, TESTS_SOURCE, TestSummary } from './tests-source';
import { sortTests, TEST_SORTS } from './tests-sort';

const item: TestSummary = { id: 'one', name: 'Лекция 1', materialName: 'Материал Б', format: 'PDF', questionCount: 5, difficulty: 'EASY', questionType: 'TRUE_FALSE', createdAt: '2026-10-01T00:00:00Z' };
describe('Test management dialogs', () => {
  let close: ReturnType<typeof vi.fn>, rename: ReturnType<typeof vi.fn>, remove: ReturnType<typeof vi.fn>;
  function setup(action: 'rename' | 'delete' = 'rename') {
    close = vi.fn(); rename = vi.fn(() => of({ ...item, name: 'Новое' })); remove = vi.fn(() => of(undefined));
    TestBed.configureTestingModule({ imports: [TestDialog], providers: [
      { provide: MAT_DIALOG_DATA, useValue: { action, item } },
      { provide: MatDialogRef, useValue: { close, disableClose: false } },
      { provide: TESTS_SOURCE, useValue: { demo: true, rename, remove } },
    ] });
    const fixture = TestBed.createComponent(TestDialog); fixture.detectChanges(); return fixture;
  }
  it('opens with the current name and cancels without a mutation', async () => {
    const f = setup(); await f.whenStable(); f.detectChanges();
    expect(f.nativeElement.querySelector('input').value).toBe(item.name);
    const cancel = Array.from(f.nativeElement.querySelectorAll('button')).find((b: any) => b.textContent === 'Отмена') as HTMLButtonElement;
    f.componentInstance.name.set('Другой'); cancel.click();
    expect(close).toHaveBeenCalled(); expect(rename).not.toHaveBeenCalled();
  });
  it.each(['', '   ', 'a'.repeat(101)])('rejects invalid names before calling the source', name => {
    const f = setup(); f.componentInstance.name.set(name); f.componentInstance.save();
    expect(rename).not.toHaveBeenCalled(); expect(f.componentInstance.error()).toContain('от 1 до 100');
  });
  it('trims and sends only the selected id and name', () => {
    const f = setup(); f.componentInstance.name.set(' Новое '); f.componentInstance.save();
    expect(rename).toHaveBeenCalledExactlyOnceWith('one', 'Новое');
    expect(close).toHaveBeenCalledWith({ action: 'rename', item: { ...item, name: 'Новое' } });
  });
  it('keeps entered text after conflict and retries without duplicate sending', () => {
    const f = setup(); const reply = new Subject<TestSummary>(); rename.mockReturnValue(reply);
    f.componentInstance.name.set('Введённое'); f.componentInstance.save(); f.componentInstance.save();
    expect(rename).toHaveBeenCalledTimes(1); expect(TestBed.inject(MatDialogRef).disableClose).toBe(true);
    reply.error(new HttpErrorResponse({ status: 409 }));
    expect(f.componentInstance.name()).toBe('Введённое'); expect(close).not.toHaveBeenCalled();
    expect(f.componentInstance.error()).toContain('уже существует');
    rename.mockReturnValue(of({ ...item, name: 'Введённое' })); f.componentInstance.save(); expect(close).toHaveBeenCalled();
  });
  it('requires confirmation, preserves the selected item on failure and permits retry', () => {
    const f = setup('delete'); expect(f.nativeElement.textContent).toContain(item.name); expect(remove).not.toHaveBeenCalled();
    remove.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 503 })));
    f.componentInstance.save(); expect(close).not.toHaveBeenCalled(); expect(f.componentInstance.error()).toContain('Не удалось удалить');
    remove.mockReturnValue(of(undefined)); f.componentInstance.save(); expect(close).toHaveBeenCalledWith({ action: 'delete', id: 'one' });
  });
  it('blocks repeated delete submission while waiting', () => {
    const f = setup('delete'); remove.mockReturnValue(new Subject<void>());
    f.componentInstance.save(); f.componentInstance.save(); expect(remove).toHaveBeenCalledTimes(1); expect(f.componentInstance.busy()).toBe(true);
  });
});

describe('Stable table sorting', () => {
  const rows = [item, { ...item, id: 'two', name: 'А', materialName: 'Материал А', createdAt: '2026-09-01T00:00:00Z' }, { ...item, id: 'three' }];
  it.each(TEST_SORTS)('preserves equal values and source data for %s', sort => {
    const original = structuredClone(rows); const result = sortTests(rows, sort);
    expect(result.indexOf(rows[0])).toBeLessThan(result.indexOf(rows[2])); expect(rows).toEqual(original);
    expect(sortTests([], sort)).toEqual([]);
  });
  it('sorts all three fields in both directions', () => {
    for (const key of ['name', 'materialName', 'createdAt'] as const) {
      expect(sortTests(rows, `${key}:asc`)[0].id).toBe('two');
      expect(sortTests(rows, `${key}:desc`).at(-1)!.id).toBe('two');
    }
  });
});

describe('Demo source isolation and mutations', () => {
  it('keeps mutations in memory per user and preserves all non-name fields', async () => {
    let owner = 'user-a'; const source = new DemoTestsSource(() => owner);
    const rows = (await firstValueFrom(source.list())).items; const original = rows[0];
    expect(await firstValueFrom(source.rename(original.id, 'Переименовано'))).toEqual({ ...original, name: 'Переименовано' });
    await expect(firstValueFrom(source.rename(rows[1].id, 'Переименовано'))).rejects.toMatchObject({ status: 409 });
    await firstValueFrom(source.rename(rows[1].id, 'переименовано'));
    source.setMutationFailure(true); await expect(firstValueFrom(source.remove(original.id))).rejects.toMatchObject({ status: 503 });
    expect((await firstValueFrom(source.list())).items.some(row => row.id === original.id)).toBe(true);
    source.setMutationFailure(false); await firstValueFrom(source.remove(original.id));
    expect((await firstValueFrom(source.list())).items).toHaveLength(rows.length - 1);
    owner = 'user-b'; expect((await firstValueFrom(source.list())).items[0]).toEqual(original);
    owner = 'user-a'; expect((await firstValueFrom(source.list())).items).toHaveLength(rows.length - 1);
  });
});
