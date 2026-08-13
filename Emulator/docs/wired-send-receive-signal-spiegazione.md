# Spiegazione semplice di Send Signal, Receive Signal e Antenna

Questo documento descrive a parole come devono funzionare i wired `Send Signal`, `Receive Signal` e l'antenna.

## Idea generale

Il sistema funziona come una comunicazione tra due pile wired.

Una pila wired contiene il `Send Signal`. Questa pila decide quando inviare un segnale, quale antenna usare e quali utenti o furni devono essere trasportati dentro quel segnale.

Un'altra pila wired contiene il `Receive Signal`. Questa pila resta in ascolto dell'antenna corretta. Quando arriva un segnale su quell'antenna, lo stack del `Receive Signal` viene eseguito.

Quindi il `Send Signal` non esegue direttamente gli effetti dell'altra pila. Il `Send Signal` crea un segnale. Il `Receive Signal` riceve quel segnale e fa partire la propria pila.

## Antenna

L'antenna è il punto fisico attraverso cui passa il segnale.

Quando un `Send Signal` invia un segnale, lo invia verso una o più antenne. L'antenna scelta determina il canale del segnale.

Il `Receive Signal` ascolta una antenna. Se il segnale arriva sulla stessa antenna, il `Receive Signal` si attiva.

In pratica:

- il `Send Signal` sceglie l'antenna di uscita;
- il `Receive Signal` controlla se quella antenna è tra quelle che sta ascoltando;
- se corrisponde, lo stack del `Receive Signal` parte.

L'antenna può anche cambiare stato visivo quando riceve un impulso. Questo serve solo come animazione o feedback grafico. Non cambia il contenuto del segnale.

## Click manuale sull'antenna

Se un utente clicca direttamente un'antenna, l'antenna può generare un segnale semplice.

In quel caso il segnale nasce dall'antenna stessa. L'utente che ha cliccato diventa l'utente sorgente del segnale, se disponibile.

Questo comportamento è diverso dal `Send Signal`, perché non c'è una lista di furni o utenti inoltrati da una pila wired. È un segnale singolo generato dal click.

## Send Signal

Il `Send Signal` serve a inviare informazioni da una pila wired a un'altra.

Quando viene eseguito, deve fare queste cose:

1. Capire quale antenna usare.
2. Capire quali furni vuole inoltrare.
3. Capire quali utenti vuole inoltrare.
4. Decidere se inviare tutto insieme oppure una esecuzione separata per ogni furni o utente.
5. Creare uno o più segnali.
6. Mandare quei segnali al sistema wired, che poi farà partire i `Receive Signal` compatibili.

## Antenna scelta dal Send Signal

Il `Send Signal` può usare una antenna selezionata nella sua configurazione, oppure può usare una antenna derivata dalla sorgente dell'evento.

La cosa importante è che il segnale parte solo se l'oggetto scelto è davvero una antenna.

Se non viene trovata nessuna antenna valida, il `Send Signal` non invia nulla.

## Furni inoltrati dal Send Signal

Il `Send Signal` può trasportare uno o più furni.

Questi furni possono venire da:

- furni selezionati direttamente nel `Send Signal`;
- furni scelti da un selector;
- furni del trigger;
- furni arrivati da un altro segnale.

Il `Send Signal` non deve perdere l'ordine dei furni e non deve duplicare lo stesso furni più volte nella stessa lista.

## Utenti inoltrati dal Send Signal

Il `Send Signal` può trasportare anche utenti.

Gli utenti possono venire da:

- utente che ha iniziato la catena;
- utente cliccato;
- utenti scelti da un selector;
- utenti arrivati da un altro segnale.

Quando possibile, il sistema mantiene anche l'utente originale che ha iniziato la catena wired. Questo serve per non perdere il riferimento all'utente principale durante passaggi tra più segnali.

## Invio singolo

Se il `Send Signal` non è impostato per inviare un segnale per ogni furni, allora crea un solo segnale.

Quel segnale può contenere una lista completa di furni.

In quel caso, quando un wired nello stack del `Receive Signal` usa la sorgente "furni dal segnale", può ricevere tutta la lista dei furni inoltrati.

Questo comportamento serve quando si vuole passare un gruppo intero in una sola esecuzione.

