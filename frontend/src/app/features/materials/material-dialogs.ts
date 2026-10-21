import { Component, DestroyRef, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MaterialsApi } from './materials.api';
import { Material } from './materials.models';

@Component({
  selector: 'app-material-editor', imports: [MatDialogModule, ReactiveFormsModule],
  template: `<h2 mat-dialog-title>Изменить материал</h2>
    <form [formGroup]="form" (ngSubmit)="save()">
      <mat-dialog-content>
        <label for="edit-name">Название</label><input id="edit-name" formControlName="name" maxlength="255" />
        <label for="edit-subject">Предмет</label><input id="edit-subject" formControlName="subjectName" maxlength="100" />
        <p class="hint">При смене предмета сервер отсоединит прежние теги материала.</p>
        <p><b>Теги:</b> {{ data.tags.map(tagName).join(', ') || 'Не указаны' }}</p>
        <p><b>Описание:</b> {{ data.description || 'Не указано' }}</p>
        <p class="hint">Изменение тегов и описания пока не поддерживается сервером.</p>
        @if (error()) { <p class="error" role="alert">{{ error() }}</p> }
      </mat-dialog-content>
      <mat-dialog-actions align="end"><button type="button" [disabled]="saving()" (click)="dialog.close()">Отмена</button>
        <button type="submit" class="primary" [disabled]="saving()">{{ saving() ? 'Сохранение…' : 'Сохранить' }}</button></mat-dialog-actions>
    </form>`, styleUrl: './material-dialogs.scss',
})
export class MaterialEditor {
  readonly data = inject<{ material: Material; description?: string; tags: {name: string}[] }>(MAT_DIALOG_DATA);
  readonly dialog = inject(MatDialogRef<MaterialEditor, Material>);
  private readonly api = inject(MaterialsApi);
  private readonly destroy = inject(DestroyRef);
  readonly form = inject(FormBuilder).nonNullable.group({
    name: [this.data.material.name, [Validators.required, Validators.maxLength(255)]],
    subjectName: [this.data.material.subjectName, [Validators.required, Validators.maxLength(100)]],
  });
  readonly saving = signal(false);
  readonly error = signal('');
  readonly tagName = (tag: {name: string}) => tag.name;
  save(): void {
    if (this.saving()) return;
    const values = this.form.getRawValue();
    const payload = {name: values.name.trim(), subjectName: values.subjectName.trim()};
    if (this.form.invalid || !payload.name || !payload.subjectName) {
      this.form.markAllAsTouched(); this.error.set('Заполните название и предмет в пределах указанной длины.'); return;
    }
    this.error.set(''); this.saving.set(true); this.dialog.disableClose = true;
    this.api.update(this.data.material.id, payload).pipe(takeUntilDestroyed(this.destroy), finalize(() => {
      this.saving.set(false); this.dialog.disableClose = false;
    })).subscribe({next: material => this.dialog.close(material), error: error => this.error.set(this.api.actionError(error))});
  }
}

@Component({selector: 'app-material-delete-confirm', imports: [MatDialogModule],
  template: `<h2 mat-dialog-title>Удалить {{ data.length === 1 ? 'материал' : 'материалы' }}?</h2>
    <mat-dialog-content><p>Файл и его данные будут удалены. Это действие нельзя отменить.</p>
      <ul>@for (item of data; track item.id) { <li>{{ item.name }}</li> }</ul>
      @if (data.length > 1) { <p>Если часть материалов удалить не удастся, они останутся в списке.</p> }
    </mat-dialog-content>
    <mat-dialog-actions align="end"><button [mat-dialog-close]="false">Отмена</button><button class="danger" [mat-dialog-close]="true">Удалить</button></mat-dialog-actions>`,
  styleUrl: './material-dialogs.scss'})
export class MaterialDeleteConfirm { readonly data = inject<Material[]>(MAT_DIALOG_DATA); }
