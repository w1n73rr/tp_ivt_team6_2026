import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MaterialsShell } from '../materials/materials-shell';

@Component({
  imports: [MaterialsShell, RouterLink],
  template: '<app-materials-shell active="tests"><h1>Результат теста</h1><a routerLink="/tests">К тестам</a></app-materials-shell>',
})
export class AttemptResultPage {}
