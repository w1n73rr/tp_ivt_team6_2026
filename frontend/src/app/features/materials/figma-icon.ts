import { Component, input } from '@angular/core';
const paths = {
  brand: ['M2 8l10-5 10 5-10 5L2 8z', 'M6 10v7c4 3 8 3 12 0v-7', 'M22 8v8'],
  home: ['M3 10l9-7 9 7v10H3V10z', 'M9 20v-7h6v7'],
  folder: ['M3 6h7l2 3h9v11H3V6z'],
  tests: ['M8 6h13M8 12h13M8 18h13', 'M3 5h1v2H3zM3 11h1v2H3zM3 17h1v2H3z'],
  training: ['M14 3L5 14h6l-1 7 9-11h-6l1-7z'],
  progress: ['M3 20h18M6 16V9M12 16V4M18 16v-5'],
  history: ['M3 11a9 9 0 1 1 2 7', 'M3 4v7h7', 'M12 7v5l3 2'],
  profile: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0z', 'M4 21v-2a8 8 0 0 1 16 0v2'],
  settings: ['M3 6h18M3 12h18M3 18h18', 'M7 3v6M16 9v6M9 15v6'],
  upload: ['M12 16V3M7 8l5-5 5 5', 'M4 15v6h16v-6'],
  search: ['M17 10a7 7 0 1 1-14 0 7 7 0 0 1 14 0z', 'M15 15l6 6'],
  filter: ['M3 4h18l-7 8v7l-4 2v-9L3 4z'],
  chevron: ['M6 9l6 6 6-6'],
  test: ['M5 3h14v18H5V3z', 'M8 8h8M8 12h8M8 16h5'],
  edit: ['M14 5l5 5M4 20l1-6L16 3l5 5L10 19l-6 1z'],
  download: ['M12 3v13M7 11l5 5 5-5', 'M4 17v4h16v-4'],
  eye: ['M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z'],
  delete: ['M3 6h18M9 6V3h6v3M5 6l1 15h12l1-15', 'M10 10v7M14 10v7'],
} as const;
@Component({selector: 'app-figma-icon',
 template: `<svg viewBox="0 0 24 24" aria-hidden="true" focusable="false" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
 @for (path of paths(); track $index) { <path [attr.d]="path" /> }</svg>`,
 styles: [':host { display:inline-flex; flex:0 0 18px; width:18px; height:18px; align-items:center; justify-content:center; vertical-align:middle; } svg { display:block; width:100%; height:100%; overflow:visible; }'],
})
export class FigmaIcon {
 readonly name = input<keyof typeof paths>('folder');
 paths() { return paths[this.name()]; }
}
