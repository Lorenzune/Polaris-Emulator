# Origini e selettori Wired: blocco del 4 ottobre 2026

Riferimento locale: `decomp0909/scripts/com/sulake/habbo/roomevents/wired_setup/selectors`, `inputsources`, `common/utils/SpiralUtils.as`, `common/NeighborhoodFloor.as` e testi italiani in `habbo-downloader/resource/gamedata/external_flash_texts.txt`. L'importatore rimane fuori scope.

## Campi

Il trasporto Nitro opzionale `-2026100401`, già introdotto per le variabili, viene usato anche per distinguere i parametri specifici dei selettori dalle origini e dai booleani filtro/inversione. Non si tratta di riprodurre i byte del protocollo SWF. La conversione si applica alle 18 definizioni non variabili; le due definizioni con variabile usano la conversione del blocco precedente.

| Selettori | Rappresentazione specifica SWF conservata |
| --- | --- |
| Furni per tipo | Un booleano abbina stato; origine furni separata. La card non forza più selezione manuale quando riapre un'altra origine. |
| Furni selezionati / da segnale, utenti da segnale | Nessun intero specifico; filtro e inversione separati. |
| Utenti per tipo, squadra, oggetto in mano | Un intero specifico. |
| Furni sopra/sotto furni | Tipo di selezione e origine separati. |
| Utenti sopra furni | Origine furni senza interi specifici. |
| Area furni/utenti | Quattro interi X/Y/larghezza/altezza. |
| Vicinato furni/utenti | Target utente/furni, X/Y e maschera a spirale; origine separata. Raggio 10, 441 celle, 14 parole signed 32 bit; raggio piccolo 5. Eliminato il limite locale di 64 offset. |
| Utenti per nome | Testo con nomi separati da tabulazione, massimo 1000 caratteri; persistenza legacy con righe. |
| Utenti per azione | Codice azione e filtro testuale separati (`dance N` per danza, numero per cartello). |
| Utenti per gruppo | ID del gruppo in testo; testo vuoto per gruppo della stanza. |
| Altitudine furni | Centesimi 0–8000 e confronto; conversione nella quota decimale locale. |
| Remoto | Unione/intersezione e quantità casuale di pile; zero indica nessun limite. Filtro e inversione separati. |

## Esecuzione

- L'origine 11 per un trigger restituisce l'utente cliccato, non l'autore del click. Origini sconosciute restituiscono una selezione vuota, evitando un ripiego accidentale sull'autore.
- Il risolutore espone una variante che distingue selezione primaria 100 e secondaria 101. I riferimenti variabili del blocco precedente mantengono il proprio adattamento dei due gruppi.
- I selettori che usano origine 200 vengono eseguiti dopo quelli indipendenti. Durante la valutazione, leggere una dimensione ancora vuota non riesegue ricorsivamente tutti i selettori.
- I contesti creati per valutare selettori conservano il valore corrente delle variabili di contesto e l'attore attivo. Le valutazioni remote condividono il limite di esecuzione dello stack e hanno una guardia contro cicli fra pile.
- Il remoto valuta soltanto i selettori delle pile Wired collegate, combina sia utenti sia furni, deduplica le pile sulla stessa casella e può sorteggiarne una quantità. Non esegue gli effetti ordinari delle pile collegate.
- Il vecchio JSON remoto privo di `nativeSelection` mantiene la precedente logica segnale/furni di ripiego fino a un nuovo salvataggio. Il nuovo comportamento aggiunge campi al JSON senza rendere il vecchio formato illeggibile.
- Per le nuove configurazioni di utenti/furni sopra furni, una selezione conservata ma inattiva non converte più automaticamente origine trigger in selezione manuale. Il vecchio percorso di salvataggio mantiene la compatibilità.

## Verifiche

- Salvataggio nativo, JSON, ricaricamento ed esecuzione del filtro per tipo; un risultato vuoto resta esplicitamente vuoto.
- Maschera a spirale: ordine iniziale, parole signed, tutte le 441 celle e selezione di un furni nella cella esterna (10,10) dopo ricaricamento.
- Origine utente cliccato, gruppi primario/secondario, origini invalide, variabili di contesto e assenza di rientro nella valutazione.
- Remoto: unione/intersezione di utenti e furni dopo ricaricamento, e terminazione di un ciclo fra due pile.
- Pacchetti: campi specifici, origini, flag, gruppi, nomi, cartello/danza, centesimi e campo quantità remoto.
- Card remota: ripristino dei campi, input disabilitato senza limite e rifiuto di quantità oltre signed int.
- Suite mirata Java con regressioni del blocco variabili: 42 test passati. TypeScript e build client/emulatore riusciti.

## Verifica in stanza e limiti

1. Configura due pile di selettori con risultati sovrapposti. In una terza pila collega il remoto a entrambe e un effetto visibile con origine 200. Unione deve comprendere entrambi i risultati; intersezione soltanto gli elementi comuni. Ripeti con utenti, riapri le card e prova un limite di una pila.
2. Collega reciprocamente due remoti: lo stack deve terminare, senza modificare i Wired collegati come se fossero furni selezionati.
3. Nel vicinato seleziona una cella esterna, riapri la card e verifica l'effetto sul furni corrispondente. Prova l'origine furni e poi utente.
4. Configura due gruppi furni differenti per un confronto fra variabili; verifica che il gruppo secondario non diventi quello primario dopo il salvataggio. Un risultato senza corrispondenze deve lasciare l'effetto senza destinatari.

La prova live non è stata eseguita. Le formule e i formati del client SWF non espongono tutta la logica server: ordine/combinazione di più selettori nella stessa pila, snapshot dello stato per tipo, universo della negazione del vicinato e altri casi di contesto richiedono riscontri live; non sono dichiarati certificati contro Habbo.

Gli accessori `wf_xtra_filter_furni`, `wf_xtra_filter_users` e i filtri per variabile richiedono ancora un blocco dedicato per quantità da variabile e relativi ingressi. I filtri/inversioni qui aggiornati sono i campi comuni dei selettori. Non viene dichiarata la parità completa di tutte le interazioni `wf_`.
