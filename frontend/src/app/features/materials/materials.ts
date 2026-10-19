import { Component, inject, signal, OnInit, DestroyRef } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { MaterialsMock, Material } from './materials.mock';
import { FigmaIcon } from './figma-icon';
import { MaterialsShell } from './materials-shell';
@Component({
  selector: 'app-materials',
  imports: [MaterialsShell, DatePipe, DecimalPipe, FigmaIcon],
  templateUrl: './materials.html',
  styleUrl: './materials.scss',
})
export class Materials implements OnInit {
  private readonly router = inject(Router);
  private readonly source = inject(MaterialsMock);
  private readonly destroy = inject(DestroyRef);
  readonly items = signal<Material[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  mode: 'data' | 'empty' | 'error' = 'data';
  selected = new Set<string>();
  ngOnInit(): void {
    this.load();
  }
  load(): void {
    this.loading.set(true);
    this.error.set('');
    this.items.set([]);
    this.selected.clear();
    this.source
      .list(this.mode)
      .pipe(
        takeUntilDestroyed(this.destroy),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (r) => {
          this.items.set(r.items);
          if (r.items.length) {
            this.selected = new Set(
              ['demo-2', 'demo-3'].filter((id) => r.items.some((item) => item.id === id)),
            );
          }
        },
        error: () => this.error.set('Не удалось загрузить материалы. Попробуйте ещё раз.'),
      });
  }
  scenario(event: Event): void {
    this.mode = (event.target as HTMLSelectElement).value as typeof this.mode;
    this.load();
  }
  toggle(id: string): void {
    this.selected.has(id) ? this.selected.delete(id) : this.selected.add(id);
  }
  toggleAll(): void {
    this.selected.size === this.items().length
      ? this.selected.clear()
      : this.items().forEach((i) => this.selected.add(i.id));
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
