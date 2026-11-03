import { Injectable } from '@angular/core';
@Injectable({providedIn: 'root'})
export class PendingMaterial {
  file: File | null = null;
  take(): File | null { const file = this.file; this.file = null; return file; }
}
