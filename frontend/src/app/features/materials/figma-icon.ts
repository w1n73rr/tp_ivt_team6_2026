import { Component, input } from '@angular/core';
const icons = {
  brand: [
    { src: '/assets/materials/7c0fe.svg', x: 0, y: 0 },
    { src: '/assets/materials/a5eff.svg', x: 6.25, y: 6.25 },
    { src: '/assets/materials/0aa97.svg', x: 10.3, y: 13.8 },
    { src: '/assets/materials/e8e23.svg', x: 28.35, y: 11.35 },
    { src: '/assets/materials/42008.svg', x: 27.3, y: 18.3 },
  ],
  home: [{ src: '/assets/materials/f1a47.svg', x: 1, y: 1 }],
  folder: [{ src: '/assets/materials/5a42b.svg', x: 1, y: 3 }],
  tests: [
    { src: '/assets/materials/3ada5.svg', x: 0, y: 1 },
    { src: '/assets/materials/e0234.svg', x: 4.4, y: 1.4 },
    { src: '/assets/materials/3ada5.svg', x: 0, y: 6 },
    { src: '/assets/materials/e0234.svg', x: 4.4, y: 6.4 },
    { src: '/assets/materials/3ada5.svg', x: 0, y: 11 },
    { src: '/assets/materials/e0234.svg', x: 4.4, y: 11.4 },
  ],
  training: [
    { src: '/assets/materials/d58f2.svg', x: 1, y: 4 },
    { src: '/assets/materials/ad168.svg', x: 4.6, y: 7.6 },
    { src: '/assets/materials/cd46d.svg', x: 6.35, y: 0 },
  ],
  progress: [
    { src: '/assets/materials/0b0b4.svg', x: 0, y: 14 },
    { src: '/assets/materials/8e246.svg', x: 1.85, y: 6.1 },
    { src: '/assets/materials/cc8d4.svg', x: 6.85, y: 1.1 },
    { src: '/assets/materials/5b571.svg', x: 11.85, y: 4.1 },
  ],
  history: [
    { src: '/assets/materials/d58f2.svg', x: 1, y: 2 },
    { src: '/assets/materials/7ced1.svg', x: 6.25, y: 3.25 },
    { src: '/assets/materials/93b40.svg', x: 6.25, y: 7.25 },
  ],
  profile: [
    { src: '/assets/materials/cf7c3.svg', x: 4, y: 1 },
    { src: '/assets/materials/88f00.svg', x: 0, y: 7 },
  ],
  settings: [
    { src: '/assets/materials/8a472.svg', x: 0, y: 3 },
    { src: '/assets/materials/8a472.svg', x: 0, y: 8 },
    { src: '/assets/materials/8a472.svg', x: 0, y: 13 },
    { src: '/assets/materials/1f145.svg', x: 3.2, y: 1.2 },
    { src: '/assets/materials/1f145.svg', x: 8.2, y: 6.2 },
    { src: '/assets/materials/1f145.svg', x: 2.2, y: 11.2 },
  ],
  upload: [{ src: '/assets/materials/0ca70.svg', x: 2, y: 0 }],
  search: [
    { src: '/assets/materials/48b37.svg', x: 0, y: 0 },
    { src: '/assets/materials/f24f0.svg', x: 10, y: 10 },
  ],
  filter: [
    { src: '/assets/materials/c8d45.svg', x: 0, y: 0 },
    { src: '/assets/materials/155f6.svg', x: 3, y: 8 },
    { src: '/assets/materials/d6970.svg', x: 6, y: 16 },
  ],
  chevron: [{ src: '/assets/materials/0122e.svg', x: 0, y: 0 }],
  test: [{ src: '/assets/materials/513a8.svg', x: 0, y: 0 }],
  edit: [{ src: '/assets/materials/78af2.svg', x: 0, y: 0 }],
  download: [{ src: '/assets/materials/91f19.svg', x: 0, y: 0 }],
  eye: [{ src: '/assets/materials/9f581.svg', x: 0, y: 0 }],
  delete: [{ src: '/assets/materials/e9101.svg', x: 0, y: 0 }],
} as const;
@Component({
  selector: 'app-figma-icon',
  template: `<span class="icon" [class.brand]="name() === 'brand'">
    @for (part of parts(); track $index) {
      <img [src]="part.src" alt="" [style.left.px]="part.x" [style.top.px]="part.y" />
    }
  </span>`,
  styles: [
    `
      :host {
        display: inline-flex;
        flex-shrink: 0;
      }
      .icon {
        position: relative;
        width: 18px;
        height: 20px;
        display: block;
      }
      .brand {
        width: 36px;
        height: 36px;
      }
      img {
        position: absolute;
        max-width: none;
        pointer-events: none;
      }
    `,
  ],
})
export class FigmaIcon {
  readonly name = input<keyof typeof icons>('folder');
  parts() {
    return icons[this.name()];
  }
}
