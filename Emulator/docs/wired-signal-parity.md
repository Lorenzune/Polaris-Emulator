# Parità SWF di segnali e contesto

Blocco del 5 ottobre 2026. Interazioni aggiornate: `wf_act_send_signal`, `wf_act_neg_send_signal`, `wf_trg_recv_signal`. Verificati i selettori `wf_slc_furni_signal` / `wf_slc_users_signal` e la dichiarazione `wf_var_context`, già coperti dai blocchi precedenti. Importatore fuori ambito.

## Fonti locali e campi

`decomp0909/scripts/com/sulake/habbo/roomevents/wired_setup/actiontypes/SendSignal.as`, `triggerconfs/§_-81g§.as`, `DefaultElement.as` e testi italiani in `habbo-downloader/resource/gamedata/external_flash_texts.txt`.

| Interazione | Campi specifici | Ingressi comuni |
| --- | --- | --- |
| Invio normale/negativo | due booleani: split furni e split utenti | origine antenne, origine furni inoltrati, origine utenti inoltrati; selezione antenne e selezione furni distinte; ritardo |
| Ricezione | nessun intero specifico da salvare | origine antenne: selezione 100 o selettori 200 |
| Selettori da segnale | nessun parametro specifico | filtro e inversione, allineati nel blocco selettori |
| Variabile di contesto | nome e indicazione presenza di valore | trasporto dello stato nel contesto d'esecuzione |

Per l'invio, i gruppi manuali sono 100 (antenne) e 101 (furni inoltrati). I due gruppi restano separati nel pacchetto e nel JSON. Antenne e furni inoltrati possono usare origine trigger 0, selettori 200 e segnale 201; gli utenti anche origine utente cliccato 11 quando il trigger della pila la rende disponibile.

La card di ricezione non rimanda al server il vecchio campo locale `channel`. Il canale interno resta l'ID dell'antenna; i contatori dei collegamenti nella risposta rimangono un'estensione locale di sola lettura. La selezione manuale non è richiesta quando l'origine è un selettore. Le opzioni di split richiedono la conferma descritta da `SendSignal.as`, con i testi originali importati.

## Esecuzione

- Il nuovo invio risolve soltanto le antenne dell'origine scelta, controllando tipo e permanenza in stanza. Un'antenna non valida non diventa un furni inoltrato.
- Lo split utenti usa esclusivamente gli utenti inoltrati, senza aggiungere automaticamente l'autore originale del trigger. L'autore originale rimane nei metadati `originActor`.
- Un gruppo inoltrato vuoto rimane vuoto: il payload esplicito impedisce che origine 201 recuperi accidentalmente attore o furni del trigger. Questo stato è conservato anche dalle esecuzioni ritardate.
- Senza split viene emesso un evento per antenna, con le liste inoltrate complete; lo split produce un evento per destinatario della dimensione selezionata. Con entrambi gli split si usano le coppie utente/furni. Una dimensione vuota con split attivo non produce eventi. I contatori indicano la dimensione del payload emesso.
- Ogni evento ha una copia distinta delle variabili di contesto. La copia viene inoltrata nelle catene e conservata negli snapshot dei ritardi. Una modifica in un ramo non modifica gli altri eventi già creati.
- Il segnale negativo usa il percorso che nega le condizioni della pila ricevente; prima richiamava il medesimo dispatcher del segnale positivo. Questa correzione vale anche per le configurazioni precedenti.
- Restano le guardie già presenti per profondità opzionale e budget delle catene; l'invio non introduce una nuova ricorsione sincrona.

## Compatibilità

Nuovi salvataggi attraverso l'estensione opt-in -2026100401. Il JSON aggiunge `nativeSignal` e, nell'invio, `nativeAntennaSource`; i campi precedenti sono mantenuti. Le configurazioni di invio precedenti conservano il loro percorso legacy fino al nuovo salvataggio. La ricezione mantiene il fallback del canale soltanto per le configurazioni legacy; una selezione nativa vuota non usa un vecchio canale.

Dopo aggiornamento di client ed emulatore, riaprire e salvare i Wired esistenti per attivare il nuovo percorso. Non sono richieste modifiche agli integratori che continuano a usare i pacchetti legacy. Nessun deploy, riavvio, commit o push effettuato in questo blocco.

## Verifiche automatiche

- Java: salvataggio e ricaricamento di invio/ricezione, doppio split senza autore aggiunto, origine utente cliccato, origine antenne da selettori, inoltro da segnale, copie indipendenti del contesto, payload vuoto, rifiuto di origini invalide, dispatcher negativo, snapshot ritardato con payload esplicito, regressioni dei budget e dei filtri/selettori.
- Renderer: due booleani specifici, origini e gruppi indipendenti per entrambi gli effetti, ricezione con zero interi specifici; regressioni sulle variabili.
- Client: ripristino e salvataggio delle due selezioni, origine dell'antenna, utente cliccato, conferma split, campo canale assente dalla ricezione e stato della selezione manuale.
- TypeScript e build client/emulatore.

## Prova in stanza da completare

1. Pila A: trigger normale, invio segnale, antenna selezionata e due furni da inoltrare. Pila B: ricezione dalla stessa antenna, selettore furni da segnale ed effetto visibile con origine 200. Riavvia l'apertura delle card e verifica entrambe le selezioni.
2. Aggiungi due utenti da selettore; confronta senza split, split utenti, split furni ed entrambi. Lo split utenti non deve aggiungere l'autore quando non appartiene al gruppo inoltrato.
3. Prova un gruppo vuoto: origine segnale nella pila ricevente deve restare vuota. Se lo split di quel gruppo è attivo, la ricezione non deve scattare.
4. Assegna una variabile di contesto prima dell'invio, leggila nella pila ricevente e poi inoltra ad una terza pila. Ripeti con ritardo nell'invio e nella ricezione; i valori devono restare quelli della copia pertinente.
5. Metti una condizione verificabile sulla ricezione: il segnale normale esegue con condizione vera, quello negativo con condizione negata. Prova anche una catena circolare e verifica la terminazione secondo i limiti configurati.

Il SWF rende verificabili campi, ingressi e stati delle card, ma non contiene il server Habbo. Cardinalità degli split, attore nello split, contatori, ordine delle emissioni e combinazione delle condizioni richiedono confronto live; non sono dichiarati certificati contro il server ufficiale. La prova live non è stata eseguita e non viene dichiarata la parità completa di tutte le interazioni `wf_`.
