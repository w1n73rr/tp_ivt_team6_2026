import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { MaterialsShell } from '../materials/materials-shell';
import { FigmaIcon } from '../materials/figma-icon';
@Component({
  selector: 'app-dashboard',
  imports: [MaterialsShell, FigmaIcon],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
  private readonly router = inject(Router);
  readonly sections = [
    'Мои материалы',
    'Тесты',
    'Тренировка',
    'Прогресс',
    'История тестов',
    'Профиль',
    'Настройки',
  ];
  notice = '';
  explain(section: string): void {
    if (section === 'Мои материалы') {
      void this.router.navigateByUrl('/materials');
      return;
    }
    this.notice = `${section}: раздел пока в разработке.`;
  }
}
