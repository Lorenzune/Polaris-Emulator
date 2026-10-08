# Movimento e teletrasporto: allineamento SWF

## Ambito

19 interazioni: 15 effetti e quattro accessori, verificati sui controller decompilati in `decomp0909/scripts/com/sulake/habbo/roomevents/wired_setup`. Il lavoro riguarda card, pacchetti, salvataggio e runtime Polaris. L'importatore resta fuori ambito. Gli accessori di fisica, trasporto utenti e animazione continuano a essere applicati dai servizi di movimento comuni; sono stati allineati i loro campi, origini, validazione e stati della card.

| Interazione | Codice SWF / locale | Campi specifici SWF |
| --- | --- | --- |
| `wf_act_move_rotate` | 4 / 4 | movimento 0..11, rotazione 0..3 |
| `wf_act_teleport_to` | 8 / 8 | teletrasporto rapido 0/1 |
| `wf_act_chase` | 11 / 11 | nessuno |
| `wf_act_flee` | 12 / 12 | nessuno |
| `wf_act_move_to_dir` | 13 / 13 | direzione 0..7, comportamento se bloccato 0..6, blocca su utenti 0/1 |
| `wf_act_move_furni_to` | 16 / 16 | direzione 0/2/4/6, spaziatura 1..5 |
| `wf_act_bot_teleport` | 21 / 21 | nome bot nel testo |
| `wf_act_bot_move` | 22 / 22 | nome bot nel testo |
| `wf_act_set_altitude` | 29 / 39 | altitudine 0..8000 in centesimi, operatore 0..2 |
| `wf_act_furni_to_furni` | 34 / 38 | nessuno; due origini furni e due selezioni comuni |
| `wf_act_furni_to_user` | 35 / 36 | nessuno |
| `wf_act_move_rotate_user` | 42 / 43 | movimento -1..7, rotazione -1..7 oppure 9/10 |
| `wf_act_user_to_furni` | 43 / 37 | modalità cammino 0..2 |
| `wf_act_teleport_to_room` | 44 / 2044 | nessuno; un linker selezionato e origine utenti |
| `wf_act_move_furni_as_group` | 57 / 95 | destinazione utente 0/1, offset X/Y -64..64 |
| `wf_xtra_mov_no_animation` | 6 / 59 | nessuno |
| `wf_xtra_mov_physics` | 7 / 61 | quattro flag 0/1; due origini furni e una utenti separate |
| `wf_xtra_mov_carry_users` | 8 / 58 | modalità trasporto 0/1; origine utenti separata |
| `wf_xtra_anim_time` | 9 / 60 | durata 50..2000 ms, passi di 50 ms |

Per i controller con nomi offuscati, le associazioni complete sono nell'inventario `reports/wired-parity-2026-10-04/inventory.json`. I controller nominati sono `SetFurniAltitude.as`, `TeleportToRoom.as` e `MoveAsGroup.as`.

## Card e pacchetti

`WiredNativeMovementSettings` usa il marker opt-in già esistente `-2026100401`. Gli int specifici seguono il controller SWF; le origini furni/utenti, le selezioni secondarie e il ritardo restano separati. L'emulatore traduce il formato nei campi Polaris esistenti, dopo validazione completa.

Le rotazioni utenti SWF 9/10 corrispondono ai valori locali precedenti 8/9; la conversione evita di reinterpretare i vecchi JSON. L'altitudine in centesimi viene tradotta nella stringa decimale persistita, senza perdere i centesimi. Le risposte restano nel formato letto dalle card Nitro: non è un endpoint per un client SWF originale senza adattamenti.

Le card attivano la selezione manuale solo per origine 100; le due selezioni di furni verso furni e dei gruppi restano distinguibili. Le origini dei gruppi restano sempre visibili anche quando la destinazione è un utente o un selettore. Ripristino e salvataggio mantengono i campi distinti.

Muovi furni e spaziatura usano default validi (0/0 e 0/1). La card altitudine arriva a 80 e usa il default operatore 0 del radio group SWF. L'emulatore ha ora limite comune `Room.MAXIMUM_FURNI_HEIGHT = 80`: il limite vale anche per gli altri percorsi di posizionamento, che altrimenti avrebbero rifiutato o troncato i valori consentiti dal Wired.

La durata animazione parte da 50 ms per nuovi accessori, come lo slider SWF; i valori già salvati restano leggibili. Il campo numerico e lo slider restano sincronizzati. La scelta degli utenti da trasportare è sempre visibile. Le origini degli accessori ammettono anche 900 (tutta la stanza); i quattro flag di fisica e le origini hanno posizioni distinte nel pacchetto nativo.

## Runtime e compatibilità

