import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { ProfiloService } from './profilo.service';

/**
 * Mostra il contenuto solo se l'utente ha il permesso.
 *
 * Uso: `<ion-button *appHasPermission="'customers:write'">Nuovo</ion-button>`
 */
@Directive({ selector: '[appHasPermission]' })
export class HaPermessoDirective {
  readonly appHasPermission = input.required<string>();

  private readonly profile = inject(ProfiloService);
  private readonly template = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private rendered = false;

  constructor() {
    void this.profile.load();
    effect(() => {
      const visible = this.profile.hasPermission(this.appHasPermission());
      if (visible && !this.rendered) {
        this.viewContainer.createEmbeddedView(this.template);
        this.rendered = true;
      } else if (!visible && this.rendered) {
        this.viewContainer.clear();
        this.rendered = false;
      }
    });
  }
}
