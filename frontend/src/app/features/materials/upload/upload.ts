import { Component, inject, signal, DestroyRef, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MaterialsApi } from '../materials.api';
import { MaterialsShell } from '../materials-shell';
import { FigmaIcon } from '../figma-icon';
@Component({
  selector: 'app-material-upload',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, MaterialsShell, FigmaIcon],
  templateUrl: './upload.html',
  styleUrl: './upload.scss',
})
export class MaterialUpload implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly source = inject(MaterialsApi);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  readonly form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(255)]],
    subject: ['', [Validators.required, Validators.maxLength(100)]],
    customSubject: ['', Validators.maxLength(100)],
    description: ['', Validators.maxLength(2000)],
  });
  readonly file = signal<File | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly fileError = signal('');
  readonly tags = signal<string[]>([]);
  readonly success = signal(false);
  tagInput = '';
  readonly subjects = signal<{ id: string; name: string }[]>([]);
  readonly subjectsError = signal('');
  readonly savedId = signal('');
  ngOnInit(): void {
    this.loadSubjects();
  }
  loadSubjects(): void {
    this.subjectsError.set('');
    this.source
      .subjects()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => this.subjects.set(r.items),
        error: () =>
          this.subjectsError.set(
            'Не удалось получить предметы. Повторите запрос или укажите новый предмет.',
          ),
      });
  }
  get subjectName(): string {
    return (
      this.form.controls.subject.value === '__new'
        ? this.form.controls.customSubject.value
        : this.form.controls.subject.value
    ).trim();
  }
  choose(event: Event): void {
    const input = event.target as HTMLInputElement;
    const files = input.files;
    this.accept(files);
    input.value = '';
  }
  drop(event: DragEvent): void {
    event.preventDefault();
    if (!this.loading()) this.accept(event.dataTransfer?.files ?? null);
  }
  accept(files: FileList | null): void {
    if (this.loading()) return;
    this.error.set('');
    this.fileError.set('');
    this.success.set(false);
    if (!files?.length) return;
    if (files.length !== 1) {
      this.fileError.set('Выберите один файл.');
      return;
    }
    const file = files[0];
    const extension = file.name.split('.').pop()?.toLowerCase();
    if (!['pdf', 'docx', 'txt'].includes(extension ?? '')) {
      this.file.set(null);
      this.fileError.set('Поддерживаются только PDF, DOCX и TXT.');
      return;
    }
    if (file.size === 0) {
      this.file.set(null);
      this.fileError.set('Файл пустой. Выберите другой документ.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.file.set(null);
      this.fileError.set('Размер файла не должен превышать 10 МБ.');
      return;
    }
    this.file.set(file);
    if (!this.form.controls.name.value.trim())
      this.form.controls.name.setValue(file.name.replace(/\.[^.]+$/, ''));
  }
  removeFile(): void {
    if (!this.loading()) {
      this.file.set(null);
      this.fileError.set('');
      this.success.set(false);
    }
  }
  addTag(event?: Event): void {
    event?.preventDefault();
    if (this.loading()) return;
    const tag = this.tagInput.trim();
    if (tag.length > 50 || (tag && !this.tags().includes(tag) && this.tags().length >= 20)) {
      this.error.set('Можно добавить до 20 тегов длиной до 50 символов.');
      return;
    }
    if (tag && !this.tags().includes(tag)) this.tags.update((tags) => [...tags, tag]);
    this.tagInput = '';
  }
  removeTag(tag: string): void {
    if (!this.loading()) this.tags.update((tags) => tags.filter((t) => t !== tag));
  }
  submit(): void {
    if (this.loading() || this.success()) return;
    this.error.set('');
    this.form.markAllAsTouched();
    if (!this.file()) {
      this.fileError.set('Выберите файл для загрузки.');
      return;
    }
    if (this.form.invalid || !this.form.controls.name.value.trim() || !this.subjectName) {
      this.error.set(
        'Проверьте название (до 255 символов), предмет (до 100) и описание (до 2000).',
      );
      return;
    }
    this.addTag();
    if (this.tagInput.trim()) return;
    this.loading.set(true);
    this.source
      .upload(this.file()!, {
        name: this.form.controls.name.value.trim(),
        subjectName: this.subjectName,
        description: this.form.controls.description.value.trim(),
        tags: this.tags(),
      })
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (response) => {
          this.savedId.set(response.material.id);
          this.success.set(true);
        },
        error: (error: unknown) =>
          this.error.set(this.source.uploadError(error) + ' Данные формы сохранены.'),
      });
  }
  cancel(): void {
    if (!this.loading()) void this.router.navigateByUrl('/materials');
  }
}