## Invio per ogni furni

Se il `Send Signal` è impostato per inviare un segnale per ogni furni, allora deve creare una esecuzione separata per ogni furni.

Per esempio, se il `Send Signal` ha quattro furni da inoltrare, deve creare quattro segnali separati.

Ogni segnale rappresenta un solo furni corrente.

Quindi il `Receive Signal` deve essere eseguito quattro volte, una per ogni furni.

In ogni esecuzione:

- il furni corrente è diverso;
- selector e condition devono ragionare solo su quel furni corrente;
- gli effetti devono usare quel furni corrente se sono configurati per usare la sorgente del segnale.

La lista completa dei furni può ancora esistere come informazione interna del segnale, ma non deve essere usata come target principale quando il segnale è stato diviso per furni.

## Invio per ogni utente

Il funzionamento per gli utenti è simile a quello dei furni.

Se il `Send Signal` è impostato per inviare un segnale per ogni utente, allora ogni utente genera una esecuzione separata.

In ogni esecuzione:

- l'utente corrente è diverso;
- i wired che usano la sorgente "utente dal segnale" devono vedere solo quell'utente corrente;
- la lista completa degli utenti resta solo come informazione generale della raffica.

## Invio per ogni furni e per ogni utente insieme

Se sono attivi sia "per ogni furni" sia "per ogni utente", il sistema deve combinare le due cose.

Per ogni utente e per ogni furni viene creato un segnale separato.

Per esempio, con due utenti e quattro furni, vengono create otto esecuzioni.

Se ci sono anche più antenne, ogni combinazione viene mandata a ogni antenna.

## Receive Signal

Il `Receive Signal` è un trigger.

Non sceglie lui quando partire. Parte quando arriva un segnale compatibile.

Quando arriva un segnale, il `Receive Signal` controlla se il segnale è arrivato sulla sua antenna.

Se l'antenna corrisponde, la pila del `Receive Signal` viene eseguita.

Se l'antenna non corrisponde, quello stack viene ignorato.

## Cosa vede lo stack del Receive Signal

Quando il `Receive Signal` parte, viene creata una nuova esecuzione della pila wired.

Questa esecuzione ha il suo contesto separato.

Dentro quel contesto:

- il trigger della pila è il box `Receive Signal`;
- il furni sorgente del segnale è il furni trasportato dal `Send Signal`;
- l'utente sorgente del segnale è l'utente trasportato o l'utente originale della catena;
- i selector possono cambiare i target;
- le condition decidono se quella esecuzione deve continuare;
- gli effetti vengono eseguiti solo se le condition passano.

Questa separazione è fondamentale: se arrivano quattro segnali, devono esserci quattro esecuzioni separate, non una sola esecuzione condivisa.

## Selector nel Receive Signal

I selector nello stack del `Receive Signal` vengono eseguiti prima delle condition e degli effetti.

Se un selector usa il furni dal segnale, deve lavorare sul furni corrente di quella esecuzione.

Per esempio, un selector "utenti nelle vicinanze" impostato su "furni dal segnale" deve controllare gli utenti intorno al furni corrente, non intorno a tutti i furni della raffica.

Se il primo segnale riguarda il furni uno, il selector controlla il furni uno.

Se il secondo segnale riguarda il furni due, il selector controlla il furni due.

E così via.

## Condition nel Receive Signal

Le condition vengono valutate dopo i selector.

Ogni segnale ha la sua valutazione separata.

Se una condition fallisce su un furni, deve bloccare solo quella esecuzione.

Non deve bloccare gli altri segnali della stessa raffica.

Per esempio, se arrivano quattro furni e uno ha un utente sopra, una condition "utenti selezionati uguale a zero" deve far passare solo gli altri tre.

Il risultato corretto è:

- tre esecuzioni passano;
- una esecuzione viene scartata;
- gli effetti partono solo sulle tre esecuzioni valide.

## Effetti nel Receive Signal

Gli effetti nello stack del `Receive Signal` devono usare la sorgente configurata.

Se un effetto è impostato su "furni dal segnale", allora con invio per ogni furni deve usare solo il furni corrente.

Se un effetto è impostato su "utente dal segnale", allora con invio per ogni utente deve usare solo l'utente corrente.