- Le otto direzioni determinate del movimento furni e utenti usano la stessa convenzione della mappa (`getTileInFront`); corretti asse Y e associazione dei pulsanti Nord/Sud.
- I bersagli di movimento devono essere ancora gli stessi oggetti presenti nella stanza. Un furni rimosso o sostituito non viene mosso attraverso un riferimento obsoleto.
- Il teletrasporto ritardato conserva stanza e generazione di caricamento: dopo cambio stanza o ricaricamento non modifica l'avatar della nuova sessione. Se la destinazione manca, annulla lo stato pendente e ripristina il cammino, rispettando i blocchi Wired/Builder. Il successo azzera il vecchio percorso e aggiorna anche la destinazione del cammino.
- I teletrasporti provenienti da segnali raggruppati controllano stanza e generazione prima di applicarsi.
- Il trasferimento con `wf_room_linker` conserva il flusso normale di ingresso e le protezioni preesistenti contro duplicazione e ingresso incompleto. Salvataggi nuovi selezionano esattamente un linker; un furni di altro tipo viene rifiutato.
- I gruppi nuovi usano destinazione utente/furni e offset, spostando tutti i membri con lo stesso vettore. Ancora stabile: membro con id più basso. Il bordo avanzato viene mosso per primo; collisioni, fisica, utenti trasportati e animazione passano dai servizi comuni. La gestione delle collisioni resta best-effort: un membro bloccato viene saltato.
- I vecchi JSON dei gruppi, che contengono solo la direzione, conservano la modalità precedente. Un salvataggio dalla nuova card passa alla modalità destinazione/offset. Corretto il vecchio passo del gruppo: l'offset 1 di `getTileInFront` significava due caselle, ora il passo è una casella.
- Salvataggi senza marker restano leggibili; nessuna migrazione DB. I caricamenti di dati nulli o malformati tornano a uno stato pulito senza impedire il caricamento della stanza.

## Limiti della verifica

Lo SWF documenta campi, range e stati della UI, ma non include il codice del server Habbo. La scelta dell'ancora del gruppo, la distribuzione casuale, la ricerca di inseguimento/fuga e i dettagli delle collisioni non possono essere certificati 1:1 con quel materiale. La nuova semantica del gruppo e l'ancora sopra sono scelte Polaris esplicite, da confrontare con una traccia del server ufficiale se disponibile. Non si dichiara parità integrale del runtime ufficiale.

## Verifiche

- `WiredNativeMovementParityTest`: conversione delle 19 interazioni, round-trip JSON, salvataggi invalidi senza mutazione, otto direzioni, gruppi con offset, entrambe le selezioni, dati malformati, modalità gruppo precedente, altitudine 80 e default/validazione degli accessori.
- `RoomUnitTeleportTest`: cambio stanza, generazione obsoleta, destinazione mancante, percorso e cammino dopo successo.
- Regressioni: `RoomLinkerTransferTest`, `WiredMovementPayloadGuardTest`, `WiredMovementPhysicsTest`, `WiredMoveStyleHelperTest`, `WiredShortMovementTimingTest`, `RoomItemMovementBehaviorTest`, oltre a timer, ritardi, segnali e filtri.
- Renderer: `WiredNativeMovementSettings.test.ts` (15 casi), più regressioni timer: 19 test passati.
- Client: `WiredMovementParity.test.tsx` (16 casi), più timer e controllo reale del footer in `WiredBaseView.selection.test.tsx`: 28 test passati.
- Emulatore: package Maven completato, 81 test passati nelle suite selezionate sopra. Controlli TypeScript client/renderer e build Vite client completati.

## Prove in stanza

1. Mettere un furni al centro di una zona libera e provare le otto direzioni determinate; poi provare Muovi utente sugli stessi pulsanti. Nord/Sud e diagonali devono concordare.
2. Provare Muovi e ruota, Muovi in direzione con i comportamenti di blocco, inseguimento e fuga. Ripetere con origine selettore e segnale; riaprire le card.
3. Altitudine: Imposta 79,99 e 80, poi Aumenta/Diminuisci di 0,01. Riaprire la card e ricaricare la stanza; verificare che l'altezza non sia tagliata a 40.
4. Gruppo di tre furni: destinazione utente, poi furni secondario, offset X/Y anche negativi. Controllare distanze relative, colori delle selezioni, ostacoli, accessori di fisica e trasporto utenti. Un vecchio gruppo deve continuare a eseguire la modalità direzione prima di essere risalvato.
5. Furni verso furni/utente e utente verso furni: provare tutte le origini e le tre modalità cammino. Riaprire e verificare le selezioni separate.
6. Bot: nome manuale e origini dinamiche; provare cammino e teletrasporto e riaprire le card.
7. Teletrasporto normale/rapido: cambiare stanza mentre è pendente e rimuovere la destinazione. Il vecchio teletrasporto non deve spostare l'utente della nuova stanza né lasciarlo bloccato.
8. Linker gemelli in due stanze: ripetere il passaggio in entrambe le direzioni, anche con più utenti. Dopo l'arrivo deve essere possibile camminare; verificare che ci sia un solo avatar per utente e che una destinazione non disponibile non lasci uno schermo nero.

Le prove live non sono state eseguite in questa sessione. Nessun riavvio, deploy, commit o push.
