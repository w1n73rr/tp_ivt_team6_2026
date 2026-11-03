import { Component, input, inject, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { FigmaIcon } from './figma-icon';
@Component({
  selector: 'app-materials-shell',
  imports: [RouterLink, FigmaIcon],
  template: `<div class="layout">
    <aside>
      <a class="brand" routerLink="/dashboard"><app-figma-icon name="brand" /><b>ИИ-помощник</b></a>
      <nav aria-label="Разделы приложения">
        <a [class.active]="active() === 'dashboard'" [attr.aria-current]="active() === 'dashboard' ? 'page' : null" routerLink="/dashboard"><app-figma-icon name="home" />Главная</a
        ><a [class.active]="active() === 'materials'" routerLink="/materials" [attr.aria-current]="active() === 'materials' ? 'page' : null"
          ><app-figma-icon name="folder" />Мои материалы</a
        ><a [class.active]="active() === 'tests'" [attr.aria-current]="active() === 'tests' ? 'page' : null" routerLink="/tests"><app-figma-icon name="tests" />Тесты</a
        ><span><app-figma-icon name="training" />Тренировка</span
        ><span><app-figma-icon name="progress" />Прогресс</span
        ><span><app-figma-icon name="history" />История тестов</span>
        <hr />
        <span><app-figma-icon name="profile" />Профиль</span
        ><span><app-figma-icon name="settings" />Настройки</span>
      </nav>
      <div class="account">
        <span class="avatar">{{ initial() }}</span>
        <div><b>{{ displayName() }}</b><small>Личный кабинет</small></div>
        <button class="logout" type="button" (click)="logout()">Выйти</button>
      </div>
    </aside>
    <main><ng-content /></main>
  </div>`,
  styleUrl: './materials-shell.scss',
})
export class MaterialsShell {
  readonly active = input<'materials' | 'dashboard' | 'tests'>('materials');
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly displayName = computed(() => this.auth.username() || 'Пользователь');
  readonly initial = computed(() => Array.from(this.displayName())[0].toLocaleUpperCase('ru')); 
  logout(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/login', { replaceUrl: true });
  }
}
