import { Component } from '@angular/core';
// Entry point per singolo componente: importare dal barrel '@ionic/angular' porterebbe nel
// bundle iniziale tutti i componenti Ionic usati anche dalle pagine caricate on demand.
import { IonApp } from '@ionic/angular/ion-app';
import { IonRouterOutlet } from '@ionic/angular/ion-router-outlet';

@Component({
  selector: 'app-root',
  imports: [IonApp, IonRouterOutlet],
  template: `
    <ion-app>
      <ion-router-outlet></ion-router-outlet>
    </ion-app>
  `,
})
export class App {}
