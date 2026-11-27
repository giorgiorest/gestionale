import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule } from '@angular/material/dialog';
import { firstValueFrom } from 'rxjs';

/** Testi della conferma. */
export interface DatiConferma {
  title: string;
  message: string;
  confirm: string;
}

/** Conferma di un'operazione delicata (cancellazioni, operazioni non reversibili). */
@Component({
  selector: 'app-confirm',
  imports: [MatDialogModule, MatButtonModule],
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content
      ><p>{{ data.message }}</p></mat-dialog-content
    >
    <mat-dialog-actions align="end">
      <button mat-button [mat-dialog-close]="false">Annulla</button>
      <button mat-flat-button [mat-dialog-close]="true" data-testid="confirm">
        {{ data.confirm }}
      </button>
    </mat-dialog-actions>
  `,
})
export class DialogoConferma {
  readonly data = inject<DatiConferma>(MAT_DIALOG_DATA);
}

/** Apre la conferma e risolve a true solo se l'utente ha confermato. */
export async function openConfirm(dialog: MatDialog, data: DatiConferma): Promise<boolean> {
  const ref = dialog.open(DialogoConferma, { data });
  return (await firstValueFrom(ref.afterClosed())) === true;
}
