import { Injectable } from '@angular/core';
import { inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/auth/auth.service';
@Injectable({providedIn: 'root'})
export class PendingMaterial {
  file: File | null = null;
  constructor() { inject(AuthService).sessionChanges.pipe(takeUntilDestroyed()).subscribe(() => this.file = null); }
  take(): File | null { const file = this.file; this.file = null; return file; }
}
