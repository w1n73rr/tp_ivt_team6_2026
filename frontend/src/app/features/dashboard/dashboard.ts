import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
})
export class Dashboard {
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
    this.notice = `${section}: раздел пока в разработке.`;
  }
}
