import { Component, inject, signal, DestroyRef } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MaterialsMock } from '../materials.mock';
import { MaterialsShell } from '../materials-shell';
import { FigmaIcon } from '../figma-icon';
@Component({
  selector: 'app-material-upload',
  imports: [ReactiveFormsModule, RouterLink, DecimalPipe, MaterialsShell, FigmaIcon],
  templateUrl: './upload.html',
  styleUrl: './upload.scss',
})
export class MaterialUpload {
  private readonly fb = inject(FormBuilder);
  private readonly source = inject(MaterialsMock);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  readonly form = this.fb.nonNullable.group({
    name: ['', Validators.required],
    subject: ['', Validators.required],
    customSubject: [''],
    description: [''],
  });
  readonly file = signal<File | null>(null);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly fileError = signal('');
  readonly tags = signal<string[]>([]);
  readonly success = signal(false);
  tagInput = '';
  failUpload = false;
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
      this.error.set('Заполните название материала и предмет.');
      return;
    }
    this.addTag();
    this.loading.set(true);
    this.source
      .upload(
        this.file()!,
        {
          name: this.form.controls.name.value.trim(),
          subjectName: this.subjectName,
          description: this.form.controls.description.value.trim(),
          tags: this.tags(),
        },
        this.failUpload,
      )
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: () => this.success.set(true),
        error: () =>
          this.error.set(
            'Не удалось выполнить демонстрационную загрузку. Данные формы сохранены, можно повторить.',
          ),
      });
  }
  cancel(): void {
    if (!this.loading()) void this.router.navigateByUrl('/materials');
  }
}
