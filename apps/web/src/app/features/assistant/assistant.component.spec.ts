import { TestBed } from '@angular/core/testing';
import { AssistantComponent } from './assistant.component';

describe('AssistantComponent', () => {
  it('aggiunge il messaggio dell\'utente e una risposta segnaposto', () => {
    const component = TestBed.createComponent(AssistantComponent).componentInstance;

    component.draft = '  Quanti clienti ho?  ';
    component.send();

    expect(component.messages()).toEqual([
      { role: 'user', content: 'Quanti clienti ho?' },
      { role: 'assistant', content: "L'assistente non è ancora disponibile." },
    ]);
    expect(component.draft).toBe('');
  });

  it('ignora i messaggi vuoti', () => {
    const component = TestBed.createComponent(AssistantComponent).componentInstance;
    component.draft = '   ';
    component.send();
    expect(component.messages()).toEqual([]);
  });
});
