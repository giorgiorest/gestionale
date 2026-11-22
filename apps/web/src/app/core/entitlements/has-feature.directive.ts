import { Directive, TemplateRef, ViewContainerRef, effect, inject, input } from '@angular/core';
import { EntitlementsService } from './entitlements.service';

/**
 * Mostra il contenuto solo se il piano include la funzionalità.
 *
 * Uso: `<ion-item *appHasFeature="'aiAssistant'">…</ion-item>`
 */
@Directive({ selector: '[appHasFeature]' })
export class HasFeatureDirective {
  readonly appHasFeature = input.required<string>();

  private readonly entitlements = inject(EntitlementsService);
  private readonly template = inject(TemplateRef<unknown>);
  private readonly viewContainer = inject(ViewContainerRef);
  private rendered = false;

  constructor() {
    void this.entitlements.load();
    effect(() => {
      const visible = this.entitlements.hasFeature(this.appHasFeature());
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
