import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular';
import { App } from './app';

describe('App', () => {
  it('crea la shell dell\'applicazione', () => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideIonicAngular({})],
    });
    const fixture = TestBed.createComponent(App);
    expect(fixture.componentInstance).toBeTruthy();
    expect((fixture.nativeElement as HTMLElement).querySelector('ion-app')).not.toBeNull();
  });
});
