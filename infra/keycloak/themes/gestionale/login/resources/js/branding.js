/*
 * Marchio dell'installazione anche nelle pagine di accesso.
 *
 * In produzione Keycloak è sullo stesso dominio della web app (/sso): qui si legge lo stesso
 * /config.json dell'app e si applicano nome, logo e colori del cliente (BRAND_* nel .env), così
 * l'accesso ha l'aspetto dell'applicazione. Senza config.json (es. sviluppo, altra origine)
 * resta il marchio di prodotto del tema.
 */
(function () {
  'use strict';

  var PRODUCT_NAME = 'Civil Beefy';
  var COLOR = /^#[0-9a-fA-F]{6}$/;
  var admin = /\/realms\/piattaforma\//.test(window.location.pathname);

  function apply(branding) {
    var root = document.documentElement.style;
    var header = document.querySelector('#kc-header-wrapper .kc-logo-text span');
    var previous = header ? header.textContent : null;
    var name = (branding.name || PRODUCT_NAME) + (admin ? ' · amministrazione' : '');

    if (COLOR.test(branding.primaryColor || '')) {
      root.setProperty('--gestionale-primary', branding.primaryColor);
    }
    if (COLOR.test(branding.accentColor || '')) {
      root.setProperty('--gestionale-accent', branding.accentColor);
    }
    if (branding.logoUrl) {
      try {
        var logo = new URL(branding.logoUrl, window.location.origin).href;
        root.setProperty('--gestionale-logo-url', 'url("' + logo.replace(/["\\\n]/g, '') + '")');
      } catch (e) {
        // URL non valido: resta il logo di prodotto.
      }
    }
    if (header) {
      header.textContent = name;
    }
    if (previous && document.title.indexOf(previous) >= 0) {
      document.title = document.title.replace(previous, name);
    }
    document.documentElement.classList.add('marchio-pronto');
  }

  function fallback() {
    apply({});
  }

  try {
    window
      .fetch('/config.json', { credentials: 'omit', cache: 'no-cache' })
      .then(function (response) {
        return response.ok ? response.json() : null;
      })
      .then(function (config) {
        apply((config && config.branding) || {});
      })
      .catch(fallback);
  } catch (e) {
    fallback();
  }
  // Rete lenta: l'intestazione non resta mai nascosta.
  window.setTimeout(function () {
    document.documentElement.classList.add('marchio-pronto');
  }, 1500);
})();
