import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonContent,
  IonFooter,
  IonHeader,
  IonInput,
  IonItem,
  IonLabel,
  IonList,
  IonTitle,
  IonToolbar,
} from '@ionic/angular';

/** Messaggio mostrato nella chat. */
export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

/**
 * Chat con l'assistente AI (placeholder, nessuna chiamata reale).
 * La rotta è protetta dalla funzionalità `aiAssistant`; il backend ricontrolla piano e quota.
 */
@Component({
  selector: 'app-assistant',
  imports: [
    FormsModule,
    IonBackButton,
    IonButton,
    IonButtons,
    IonContent,
    IonFooter,
    IonHeader,
    IonInput,
    IonItem,
    IonLabel,
    IonList,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './assistant.component.html',
})
export class AssistantComponent {
  readonly messages = signal<ChatMessage[]>([]);
  draft = '';

  send(): void {
    const text = this.draft.trim();
    if (!text) {
      return;
    }
    this.messages.update((list) => [
      ...list,
      { role: 'user', content: text },
      { role: 'assistant', content: "L'assistente non è ancora disponibile." },
    ]);
    this.draft = '';
  }
}
