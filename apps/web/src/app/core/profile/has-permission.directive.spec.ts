import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { API_URL } from '../api/api-url';
import { HasPermissionDirective } from './has-permission.directive';

@Component({
  imports: [HasPermissionDirective],
  template: `
    <p id="read" *appHasPermission="'customers:read'">leggi</p>
    <p id="write" *appHasPermission="'customers:write'">scrivi</p>
  `,
})
class HostComponent {}

describe('HasPermissionDirective', () => {
  it('mostra solo ciò che i permessi dell\'utente consentono', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: API_URL, useValue: '/api' }],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#read')).toBeNull();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/me')
      .flush({
        userId: 'u1',
        username: 'lettore.demo',
        name: 'Laura Lettrice',
        email: null,
        tenant: null,
        roles: ['viewer'],
        permissions: ['customers:read'],
      });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(element.querySelector('#read')).not.toBeNull();
    expect(element.querySelector('#write')).toBeNull();
  });
});
