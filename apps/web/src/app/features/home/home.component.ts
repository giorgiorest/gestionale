import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { AbilitazioniService } from '../../core/abilitazioni/abilitazioni.service';
import { NotificheService } from '../../core/notifiche/notifiche.service';
import { ProfiloService } from '../../core/profilo/profilo.service';

/** Pagina iniziale: saluto, comunicazioni da confermare, accessi rapidi. */
@Component({
  selector: 'app-home',
  imports: [RouterLink, MatButtonModule, MatCardModule, MatIconModule],
  template: `
    <div class="page">
      <div class="page-header">
        <div>
          <h1>{{ greeting() }}{{ firstName() ? ', ' + firstName() : '' }}</h1>
          <span class="subtitle">{{ profile.me()?.tenant?.name ?? '' }}</span>
        </div>
      </div>

      @if (notifications.counts().toAcknowledge > 0) {
        <a class="banner warn" routerLink="/notifiche" data-testid="ack-banner">
          <mat-icon>assignment_late</mat-icon>
          <span>
            Hai {{ notifications.counts().toAcknowledge }} comunicazion{{ notifications.counts().toAcknowledge === 1 ? 'e' : 'i' }}
            da confermare
          </span>
        </a>
      }

      <div class="cards">
        @if (can('customers:read')) {
          <a routerLink="/clienti" class="tile">
            <mat-card appearance="outlined">
              <mat-card-content>
                <mat-icon>groups</mat-icon>
                <h2>Clienti</h2>
                <p class="muted">Anagrafica dei clienti dell'azienda</p>
              </mat-card-content>
            </mat-card>
          </a>
        }
        <a routerLink="/notifiche" class="tile">
          <mat-card appearance="outlined">
            <mat-card-content>
              <mat-icon>notifications</mat-icon>
              <h2>Notifiche</h2>
              <p class="muted">
                @if (notifications.counts().unread > 0) {
                  {{ notifications.counts().unread }} da leggere
                } @else {
                  Nessuna notifica da leggere
                }
              </p>
            </mat-card-content>
          </mat-card>
        </a>
        @if (can('notifications:send')) {
          <a routerLink="/comunicazioni" class="tile">
            <mat-card appearance="outlined">
              <mat-card-content>
                <mat-icon>campaign</mat-icon>
                <h2>Comunicazioni</h2>
                <p class="muted">Avvisi ai dipendenti e presa visione</p>
              </mat-card-content>
            </mat-card>
          </a>
        }
        @if (can('assistant:use') && entitlements.features().has('aiAssistant')) {
          <a routerLink="/assistente" class="tile">
            <mat-card appearance="outlined">
              <mat-card-content>
                <mat-icon>smart_toy</mat-icon>
                <h2>Assistente</h2>
                <p class="muted">Chiedi aiuto all'assistente</p>
              </mat-card-content>
            </mat-card>
          </a>
        }
      </div>
    </div>
  `,
  styles: `
    .banner {
      text-decoration: none;
    }
    .tile {
      text-decoration: none;
      color: inherit;
    }
    .tile mat-card {
      height: 100%;
      transition: background 0.15s;
    }
    .tile:hover mat-card {
      background: var(--mat-sys-surface-container-high);
    }
    .tile mat-icon {
      color: var(--mat-sys-primary);
      font-size: 32px;
      width: 32px;
      height: 32px;
    }
    .tile h2 {
      font: var(--mat-sys-title-medium);
      margin: 12px 0 4px;
    }
    .tile p {
      margin: 0;
    }
  `,
})
export class HomeComponent {
  readonly profile = inject(ProfiloService);
  readonly notifications = inject(NotificheService);
  readonly entitlements = inject(AbilitazioniService);

  readonly firstName = computed(() => (this.profile.me()?.name ?? '').split(' ')[0]);
  readonly greeting = computed(() => {
    const hour = new Date().getHours();
    return hour < 13 ? 'Buongiorno' : hour < 18 ? 'Buon pomeriggio' : 'Buonasera';
  });

  can(permission: string): boolean {
    return this.profile.permissions().has(permission);
  }
}
