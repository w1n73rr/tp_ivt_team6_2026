import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';
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
        ><span><app-figma-icon name="tests" />Тесты</span
        ><span><app-figma-icon name="training" />Тренировка</span
        ><span><app-figma-icon name="progress" />Прогресс</span
        ><span><app-figma-icon name="history" />История тестов</span>
        <hr />
        <span><app-figma-icon name="profile" />Профиль</span
        ><span><app-figma-icon name="settings" />Настройки</span>
      </nav>
      <div class="account">
        <span class="avatar">М</span>
        <div><b>Мария</b><small>Демо-профиль</small></div>
      </div>
    </aside>
    <main><ng-content /></main>
  </div>`,
  styleUrl: './materials-shell.scss',
})
export class MaterialsShell { readonly active = input<'materials' | 'dashboard'>('materials'); }
