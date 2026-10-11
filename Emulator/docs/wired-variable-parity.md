# Variabili Wired: allineamento dei campi SWF e comportamento locale

Riferimento: sorgenti decompilati locali `decomp0909/scripts/com/sulake/habbo/roomevents/wired_setup`, audit del 4 ottobre 2026. L'importatore delle strutture non fa parte di questa modifica.

## Modifiche

| Interazioni | Campi e comportamento aggiornati |
| --- | --- |
| Variabili utente, furni, stanza, contesto | Ordine dei parametri SWF; disponibilità e presenza del valore; nomi in minuscolo quando salvati. I nomi già memorizzati conservano la capitalizzazione al caricamento. |
| Riferimento | Nome, riferimento condiviso e sola lettura separati; scelta limitata alle variabili condivise disponibili. |
| Eco | Nome e riferimento separati; aggiunto il contesto; eliminato il requisito del connettore di testo; controllo dei cicli prima della risoluzione; modifica di un eco di contesto inoltrata alla sorgente. |
| Assegna / rimuovi variabile | Destinazione, valore, sovrascrittura e origini utente/furni separati; validazione della capacità di creare/rimuovere, distinta dalla scrivibilità del valore. |
| Cambia valore | Destinazione, operazione, modalità dell'operando, valore e riferimento separati, con due gruppi di origini e selezione furni secondaria; aggiunte le operazioni bit 110–122. Le operazioni unarie non richiedono un secondo riferimento. |
| Condizioni esistenza, valore, età | Quantificatore separato, riferimenti e origini separati; valori e durate su tutto l'intervallo signed int; opzioni creazione/ultimo aggiornamento dipendenti dalle capacità della variabile. |
| Selettori utente/furni con variabile | Modalità esistenza/valore fisso/variabile, confronto, operand e riferimento separati; filtro dei selezionati e inversione separati. |
| Trigger variabile modificata | Campi SWF creazione/modifica/cancellazione, maschera incremento/decremento/invariato e quattro origini (stanza, altra stanza, ispezione, esterno). Corretto il caso in cui creazione/cancellazione disattivate ricadevano nel ramo modifica del valore. La card disabilita le opzioni incompatibili con le capacità della variabile. |
| Variabili interne intercettabili | Eventi immediati per le scritture Wired/ispezione; osservazione nel ciclo della stanza delle variabili selezionate dai trigger. Il primo campionamento costituisce la base, senza produrre un falso evento. I destinatari utenti sono identificati dall'oggetto RoomUnit, evitando collisioni dopo il riuso degli slot. |
| Riferimenti condivisi | Le modifiche effettuate tramite una stanza diversa notificano il runtime sorgente. L'origine ispezione/esterno viene conservata; gli effetti Wired innescati producono nuove modifiche di origine stanza. |
| Sistema livelli | Maschera delle otto sottovariabili; campi manuali, lineari ed esponenziali separati come nel SWF. Formula esponenziale senza arrotondamenti intermedi, interpolazione manuale troncata, XP richiesti come differenza fra soglie, progresso azzerato al livello massimo. Ricerca del livello senza allocare un elenco per ogni livello. |
| Variabile giornaliera | Campi SWF tipo 8: nome e codice separati da tabulazione. Supporto locale `wf_var_daily`/layout 115, senza creazione di un item o inserimento nel catalogo. Legge il progresso dei compiti giornalieri locali, senza duplicarne lo stato. |

Il salvataggio usa un'estensione Nitro identificata da `-2026100401`, mantenendo distinti i campi SWF. La lettura delle card e il JSON persistito conservano il formato esistente. Le capacità aggiuntive arrivano in una coda opzionale `-2026100402`. Un client precedente continua a usare i vecchi pacchetti; aggiornare insieme client, render ed emulatore per usare il nuovo salvataggio.

Questa estensione non equivale a usare direttamente i pacchetti del client SWF. Per esempio il riferimento eco trasporta il tipo di destinazione insieme al token. L'adattamento resta esplicito al confine del salvataggio, senza migrare i dati già presenti.

## Verifica automatica

