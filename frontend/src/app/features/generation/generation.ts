import { Component, DestroyRef, Injectable, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, forkJoin } from 'rxjs';
import { MaterialsShell } from '../materials/materials-shell';
import { MaterialsApi } from '../materials/materials.api';
import { Material } from '../materials/materials.models';
import { GENERATION_SOURCE, GenerationOptions, GenerationSettings } from './generation-source';

@Injectable({ providedIn: 'root' })
export class GenerationDrafts {
  readonly values = new Map<string, GenerationSettings>();
  readonly names = new Map<string, string>();
}
@Component({
  selector: 'app-generation', imports: [MaterialsShell, RouterLink, DecimalPipe],
  templateUrl: './generation.html', styleUrl: './generation.scss',
})
export class Generation {
  private readonly api = inject(MaterialsApi);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly drafts = inject(GenerationDrafts);
  readonly source = inject(GENERATION_SOURCE);
  private loading?: Subscription;
  private sending?: Subscription;
  readonly material = signal<Material | null>(null);
  readonly options = signal<GenerationOptions | null>(null);
  readonly settings = signal<GenerationSettings | null>(null);
  readonly state = signal<'loading' | 'settings' | 'sending' | 'waiting' | 'error' | 'ready' | 'failed'>('loading');
  readonly name = signal('');
  readonly actualCount = signal(0);
  setName(value: string): void {
    this.name.set(value);
    if (this.material()) this.drafts.names.set(this.material()!.id, value);
  }
  demoResult(success: boolean, count = this.settings()?.questionCount ?? 0): void {
    if (!this.source.demo || this.state() !== 'waiting') return;
    this.actualCount.set(Math.max(0, Math.min(count, this.settings()?.questionCount ?? 0)));
    this.state.set(success && this.actualCount() > 0 ? 'ready' : 'failed');
  }
  backToSettings(): void { this.state.set('settings'); }
  retryGeneration(): void { if (this.state() === 'failed') { this.backToSettings(); this.start(); } }
  readonly error = signal('');
  readonly difficulties = { EASY: 'Лёгкая', MEDIUM: 'Средняя', HARD: 'Сложная' };
  readonly summaries = { EASY: 'лёгкая', MEDIUM: 'средняя', HARD: 'сложная' };
  readonly types = { SINGLE_CHOICE: 'Один ответ', MULTIPLE_CHOICE: 'Несколько ответов', TRUE_FALSE: 'Верно/неверно' };
  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.retry());
  }
  private loaded(material: Material, options: GenerationOptions): void {
    if (!material || material.id !== this.route.snapshot.paramMap.get('materialId')) {
      this.error.set('Материал не найден или недоступен.'); this.state.set('error'); return;
    }
    if (!options.counts?.length || !options.difficulties?.length || !options.types?.length) {
      this.error.set('Нет доступных параметров генерации.'); this.state.set('error'); return;
    }
    this.material.set(material); this.options.set(options);
    this.name.set(this.drafts.names.get(material.id) ?? material.name.slice(0, 100));
    const saved = this.drafts.values.get(material.id);
    this.settings.set(saved && this.valid(saved, options) ? { ...saved } : { ...options.defaults });
    if (!this.valid(this.settings()!, options)) {
      this.error.set('Не удалось загрузить допустимые настройки.'); this.state.set('error'); return;
    }
    this.state.set('settings');
  }
  retry(): void {
    this.loading?.unsubscribe();
    this.sending?.unsubscribe();
    this.state.set('loading'); this.error.set('');
    this.material.set(null); this.options.set(null); this.settings.set(null);
    this.loading = forkJoin({ detail: this.api.detail(this.route.snapshot.paramMap.get('materialId') || ''), options: this.source.options() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: data => this.loaded(data.detail.material, data.options),
        error: error => { this.error.set('Не удалось загрузить материал или настройки. ' + this.api.actionError(error)); this.state.set('error'); },
      });
  }
  private valid(value: GenerationSettings, options: GenerationOptions): boolean {
    return [5, 10].includes(value.questionCount) && options.counts.includes(value.questionCount)
      && Object.hasOwn(this.difficulties, value.difficulty) && options.difficulties.includes(value.difficulty)
      && Object.hasOwn(this.types, value.questionType) && options.types.includes(value.questionType);
  }
  choose(value: Partial<GenerationSettings>): void {
    if (this.state() !== 'settings' || !this.settings()) return;
    const next = { ...this.settings()!, ...value };
    if (!this.valid(next, this.options()!)) return;
    this.settings.set(next); this.drafts.values.set(this.material()!.id, { ...next }); this.error.set('');
  }
  start(): void {
    if (this.state() !== 'settings') return;
    if (!this.name().trim() || this.name().trim().length > 100) {
      this.error.set('Введите название теста от 1 до 100 символов.'); return;
    }
    this.setName(this.name().trim());
    const material = this.material(), settings = this.settings(), options = this.options();
    if (!material || !settings || !options || !this.valid(settings, options)) {
      this.error.set('Выберите допустимые параметры теста.'); return;
    }
    this.drafts.values.set(material.id, { ...settings });
    this.state.set('sending'); this.error.set('');
    this.sending = this.source.start(material.id, { ...settings }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.state.set('waiting'),
      error: () => { this.error.set('Не удалось запустить генерацию. Параметры сохранены. Повторите попытку.'); this.state.set('settings'); },
    });
  }
}
