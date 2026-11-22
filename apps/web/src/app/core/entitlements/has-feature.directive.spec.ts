import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../api/api-url';
import { HasFeatureDirective } from './has-feature.directive';

@Component({
  imports: [HasFeatureDirective],
  template: `
    <p id="ai" *appHasFeature="'aiAssistant'">assistente</p>
    <p id="reports" *appHasFeature="'reports'">report</p>
  `,
})
class HostComponent {}

describe('HasFeatureDirective', () => {
  it('mostra solo le funzionalità incluse nel piano', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: '/api' }],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#ai')).toBeNull();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/entitlements')
      .flush({ plan: 'pro', maxUsers: 10, features: ['aiAssistant'], aiQuotaMonthly: 100, validUntil: null });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('#ai')).not.toBeNull();
    expect(element.querySelector('#reports')).toBeNull();
  });
});
