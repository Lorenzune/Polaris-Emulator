# Parità SWF degli accessori di filtro

Implementazione del 5 ottobre 2026 per `wf_xtra_filter_furni`, `wf_xtra_filter_users`, `wf_xtra_filter_furni_by_var` e `wf_xtra_filter_users_by_var`. Importatore fuori ambito.

## Evidenza e campi

Fonti locali: `decomp0909/scripts/com/sulake/habbo/roomevents/wired_setup/addons/SelectorFilter.as`, `addons/§_-L1H§.as` e `uibuilder/presets/sections/ValueOrVariableSection.as`.

| Accessorio | Interi specifici SWF | Riferimenti separati |
| --- | --- | --- |
| Quantità furni/utenti | quantità, modalità fissa/variabile, target della variabile | variabile della quantità, origine furni o utente |
| Ordinamento per variabile | quantità, ordinamento, modalità della quantità, target del riferimento | variabile da ordinare, variabile della quantità, origine furni o utente |

Target SWF: furni 0, utente 1, globale -10, contesto -20. Origini: trigger 0, furni selezionati 100, selettori 200, segnale 201, utente cliccato 11. La selezione manuale è l'unico gruppo di questo accessorio; il vecchio codice locale 101 viene adattato a 100 nel trasporto nativo.

Quantità fissa 1–1000, validata nella card e nel percorso di salvataggio nativo. Quantità da variabile: lettura corrente ad ogni esecuzione, nessun limite artificiale a 10000. Valore zero/negativo, variabile inesistente o origine vuota producono quantità zero. Per origini con più destinatari resta la lettura del primo valore disponibile, già usata dal risolutore locale.

La quantità da variabile espone riferimenti furni, utente, globale e contesto. I campi dipendenti restano visibili e disabilitati quando inattivi. La selezione manuale è richiesta soltanto per un riferimento furni con origine 100; le istruzioni generiche di selezione sono nascoste come nel SWF.

Il picker per l'ordinamento ammette variabili con valore o timestamp leggibili. Gli ordinamenti 0/1 richiedono valore, 2/3 creazione, 4/5 ultimo aggiornamento. La card aggiorna le opzioni disponibili; l'emulatore rifiuta nuovi salvataggi incompatibili ed esclude le metriche non più leggibili durante l'esecuzione. Per variabili interne attualmente sono disponibili gli ordinamenti sul valore.

## Compatibilità ed esecuzione

Si usa l'estensione nativa opt-in già introdotta (-2026100401). Il JSON e gli interi locali degli accessori per variabile restano leggibili. I filtri semplici leggono ancora JSON `{amount: N}`, testo numerico e salvataggi legacy con un solo intero, compreso zero e il precedente limite 10000. Il nuovo JSON mantiene anche `amount` e aggiunge modalità/riferimenti; la risposta dei filtri semplici conserva la quantità come primo intero. Il client aggiornato riconosce sia la vecchia risposta sia i campi aggiuntivi.

La risoluzione della quantità avviene dentro la guardia dei filtri: l'origine selettori non applica ricorsivamente lo stesso filtro. Restano l'ordine per ID degli accessori ordinati, il minimo dei limiti casuali presenti nella pila e la gestione locale dei pareggi per ID. Non sono introdotte modifiche alle formule delle variabili o alle azioni ordinarie.

## Verifica

Test automatici su conversione dei pacchetti dei quattro accessori; range fisso; riferimenti e stati delle card; opzioni di ordinamento; JSON legacy; quantità globale dopo salvataggio e ricaricamento; quantità oltre 10000; valori negativi/mancanti; ordinamento di una variabile con sola data di creazione; lettura dall'origine selettori senza ricorsione; selezioni vuote. Eseguite anche regressioni su variabili, origini e selettori. TypeScript e build client/emulatore riusciti.

## Prova in stanza ancora da eseguire

1. Collega un selettore area a un effetto visibile con origine 200 e un filtro quantità. Imposta 2, riapri la card e verifica che l'effetto riceva due destinatari. Ripeti con utenti.
2. Crea una variabile numerica globale `limite`, scegli quantità da variabile e riferimento globale. Prova 2, 0 e -1 senza risalvare il filtro. Soltanto 2 deve produrre destinatari.
3. Ripeti con un valore sul furni selezionato e poi con origini selettori e segnale. Una selezione vuota o una variabile assente deve restare vuota, senza ripiego sull'utente che innesca.
4. Assegna valori diversi a tre destinatari; prova ordinamento crescente/decrescente e limite 1. Ripeti con creazione e ultimo aggiornamento, riaprendo ogni card dopo il salvataggio.
5. Verifica anche pile contenenti più filtri e pareggi: il SWF espone campi e capacità, non tutta la logica server Habbo. Combinazione dei filtri, scelta fra più riferimenti e pareggi non sono certificati contro il server ufficiale.

Nessun riavvio o deploy effettuato. Non viene dichiarata la parità completa di tutti i `wf_`.
