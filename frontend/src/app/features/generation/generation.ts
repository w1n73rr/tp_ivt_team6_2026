import { generationRequestKey } from './generation-request-key';
import { generationStage, generationFailure, generationStep } from './generation-status';
import { HttpErrorResponse } from '@angular/common/http';
import { AuthService } from '../../core/auth/auth.service';
import { Component, DestroyRef, Injectable, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, forkJoin, timer, exhaustMap, catchError, of, switchMap, map } from 'rxjs';
import { MaterialsShell } from '../materials/materials-shell';
import { MaterialsApi } from '../materials/materials.api';
import { Material } from '../materials/materials.models';
import { GENERATION_SOURCE, GenerationJob, GenerationOptions, GenerationSettings } from './generation-source';

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
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly drafts = inject(GenerationDrafts);
  readonly source = inject(GENERATION_SOURCE);
  private loading?: Subscription;
  private sending?: Subscription;
  private watching?: Subscription;
  private pending?: { signature: string; key: string };
  private activeJob?: GenerationJob;
  private storageKey(): string { return `generation:${this.auth.username() ?? ''}:${this.route.snapshot.paramMap.get('materialId')}`; }
  private persist(): void {
    if (this.source.demo) return;
    try { sessionStorage.setItem(this.storageKey(), JSON.stringify({ pending: this.pending, job: this.activeJob, name: this.name(), settings: this.settings() })); } catch { /* Storage may be unavailable; current request still remains protected. */ }
  }
  private version = -1;
  readonly quizId = signal<string | null>(null);
  readonly stage = signal('');
  readonly currentStep = signal(0);
  readonly progressSteps = ['Подготовка материала', 'Генерация вопросов', 'Проверка и сохранение'];
  readonly connectionError = signal('');
  readonly authRequired = signal(false);
  readonly returnTo = () => '/materials/' + this.route.snapshot.paramMap.get('materialId') + '/generate';
  readonly failureMessage = signal(generationFailure());
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
    this.state.set(success && this.actualCount() === this.settings()?.questionCount && this.actualCount() > 0 ? 'ready' : 'failed');
  }
  backToSettings(): void { if (this.state() === 'failed' || this.source.demo) this.state.set('settings'); }
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
    if (!this.source.demo) {
      try {
        const saved = JSON.parse(sessionStorage.getItem(this.storageKey()) ?? 'null');
        if (saved?.pending?.signature && saved?.pending?.key) {
          this.pending = saved.pending;
          if (saved.settings && typeof saved.name === 'string') {
            this.settings.set(saved.settings); this.name.set(saved.name);
          }
          if (saved.job?.id && saved.job.materialId === material.id) {
            this.activeJob = saved.job; this.state.set('waiting'); this.watch(saved.job); return;
          }
        }
      } catch { /* An invalid local snapshot is never treated as a server status. */ }
    }
    this.state.set('settings');
  }
  retry(): void {
    this.loading?.unsubscribe();
    this.sending?.unsubscribe();
    this.watching?.unsubscribe(); this.pending = undefined; this.activeJob = undefined; this.quizId.set(null); this.version = -1; this.connectionError.set('');
    this.state.set('loading'); this.error.set(''); this.authRequired.set(false);
    this.material.set(null); this.options.set(null); this.settings.set(null);
    this.loading = forkJoin({ detail: this.api.detail(this.route.snapshot.paramMap.get('materialId') || ''), options: this.source.options() })
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: data => this.loaded(data.detail.material, data.options),
        error: error => { if (error instanceof HttpErrorResponse && error.status === 401) this.authRequired.set(true); this.error.set('Не удалось загрузить материал или настройки. ' + this.api.actionError(error)); this.state.set('error'); },
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
    this.state.set('sending'); this.error.set(''); this.connectionError.set('');
    const signature = JSON.stringify({ materialId: material.id, ...settings, name: this.name() });
    if (!this.pending || this.pending.signature !== signature) this.pending = { signature, key: generationRequestKey() };
    this.persist();
    this.sending = (this.source.demo ? this.source.start(material.id, { ...settings }) : this.source.start(material.id, { ...settings }, this.name(), this.pending.key)).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: job => {
        this.state.set('waiting');
        if (!this.source.demo && 'id' in job) { this.activeJob = job; this.persist(); this.stage.set(generationStage(job)); this.currentStep.set(generationStep(job)); this.watch(job); }
      },
      error: error => { if (error instanceof HttpErrorResponse && error.status === 401) this.authRequired.set(true); this.error.set('Не удалось запустить генерацию. Параметры сохранены. Повторите попытку.'); this.state.set('settings'); },
    });
  }
  private watch(initial: GenerationJob): void {
    if (!initial.id || initial.materialId !== this.material()?.id || !this.source.status) {
      this.connectionError.set('Не удалось подтвердить задачу генерации.'); return;
    }
    this.version = initial.version; this.quizId.set(null); this.currentStep.set(generationStep(initial));
    this.watching?.unsubscribe();
    const owner = this.auth.username();
    this.watching = timer(0, 2000).pipe(
      exhaustMap(() => {
        if (this.authRequired()) return of(null);
        if (owner !== this.auth.username() || !this.auth.token) {
          this.authRequired.set(true);
          this.connectionError.set('Сессия завершена. Войдите заново для проверки статуса.'); return of(null);
        }
        return this.source.status!(initial.id).pipe(switchMap(job => {
          if (job.id !== initial.id || job.materialId !== this.material()?.id || !Number.isInteger(job.version) || job.version < this.version) return of(null);
          if (job.status === 'COMPLETED') {
            if (!job.quizId || !this.source.saved) throw new Error('No saved ID');
            return this.source.saved(job.quizId, job.materialId).pipe(map(saved => ({ job, saved })));
          }
          return of({ job, saved: null });
        }), catchError(error => {
          if (error instanceof HttpErrorResponse && error.status === 401) {
            this.authRequired.set(true); this.connectionError.set('Сессия завершена. Войдите заново; задача генерации сохранена.'); return of(null);
          }
          this.connectionError.set('Не удалось проверить статус или прочитать сохранённый тест. Проверяем связь; генерация может продолжаться.'); return of(null);
        }));
      }), takeUntilDestroyed(this.destroyRef),
    ).subscribe(result => {
      if (!result) return;
      const { job, saved } = result;
      this.version = job.version; this.connectionError.set(''); this.stage.set(generationStage(job)); this.currentStep.set(generationStep(job));
      if (job.status === 'FAILED' || job.status === 'COMPLETED' && saved) {
        this.pending = undefined; this.activeJob = undefined; this.persist(); this.watching?.unsubscribe();
        if (saved && saved.questionCount !== this.settings()?.questionCount) { this.failureMessage.set('Не удалось подготовить запрошенное количество вопросов. Повторите генерацию.'); this.actualCount.set(saved.questionCount); this.state.set('failed'); }
        else if (saved) { this.quizId.set(saved.id); this.name.set(saved.name); this.actualCount.set(saved.questionCount); this.state.set('ready'); }
        else { this.failureMessage.set(generationFailure(job.error?.code)); this.state.set('failed'); }
      }
    });
  }
}
