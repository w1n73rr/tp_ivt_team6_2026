import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, finalize, throwError } from 'rxjs';
import { TESTS_SOURCE, TestSummary } from './tests-source';

export type TestDialogResult = { action: 'rename'; item: TestSummary } | { action: 'delete'; id: string };
@Component({ selector: 'app-test-dialog', imports: [MatDialogModule, FormsModule],
  template: `<div class="dialog-head"><h2 mat-dialog-title>{{ data.action === 'rename' ? 'Изменить название' : 'Удалить тест?' }}</h2><button class="close" aria-label="Закрыть окно" [disabled]="busy()" (click)="dialog.close()">×</button></div>
    <form (ngSubmit)="save()"><mat-dialog-content>
      @if (data.action === 'rename') {
        <p>Меняется только название теста.</p><label for="test-rename">Название теста</label>
        <input id="test-rename" name="testName" [ngModel]="name()" (ngModelChange)="name.set($event)" [disabled]="busy()" maxlength="100" aria-describedby="rename-counter" [attr.aria-invalid]="!!error()" />
        <small id="rename-counter">{{ name().length }}/100</small>
      } @else {
        <p>«{{ data.item.name }}»</p><p>Тест и все его вопросы будут удалены. Восстановить их не получится.</p>
      }
      @if (source.demo) { <p class="demo">Демонстрация: изменения только в памяти вкладки. Серверные данные не меняются.</p> }
      @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
    </mat-dialog-content><mat-dialog-actions align="end"><button type="button" [disabled]="busy()" (click)="dialog.close()">Отмена</button><button type="submit" [class.danger]="data.action === 'delete'" [class.primary]="data.action === 'rename'" [disabled]="busy()">{{ busy() ? 'Отправляем…' : data.action === 'rename' ? 'Сохранить' : 'Удалить' }}</button></mat-dialog-actions></form>`,
  styleUrl: './test-dialog.scss' })
export class TestDialog {
  readonly data = inject<{ action: 'rename' | 'delete'; item: TestSummary }>(MAT_DIALOG_DATA);
  readonly dialog = inject(MatDialogRef<TestDialog, TestDialogResult>);
  readonly source = inject(TESTS_SOURCE);
  private readonly destroyRef = inject(DestroyRef);
  readonly name = signal(this.data.item.name);
  readonly busy = signal(false);
  readonly error = signal('');
  save(): void {
    if (this.busy()) return;
    const name = this.name().trim();
    if (this.data.action === 'rename' && (!name || name.length > 100)) { this.error.set('Название должно содержать от 1 до 100 символов.'); return; }
    this.error.set(''); this.busy.set(true); this.dialog.disableClose = true;
    const request: Observable<TestSummary | void> | undefined = this.data.action === 'rename' ? this.source.rename?.(this.data.item.id, name) : this.source.remove?.(this.data.item.id);
    (request ?? throwError(() => new HttpErrorResponse({ status: 501 }))).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { this.busy.set(false); this.dialog.disableClose = false; })).subscribe({
      next: item => this.dialog.close(this.data.action === 'rename' ? { action: 'rename', item: item as TestSummary } : { action: 'delete', id: this.data.item.id }),
      error: error => {
        const status = error instanceof HttpErrorResponse ? error.status : 0;
        this.error.set(status === 409 ? 'Тест с таким названием уже существует' : status === 401 ? 'Сессия завершена. Войдите снова.' : status === 404 ? 'Тест не найден или недоступен.' : status === 400 ? 'Проверьте название теста.' : this.data.action === 'rename' ? 'Не удалось сохранить название. Попробуйте ещё раз.' : 'Не удалось удалить тест. Попробуйте ещё раз.');
      },
    });
  }
}
