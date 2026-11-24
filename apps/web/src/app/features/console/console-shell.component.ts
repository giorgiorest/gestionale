import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterOutlet } from '@angular/router';
import { BrandingService } from '../../core/branding/branding.service';
import { ConsoleAuthService } from './console-auth.service';

/** Cornice della console di piattaforma: separata dall'app delle aziende. */
@Component({
  selector: 'app-console-shell',
  imports: [MatButtonModule, MatIconModule, MatToolbarModule, RouterLink, RouterOutlet],
  template: `
    <mat-toolbar class="topbar">
      <a routerLink="/console" class="brand">
        <img [src]="branding.logoUrl()" alt="" class="logo" />
        <span class="name">{{ branding.name() }}</span>
        <span class="area">Console di piattaforma</span>
      </a>
      <span class="spacer"></span>
      <span class="admin" data-testid="console-admin"
        ><mat-icon>shield_person</mat-icon>{{ auth.name() }}</span
      >
      <button mat-button (click)="auth.logout()" data-testid="console-logout">
        <mat-icon>logout</mat-icon>Esci
      </button>
    </mat-toolbar>
    <main><router-outlet /></main>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      min-height: 100vh;
    }
    .topbar {
      gap: 8px;
      background: var(--mat-sys-surface);
      border-bottom: 3px solid var(--mat-sys-tertiary);
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      color: inherit;
      text-decoration: none;
    }
    .logo {
      height: 32px;
    }
    .name {
      font: var(--mat-sys-title-large);
    }
    .area {
      font: var(--mat-sys-label-large);
      padding: 4px 10px;
      border-radius: var(--mat-sys-corner-full);
      background: var(--mat-sys-tertiary);
      color: var(--mat-sys-on-tertiary);
    }
    .spacer {
      flex: 1;
    }
    .admin {
      display: flex;
      align-items: center;
      gap: 6px;
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ConsoleShellComponent {
  readonly auth = inject(ConsoleAuthService);
  readonly branding = inject(BrandingService);
}

/** Console non disponibile (app desktop o installazione senza console). */
@Component({
  selector: 'app-console-unavailable',
  imports: [MatButtonModule, RouterLink],
  template: `
    <div class="centered">
      <p>
        La console di piattaforma è disponibile solo dal browser, sul server di amministrazione.
      </p>
      <a mat-button routerLink="/">Torna all'applicazione</a>
    </div>
  `,
})
export class ConsoleUnavailablePage {}