Gli effetti non devono leggere tutta la lista della raffica quando il segnale è stato diviso per furni o per utenti.

## Messaggi e placeholder

I messaggi wired possono usare placeholder come id, nome furni, variabili o utenti.

Quando il segnale è diviso per furni, un placeholder legato al furni dal segnale deve usare il furni corrente.

Quando il segnale non è diviso per furni, un placeholder multiplo può usare tutta la lista dei furni inoltrati.

La stessa regola vale per gli utenti.

Questa distinzione è importante perché un messaggio non deve mostrare tutta la lista se l'esecuzione corrente rappresenta un solo furni.

## Teleport

Il teleport è un caso particolare.

Gli altri effetti possono essere eseguiti più volte, una per ogni segnale valido.

Il teleport invece sposta un utente. Se tre segnali validi provano a teleportare lo stesso utente nello stesso momento, l'utente non può finire su tre caselle diverse.

Per questo il teleport deve trattare la raffica in modo speciale: raccoglie le destinazioni valide e sceglie una destinazione tra quelle.

Questo non cambia il funzionamento del segnale. Cambia solo il modo in cui il teleport risolve il conflitto finale.

## Delay

I segnali e i wired non devono avere cooldown nascosti.

Se serve un ritardo, deve essere quello configurato nello slider del wired o nel trigger temporale.

Il sistema non deve aggiungere pause artificiali tra un segnale e l'altro.

## Cache

Il sistema può usare cache interne per rendere più veloce la ricerca degli stack wired.

Queste cache non devono cambiare il significato dei segnali.

Quando un wired viene salvato, spostato, piazzato o rimosso, la cache della room deve essere invalidata.

Le cache runtime non devono far sembrare una raffica come un solo evento.

In particolare, nessuna cache deve unire più segnali diversi solo perché arrivano nello stesso momento.

## Regola principale

La regola più importante è questa:

Se il `Send Signal` è impostato per inviare un segnale per ogni furni, ogni furni deve generare una esecuzione separata del `Receive Signal`.

Ogni esecuzione deve vedere solo il proprio furni corrente.

Se una condition fallisce, fallisce solo quella esecuzione.

Gli altri furni della raffica devono continuare normalmente.

## Esempio pratico

Immagina quattro furni collegati al `Send Signal`.

Il `Send Signal` è impostato per inviare un segnale per ogni furni.

Il `Receive Signal` ha un selector che controlla gli utenti sopra il furni ricevuto.

La condition dice che devono esserci zero utenti.

Se uno dei quattro furni ha un utente sopra, allora:

- il segnale del primo furni libero passa;
- il segnale del secondo furni libero passa;
- il segnale del terzo furni libero passa;
- il segnale del furni occupato viene bloccato dalla condition.

Quindi gli effetti devono partire tre volte, non una sola e non quattro.

Se tra gli effetti c'è un messaggio, il messaggio deve riferirsi ogni volta al furni corrente.

Se tra gli effetti c'è un teleport, il teleport deve scegliere una delle tre caselle valide.

## Comportamento sbagliato da evitare

Sono comportamenti sbagliati:

- il `Receive Signal` parte una sola volta anche se il `Send Signal` ha inviato più furni separati;
- tutti gli effetti vedono sempre lo stesso furni;
- un placeholder mostra tutta la lista quando dovrebbe mostrare solo il furni corrente;
- una condition fallita blocca tutta la raffica invece di bloccare solo il segnale corrente;
- una cache o un cooldown unisce più segnali arrivati nello stesso momento;
- il teleport schedula più spostamenti sovrapposti e alla fine vince sempre la stessa casella.

## Comportamento corretto finale

Il comportamento corretto è:

Il `Send Signal` invia.

L'antenna identifica il canale.

Il `Receive Signal` ascolta quel canale.

Se il segnale è diviso per furni, ogni furni genera una esecuzione separata.

Se il segnale è diviso per utenti, ogni utente genera una esecuzione separata.

Ogni esecuzione ha il suo furni o utente corrente.

Selector, condition, effetti, messaggi e variabili devono rispettare quell'esecuzione corrente.

Solo i casi che fisicamente non possono applicare più risultati insieme, come il teleport dello stesso utente, devono comprimere il risultato finale scegliendo tra le opzioni valide.
