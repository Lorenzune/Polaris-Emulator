# Trasferimento dei preset — 8 ottobre 2026

| Preset | Stanza sul server | Proprietario | Arredi | WIRED |
| --- | ---: | ---: | ---: | ---: |
| Katso Merta | 407 | 7 | 415 | 164 |
| Dama | 408 | 7 | 1.158 | 389 |
| Chocolate Cake Crush | 409 | 7 | 943 | 566 |
| Scacchi | 410 | 16 | 1.905 | 838 |

Le configurazioni native sono esportate dal database locale aggiornato l'8 ottobre. Le posizioni e gli stati di gioco provengono dai salvataggi iniziali, evitando di trasferire la partita in corso. Le posizioni degli stack WIRED conservano le correzioni locali. I due tabelloni della dama sono inclusi; il cancello Cake conserva la posizione corretta. I bot non fanno parte degli export.

Gli scacchi includono tutti i 67 WIRED aggiunti durante le correzioni e le 162 assegnazioni permanenti: colori, collegamenti pedina/marcatore e identificativi dei timer. Vengono rimappati anche i valori che rappresentano ID di arredi; i colori 1/2 restano numeri letterali. Le variabili transitorie della partita locale non vengono esportate.

## Importare solo gli scacchi

Copiare questa cartella sul server. Creare la stanza **410**, proprietario **16**, e lasciarla vuota. Non occorre svuotare le altre tre stanze già importate. Occorrono Node.js e npm.

```sh
npm ci
npm test
node transfer.cjs --config "C:/Users/Administrator/Desktop/arcturus/config.ini" --rooms 410 --check
```

Il controllo è di sola lettura. Dopo il backup del database, con l'emulatore arrestato:

```sh
node transfer.cjs --config "C:/Users/Administrator/Desktop/arcturus/config.ini" --rooms 410 --apply
```

Per importare tutti e quattro i preset in stanze vuote, omettere `--rooms` oppure usare `--rooms 407,408,409,410`. È possibile specificare qualsiasi sottoinsieme delle quattro destinazioni. Le stanze e gli utenti devono già esistere con i proprietari indicati.

## Emulatore e base della condizione

Distribuire una build dell'emulatore dal branch `backup/2026-10-08`, che include le classi native e le correzioni dei segnali. Le patch applicate alla JVM locale non aggiornano un vecchio JAR sul server. Per le animazioni distribuire anche il renderer aggiornato.

Per ogni nome ordinario duplicato viene scelto l'ID di `items_base` numericamente più basso, e l'interazione deve essere corretta. Gli scacchi hanno 17 condizioni con interazione opt-in `wf_cnd_anchored_instance_is`: se questa variante non esiste, l'importatore clona la base ordinaria `wf_cnd_stuff_is` con ID più basso, mantenendo tutti i campi grafici e cambiando soltanto l'interazione. La variante esistente viene riutilizzata se presente. Le basi ordinarie non vengono aggiornate. Gli altri preset continuano a utilizzare la condizione ordinaria. Le interazioni già presenti nel client gestiscono la relativa card.

## Transazione e riferimenti

Ogni inserimento di un arredo omette `items.id`: la mappa locale→server usa gli `insertId` effettivi, anche non consecutivi. L'importatore rimappa selezioni, antenne, chiamate agli stack, snapshot, variabili custom e assegnazioni permanenti. Non rimappa coordinate, ritardi o altri numeri letterali.

Tutte le stanze selezionate sono importate nella stessa transazione. Il controllo finale confronta arredi, configurazioni e assegnazioni. Il trasferimento richiede InnoDB per `items`, `rooms`, `room_models_custom` e, per gli scacchi, `items_base` e `room_furni_wired_variables`. La tabella delle variabili deve essere disponibile dalle migrazioni dell'emulatore aggiornato. Un errore annulla anche i nuovi arredi, le assegnazioni e la base clonata. Non vengono eliminati arredi esistenti; una destinazione occupata o con assegnazioni residue viene rifiutata.

Le piante delle stanze e gli ingressi vengono copiati; i nomi delle stanze sul server rimangono invariati. `before-*.json` salva le impostazioni precedenti; `imported-*.json` contiene la mappa degli ID e l'eventuale base creata. Solo stato `committed` e uscita con codice 0 confermano il commit; `pending_commit` richiede una verifica prima di ripetere.

## Verifica locale

`npm test` verifica rimappatura con ID non consecutivi, proprietari diversi, importazione della sola 410 con le altre stanze occupate, 162 assegnazioni, base nativa isolata, rollback e scelta della base con ID minimo. `verify-local.cjs` verifica gli SQL reali usando copie temporanee delle tabelle nella singola connessione locale; non scrive le stanze persistenti:

```sh
node verify-local.cjs --config "/percorso/config.ini-locale"
```

Il database ufficiale non è stato contattato. Dopo l'importazione verificare una partita RAPID con due giocatori, selezione, movimento, passaggio del turno, timer, promozione e reset.

`export-local.cjs` è lo strumento di rigenerazione per il workspace originale: legge i salvataggi in `reports` e il database locale. Non serve sul server e non è necessario per importare il pacchetto già generato.
