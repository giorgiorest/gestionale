import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

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
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatIconModule, MatInputModule],
  templateUrl: './assistant.component.html',
  styles: `
    .narrow {
      max-width: 820px;
    }
    .messages {
      min-height: 240px;
    }
    .message {
      margin: 8px 0;
      padding: 8px 12px;
      border-radius: var(--mat-sys-corner-medium);
      background: var(--mat-sys-surface-container-high);
      max-width: 80%;
    }
    .message.user {
      margin-left: auto;
      background: var(--mat-sys-primary-container);
      color: var(--mat-sys-on-primary-container);
    }
    .message p {
      margin: 2px 0 0;
    }
    .who {
      font: var(--mat-sys-label-small);
    }
    .input {
      display: flex;
      gap: 12px;
      align-items: center;
      width: 100%;
      padding: 0 8px 8px;
    }
    .grow {
      flex: 1;
    }
  `,
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
