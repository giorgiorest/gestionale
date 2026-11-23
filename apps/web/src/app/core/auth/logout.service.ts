import { Component, Injectable, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';
import { LocalDataService } from '../privacy/local-data.service';
import { SyncService } from '../sync/sync.service';
import { AuthService } from './auth.service';

/**
 * Uscita dall'applicazione.
 *
 * Prima di chiudere la sessione cancella i dati personali salvati sul dispositivo per l'uso
 * offline. Se ci sono modifiche non ancora inviate prova a inviarle; se restano (es. offline)
 * chiede conferma, perché uscendo andrebbero perse.
 */
@Injectable({ providedIn: 'root' })
export class LogoutService {
  private readonly auth = inject(AuthService);
  private readonly sync = inject(SyncService);
  private readonly localData = inject(LocalDataService);
  private readonly dialog = inject(MatDialog);

  /** Restituisce false se l'utente ha annullato. */
  async logout(): Promise<boolean> {
    if ((await this.localData.pendingChanges()) > 0) {
      await this.sync.flush().catch(() => 0);
      const pending = await this.localData.pendingChanges();
      if (pending > 0) {
        const ref = this.dialog.open(UnsentChangesDialog, { data: pending, autoFocus: 'dialog' });
        if (!(await firstValueFrom(ref.afterClosed()))) {
          return false;
        }
      }
    }
    await this.localData.clear();
    await this.auth.logout();
    return true;
  }
}

/** Conferma dell'uscita con modifiche non inviate. */
@Component({
  selector: 'app-unsent-changes-dialog',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>Modifiche non inviate</h2>
    <mat-dialog-content>
      <p>
        @if (count === 1) {
          C'è 1 modifica non ancora inviata al server.
        } @else {
          Ci sono {{ count }} modifiche non ancora inviate al server.
        }
      </p>
      <p>
        Uscendo, le modifiche non inviate verranno cancellate da questo dispositivo insieme agli
        altri dati salvati. Per non perderle, ricollegati a internet e riprova a uscire.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false" data-testid="logout-cancel">Annulla</button>
      <button mat-flat-button [mat-dialog-close]="true" data-testid="logout-confirm">
        Esci e cancella
      </button>
    </mat-dialog-actions>
  `,
})
export class UnsentChangesDialog {
  readonly count = inject<number>(MAT_DIALOG_DATA);
}
