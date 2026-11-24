import { ROLE_LABELS } from '../../core/profile/profile.service';

export const STATUS_LABELS: Record<string, string> = {
  active: 'Attiva',
  suspended: 'Sospesa',
  expired: 'Scaduta',
};

export const FEATURE_LABELS: Record<string, string> = {
  aiAssistant: 'Assistente AI',
};

export const ACTION_LABELS: Record<string, string> = {
  'tenant.create': 'Azienda creata',
  'tenant.update': 'Dati aggiornati',
  'tenant.suspend': 'Azienda sospesa',
  'tenant.activate': 'Azienda riattivata',
  'license.update': 'Licenza aggiornata',
  'user.create': 'Utente creato',
  'user.enable': 'Utente riattivato',
  'user.disable': 'Utente disattivato',
  'user.role': 'Ruolo cambiato',
  'user.password-reset': 'Nuova password provvisoria',
};

export function roleLabel(role: string | null): string {
  return role ? (ROLE_LABELS[role] ?? role) : '—';
}

/** Sigla da un nome: minuscole, cifre e trattini (es. "Rossi S.r.l." → "rossi-srl"). */
export function slugify(name: string): string {
  return name
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 40)
    .replace(/-+$/g, '');
}

/** Data della scadenza (fine giornata, ora locale) come istante ISO, o null. */
export function endOfDayIso(date: Date | null): string | null {
  if (!date) {
    return null;
  }
  const end = new Date(date);
  end.setHours(23, 59, 59, 0);
  return end.toISOString();
}
