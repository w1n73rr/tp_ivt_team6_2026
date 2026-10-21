import { Component, inject, signal, OnInit, DestroyRef } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, concatMap, filter, finalize, from, map, of, switchMap, toArray } from 'rxjs';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MaterialEditor, MaterialDeleteConfirm } from './material-dialogs';
import { MaterialFiles } from './material-files';
import { MaterialsApi } from './materials.api';
import { Material } from './materials.models';
import { FigmaIcon } from './figma-icon';
import { MaterialsShell } from './materials-shell';
@Component({
  selector: 'app-materials',
  imports: [MaterialsShell, DatePipe, DecimalPipe, FigmaIcon, MatDialogModule],
  templateUrl: './materials.html',
  styleUrl: './materials.scss',
})
export class Materials implements OnInit {
  private readonly dialogs = inject(MatDialog);
  private readonly files = inject(MaterialFiles);
  readonly busy = signal(false);
  private readonly router = inject(Router);
  private readonly source = inject(MaterialsApi);
  private readonly destroy = inject(DestroyRef);
  readonly items = signal<Material[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  selected = new Set<string>();
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    if (this.loading() || this.busy()) return;
    this.loading.set(true);
    this.error.set('');
    this.items.set([]);
    this.selected.clear();
    this.source
      .list()
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.items.set(r.items);
        },
        error: (error: unknown) => this.error.set(this.source.errorMessage(error)),
      });
  }
  toggle(id: string): void {
    this.selected.has(id) ? this.selected.delete(id) : this.selected.add(id);
  }
  toggleAll(): void {
    this.selected.size === this.items().length
      ? this.selected.clear()
      : this.items().forEach((i) => this.selected.add(i.id));
  }
  edit(item: Material): void {
    if (this.busy()) return;
    this.message.set(''); this.busy.set(true);
    this.source.detail(item.id).pipe(takeUntilDestroyed(this.destroy), finalize(() => this.busy.set(false))).subscribe({
      next: detail => this.dialogs.open(MaterialEditor, {data: detail, width: '460px', maxWidth: 'calc(100vw - 32px)'}).afterClosed()
        .pipe(takeUntilDestroyed(this.destroy)).subscribe((updated: Material | undefined) => {
          if (updated) { this.items.update(items => items.map(row => row.id === updated.id ? updated : row)); this.message.set('Изменения сохранены.'); }
        }),
      error: error => this.message.set(this.source.actionError(error)),
    });
  }
  remove(items: Material[]): void {
    if (this.busy() || !items.length) return;
    this.dialogs.open(MaterialDeleteConfirm, {data: items, width: '460px', maxWidth: 'calc(100vw - 32px)'}).afterClosed().pipe(
      filter(Boolean),
      switchMap(() => {
        this.busy.set(true); this.message.set('');
        return from(items).pipe(concatMap(item => this.source.delete(item.id).pipe(
          map(() => ({id: item.id, error: ''})), catchError(error => of({id: item.id, error: this.source.actionError(error)})),
        )), toArray(), finalize(() => this.busy.set(false)));
      }), takeUntilDestroyed(this.destroy),
    ).subscribe(results => {
      const removed = new Set(results.filter(result => !result.error).map(result => result.id));
      this.items.update(rows => rows.filter(row => !removed.has(row.id)));
      removed.forEach(id => this.selected.delete(id));
      const failed = results.filter(result => result.error);
      this.message.set(`Удалено материалов: ${removed.size}.` + (failed.length ? ` Не удалено: ${failed.length}. ${failed[0].error}` : ''));
    });
  }
  removeSelected(): void { this.remove(this.items().filter(item => this.selected.has(item.id))); }
  file(item: Material, preview: boolean): void {
    if (this.busy()) return;
    const tab = preview ? this.files.reservePreview() : null;
    if (preview && !tab) { this.message.set('Браузер заблокировал новую вкладку. Разрешите её открытие или скачайте файл.'); return; }
    let delivered = false;
    this.busy.set(true); this.message.set('');
    this.source.file(item.id, preview && item.format !== 'DOCX').pipe(
      switchMap(response => {
        if (tab) return from(this.files.preview(response, item, tab));
        this.files.download(response, item); return of(undefined);
      }), takeUntilDestroyed(this.destroy), finalize(() => { this.busy.set(false); if (tab && !delivered) tab.close(); }),
    ).subscribe({next: () => { delivered = true; }, error: error => { tab?.close(); this.message.set(this.source.actionError(error)); }});
  }
  explain(action: string): void {
    if (action === 'Загрузка материала') {
      void this.router.navigateByUrl('/materials/upload');
      return;
    }
    this.message.set(
      action + ' пока доступно только как элемент макета. Серверные операции не выполняются.',
    );
  }
}