- Test del formato legacy e del nuovo formato, inclusi lettura dei booleani dalla coda e rifiuto di target/valori non validi.
- Assegna, cambia e rimuovi: salvataggio con campi SWF, ricaricamento dal JSON ed esecuzione sui destinatari.
- Operazioni bit: segno, posizioni limite, ricerca inclusiva/esclusiva e assenza di risultati.
- Eco di contesto: lettura e scrittura sulla sorgente; rifiuto di un riferimento a sé stesso.
- Trigger: creazione/cancellazione disattivate non attivano il ramo cambio valore.
- Origini: filtro persistito e ripristino delle origini dopo effetti annidati; una scrittura tramite riferimento condiviso richiama il gestore della stanza sorgente.
- Variabili interne: campionamento iniziale, incremento, modifica da ispezione e assenza di un secondo evento al successivo campionamento.
- Livelli: formule lineari/esponenziali/manuali, limite massimo e interpolazione con un miliardo di livelli senza allocazione proporzionale.
- Giornaliera: codice numerico valido, progresso letto dal gestore locale, invalidazione della cache e capacità di sola lettura.
- Regressioni per condizioni, selettori, quest, persistenza utente e registro delle variabili interne.
- Controllo TypeScript di client e render; test dei pacchetti e dei selettori di variabili; build client.

## Prova in stanza

1. Crea una variabile utente `test_score` con valore. Collega un trigger click a **Assegna variabile**, valore `-7`, utente che innesca. Salva, riapri la card e verifica tutti i campi.
2. In un secondo stack usa **Cambia valore**, somma `3`. Dopo il click il valore deve essere `-4`. Prova una condizione `valore = -4` e collega un effetto visibile.
3. Usa **Rimuovi variabile**. La condizione positiva di esistenza deve diventare falsa e quella negativa vera. Riavvia l'emulatore e riapri le configurazioni salvate.
4. Ripeti con destinazione furni, selezione primaria e riferimento su una selezione secondaria differente. Le due origini non devono scambiarsi dopo il salvataggio.
5. Prova un selettore con esistenza, poi valore fisso, poi un'altra variabile; verifica separatamente filtro e inversione.
6. Prova valore assoluto, NOT e conta bit con modalità operando variabile precedentemente selezionata: il secondo riferimento non deve essere obbligatorio. Con `-1`, conta bit deve restituire `32`.
7. Prova una variabile senza valore: assegnazione/rimozione devono restare disponibili, cambio valore e controllo dell'ultimo aggiornamento devono essere disabilitati.
8. Crea un riferimento condiviso in sola lettura e un eco da una variabile interna. Devono essere leggibili nei confronti; assegnazione/rimozione non devono consentire eco. Prova a riferire un eco a sé stesso: il salvataggio deve essere rifiutato.
9. Nel trigger variabile modificata seleziona solo incremento. Assegnare e cancellare la variabile non devono attivarlo; incrementare un valore esistente deve attivarlo.
10. Seleziona solo origine **Ispezione**: una modifica con `:inspect` deve attivare il trigger, una modifica con un effetto Wired no. Ripeti con **Esterno** e la gestione delle variabili. Con una variabile utente condivisa, crea un riferimento da una seconda stanza e verifica **Un'altra stanza** nella stanza sorgente.
11. Seleziona la variabile interna posizione X: camminare deve produrre il cambio valore; riaprire il trigger o entrare in stanza non deve produrre un incremento spurio dal primo campionamento.
12. Nel sistema livelli usa lineare, passo 100, massimo 5: XP 250 deve dare livello 3, progresso 50%, XP richiesti 100 e rimanenti 50. Con XP 400 il livello è 5 e progresso/XP richiesti/rimanenti sono zero.

## Limiti ancora aperti

- La variabile giornaliera è un adattamento locale di sola lettura, collegato all'ID numerico di `daily_tasks`. Il dump contiene un codice testuale lungo fino a 100 caratteri, ma non descrive la risoluzione server dei codici né le capacità di scrittura. Non va presentata come parità completa con la giornaliera Habbo. Nel furnidata esaminato manca un furni associato al tipo 8; resta da scegliere l'item da usare.
- L'osservazione delle modifiche interne prodotte dal gioco avviene al ciclo della stanza; non intercetta eventuali valori intermedi fra due cicli. Non è una verifica dei tempi di esecuzione del server Habbo.
- Le soglie dei livelli sono limitate al signed int dell'emulatore. Curve esponenziali oltre questo intervallo vengono saturate; non si riproduce l'overflow numerico del client AS3. I nomi e gli identificatori locali delle sottovariabili già salvate restano compatibili.
- Gli altri accessori/effetti della famiglia variabili richiedono ancora un audit distinto; questo blocco non certifica tutta la famiglia `wf_`.
- Le operazioni bit adottano valori signed 32 bit, posizioni 0–31 e risultato `-1` quando una ricerca non trova un bit. I codici e le opzioni provengono dal SWF; queste convenzioni di esecuzione richiedono confronto con un server Habbo, perché il SWF contiene la configurazione, non la logica server.
- Non è stata eseguita una prova live in stanza. I test automatizzati coprono emulatore e protocollo locale, non certificano da soli la parità completa con Habbo.
