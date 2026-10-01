# Eclipse — specyfikacja

Wersja 2.0, wrzesień 2026. Źródło prawdy o funkcjach aplikacji. Zmiany tylko za zgodą użytkownika.

## 1. Cel i zakres

Eclipse to osobisty planer szkolny na danych z Librusa, w formie aplikacji na Androida. Odpowiada na trzy pytania: co mnie czeka (plan, sprawdziany, zadania), jak stoję z ocenami (w procentach, bo tak liczy szkoła) i gdzie robi się trudno (ostrzeżenia z konkretnymi podpowiedziami).

- Jeden użytkownik: uczeń, konto ucznia w Librus Synergia.
- Teraz: aplikacja na Androida (plik .apk), która sama pobiera dane z Librusa przez internet. Bez serwera i bez hostingu — komputer służy tylko do budowania aplikacji.
- Później: wersja na komputer (sekcja 16). Kod ma to ułatwiać, ale teraz jej nie budujemy.
- Interfejs po polsku. Nazwa aplikacji: Eclipse.

## 2. Założenia z wywiadu

- Konto: ucznia, jedno. Logowanie loginem do Synergii (do potwierdzenia).
- Dane: plan lekcji z zastępstwami i odwołaniami, terminarz (sprawdziany, kartkówki, wycieczki, dni wolne, inne wpisy), oceny zwykłe i punktowe, frekwencja, zadania domowe, uwagi, ogłoszenia, wiadomości (tylko odczyt), szczęśliwy numerek.
- Platforma: najpierw aplikacja na Androida z Android Studio, wersja na komputer później. Nic nie jest hostowane na komputerze użytkownika.
- Język: Kotlin. Użytkownik wspominał na początku o Pythonie, ale aplikacje na Androida pisze się w Kotlinie; Python wymagałby dodatkowej warstwy.
- Synchronizacja: automatycznie w tle co kilka godzin oraz ręcznie („Odśwież dane”, przeciągnięcie w dół).
- Powiadomienia: na telefonie, tworzone przez samą aplikację po synchronizacji.
- Użytkownik: początkujący programista, pracuje w Android Studio.
- Wygląd: glassmorphism, motyw jasny i ciemny z przełącznikiem, kolor akcentu do wyboru w ustawieniach.
- Nawigacja: pasek boczny z zakładkami Pulpit (ekran startowy), Kalendarz, Sprawdziany, Oceny, Statystyki, Ważne, Skrzynka, Ustawienia.
- Kalendarz: widoki miesiąc, tydzień, dzień, lista; domyślnie tydzień. Plan lekcji wbudowany jako bloki godzinowe. Kolor według typu wydarzenia — z lewej kolor typu, od prawej przejście w kolor przedmiotu (pomysł użytkownika). Ważne lekcje oznaczane automatycznie i ręcznie. Panel z prawej („Przybornik”) z etykietami i szablonami do przeciągania na kalendarz. Własne wydarzenia.
- Sprawdziany: osobna zakładka z odliczaniem; w kalendarzu też wyraźnie wyróżnione.
- Oceny: średnia zwykła, bez wag (szkoła nie używa wag; na części przedmiotów są oceny punktowe), wyrażona w %. Wartości od użytkownika: 6 = 100%, 5 = 90%, 4 = 75% (użytkownik nie jest pewien), 3 = 50%, 2 = 40%, 1 = poniżej 40%. Karty przedmiotów ze szczegółami. Kalkulator „co jeśli”.
- Statystyki: frekwencja ogólna i z przedmiotów, spóźnienia i nieusprawiedliwione nieobecności, wykres średniej w czasie, obciążenie sprawdzianami w kolejnych tygodniach, zapas nieobecności do 50%.
- Important: reguły z sekcji 8, trzy poziomy, konkretne podpowiedzi.
- Bez eksportu do Google Calendar.

### Otwarte kwestie — zapytaj użytkownika, gdy dojdziesz do danego miejsca
1. Czy 4 to na pewno od 75%? Czy średnia jest zaokrąglana przed porównaniem z progiem?
2. Ile procent liczy się jedynka (domyślnie 0%) i czy plusy i minusy coś zmieniają (domyślnie nie)? Zasady bywają w statucie szkoły albo w przedmiotowych zasadach oceniania (PZO).
3. Czy przy ocenach punktowych liczy się średnia z procentów, czy suma punktów? Pomoże porównanie z procentem, który pokazuje Librus.
4. Wersja Androida na telefonie użytkownika (Etap 0).
5. Jeśli użytkownik loguje się tylko e-mailem (Konto LIBRUS), pomóż znaleźć login do Synergii albo zbadaj inną drogę.

## 3. Architektura i instalacja

```
Telefon z Androidem
┌─────────────────────────────────────────────┐
│ Eclipse (.apk)                              │
│                                             │
│  Ekrany (Jetpack Compose)                   │
│     ↕                                       │
│  ViewModel → repozytoria → Room, DataStore  │
│     ↑                                       │
│  SyncWorker (WorkManager, co 3 h)           │
│     ↕                                       │
│  moduł :core (czysty Kotlin, bez Androida)  │
│  LibrusSource / DemoSource, obliczenia      │
│     ↓                                       │
│  powiadomienia lokalne                      │
└──────────────────────┬──────────────────────┘
                       │ HTTPS
                       ▼
                Librus Synergia
```

- Wszystkie dane zostają na telefonie (baza Room). Aplikacja działa bez internetu, pokazując ostatnie dane z godziną synchronizacji.
- Komputer służy tylko do pracy w Android Studio. Nic nie działa na nim na stałe.
- Instalacja: z Android Studio przez kabel USB (debugowanie USB) albo na emulatorze. Taka instalacja idzie przez ADB, którego nie obejmuje nowa weryfikacja deweloperów Google (od 30.09.2026 tylko w kilku krajach, globalnie od 2027 roku). Plik .apk instalowany bez kabla może wtedy wymagać dodatkowych kroków.

## 4. Dane z Librusa

### 4.1 Źródła
- Librus nie udostępnia uczniom oficjalnego API. Aplikacja korzysta z nieoficjalnych dróg, z których korzystają też inne projekty:
  - API JSON Synergii: `https://synergia.librus.pl/gateway/api/2.0/`. Znany przebieg logowania (do sprawdzenia w rekonesansie): `GET https://api.librus.pl/OAuth/Authorization?client_id=46&response_type=code&scope=mydata`, potem `POST` na `https://api.librus.pl/OAuth/Authorization?client_id=46` z polami `action=login`, `login`, `pass`, potem `GET https://api.librus.pl/OAuth/Authorization/Grant?client_id=46`, a dalej zapytania do gateway z ciasteczkami sesji. Znane zasoby to m.in. `Grades`, `Grades/Categories`, `Grades/Comments`, `PointGrades`, `PointGrades/Categories`, `Attendances`, `Attendances/Types`, `Timetables`, `Calendar/Substitutions`, `HomeWorks`, `HomeWorks/Categories`, `Lessons`.
  - Konto ucznia połączone z Kontem LIBRUS (logowanie e-mailem) nie otwiera sesji drogą loginu Synergii — Synergia odsyła do portalu (rekonesans 30.09.2026). Dlatego Eclipse loguje się jak aplikacja mobilna Librusa: `portal.librus.pl/konto-librus/redirect/dru` → formularz e-mail + hasło (z tokenem CSRF) → kod z przekierowania `app://librus?code=…` → `POST portal.librus.pl/oauth2/access_token` → `GET portal.librus.pl/api/v3/SynergiaAccounts` (konta ucznia z `accessToken`) → zasoby `https://api.librus.pl/2.0/…` z nagłówkiem `Authorization: Bearer`. Ekran logowania pyta o e-mail i hasło do Konta LIBRUS.
  - Wiadomości działają w osobnym serwisie (`https://wiadomosci.librus.pl/api/`) — sprawdź w rekonesansie.
  - Strony HTML Synergii (parsowane Jsoup, tak jak robi to librus-apix) — plan B dla danych, których nie ma w API (np. uwagi albo szczęśliwy numerek).
- Wzorce do nauki: szkolny-android (Kotlin; obsługuje z Librusa m.in. plan, terminarz, oceny, wiadomości, zadania, uwagi i frekwencję), librus-apix (Python), librus-api-rewrited (JavaScript). Ucz się z nich, ale pisz własny kod (licencje, np. GPL).
- Nic z tego nie jest oficjalne i może przestać działać po zmianach w Librusie. Dlatego: interfejs `DataSource`, testy na zapisanych i zanonimizowanych odpowiedziach, czytelne błędy, a awaria jednego zbioru danych nie psuje reszty.
- Konta z weryfikacją dwuetapową mogą się nie logować tą drogą.
- Librus w przeszłości zablokował publikację zewnętrznej aplikacji w Google Play. Eclipse jest prywatny i nie trafia do żadnego sklepu.

### 4.2 Rekonesans (Etap 1a)
Kod rekonesansu leży w `:core` (`recon/`), a uruchamia go tymczasowy ekran „Rekonesans Librusa” w aplikacji (zmiana z 30.09.2026 zamiast programu na komputerze i pliku `librus-dev.properties`). Użytkownik wpisuje login i hasło — trzymane tylko w pamięci, nigdzie niezapisywane — a aplikacja pokazuje raport z przyciskiem „Kopiuj raport”; użytkownik wkleja go Claude. Raport zawiera strukturę i liczby, nigdy treść ani nazwiska. Sprawdza:
1. Czy logowanie działa (bez wypisywania tokenów i ciasteczek).
2. Każdy zasób: czy odpowiada, ile rekordów, jakie pola, jeden przykładowy rekord z zamaskowanymi danymi osobowymi.
3. Oceny: symbole (1–6 z plusami i minusami, np, bz, „+”, „−” itp.), oceny punktowe i ich postać (punkty, maksimum), czy przedmioty się dublują, czy jest informacja „licz do średniej”, oceny proponowane, śródroczne i roczne, czy opisy ocen zawierają procenty albo punkty, czy Librus podaje własną średnią lub procent przedmiotu.
4. Terminarz: nazwy kategorii w tej szkole (sprawdzian, kartkówka, praca klasowa…), czy wpis ma przedmiot, numer lekcji, godzinę.
5. Plan lekcji: godziny lekcji (dzwonki), oznaczenie zastępstw i odwołań.
6. Frekwencja: typy i ich znaczenie w tej szkole.
7. Daty półroczy i roku szkolnego, numer ucznia w dzienniku oraz skąd pobrać szczęśliwy numerek, uwagi, ogłoszenia i wiadomości.

Wynik: `docs/librus-rekonesans.md` (bez danych osobowych) z tabelą: zbiór danych → zasób → mapowanie kategorii i symboli → braki. Potem stop i akceptacja użytkownika.

### 4.3 Synchronizacja
- `SyncWorker` (WorkManager): okresowo co 3 h (ustawienie 1–12 h), tylko przy dostępnym internecie, w godzinach 6:00–22:00. Do tego ręcznie („Odśwież dane”, przeciągnięcie w dół; nie częściej niż co 2 minuty) i przy otwarciu aplikacji, jeśli dane mają ponad godzinę.
- Zakres:
  - oceny i frekwencja: cały rok szkolny;
  - plan lekcji: poprzedni, bieżący i następny tydzień;
  - terminarz: poprzedni, bieżący i dwa następne miesiące;
  - zadania domowe: od 7 dni wstecz do 30 dni naprzód;
  - wiadomości, ogłoszenia, uwagi: nowe od ostatniej synchronizacji; treść wiadomości pobierana raz;
  - szczęśliwy numerek: raz dziennie, rano.
- Jedna synchronizacja naraz (unikalna praca w WorkManagerze). Błąd jednego zbioru nie przerywa reszty. Każde uruchomienie trafia do `sync_runs` (czas, status, liczby rekordów, błędy bez danych wrażliwych).
- Sesję z Librusem używaj ponownie, dopóki działa; loguj się od nowa dopiero po jej wygaśnięciu.
- Po 3 nieudanych próbach z rzędu: baner w aplikacji, jedno powiadomienie o problemie i dłuższe odstępy między próbami.
- Pierwsza synchronizacja (pusta baza) tylko zapisuje stan i nie tworzy powiadomień.

### 4.4 Wykrywanie zmian
- Każdy rekord ma stabilny klucz `sourceKey`: ID z Librusa, a gdy go brak — skrót z pól identyfikujących (np. data + przedmiot + numer lekcji + typ).
- Nowy klucz → rekord „nowy”. Zmienione pola (np. przeniesiona data sprawdzianu) → „zmieniony”, z historią zmian. Rekord, który zniknął z Librusa → „usunięty w Librusie” (zostaje przez 14 dni, pokazany jako przekreślony).
- Pola czasu: `firstSeenAt`, `lastSeenAt`, `changedAt`.
- „Nowe od ostatniej wizyty”: aplikacja zapamiętuje w DataStore, kiedy ostatnio oglądano dany ekran.

## 5. Model danych

Baza Room na telefonie (nazwy orientacyjne):
- `subjects` — nazwa, skrót (do wąskich kolumn kalendarza), kolor (nadpisywalny), `isDifficult`, `averageMethod` (`AUTO` | `MEAN_PERCENT` | `POINTS_SUM`), ukryty.
- `lessons` — data, numer lekcji, początek, koniec, przedmiot, nauczyciel, sala, status (`NORMAL` | `SUBSTITUTION` | `CANCELLED`), opis zmiany.
- `school_events` (terminarz) — data, numer lekcji lub godzina (opcjonalnie), przedmiot (opcjonalnie), kategoria z Librusa, typ (`TEST` | `QUIZ` | `TRIP` | `DAY_OFF` | `OTHER`), tytuł, opis, status (`ACTIVE` | `CHANGED` | `REMOVED`), historia zmian.
- `homework` — przedmiot, data zadania, termin, tytuł, opis.
- `grades` — przedmiot, półrocze, rodzaj (`REGULAR` | `POINT` | `PROPOSED` | `SEMESTER` | `FINAL` | `DESCRIPTIVE`), symbol, punkty, maksimum, procent i jego źródło (`POINTS` | `DESCRIPTION` | `TABLE` | `NONE`), czy liczona do średniej, kategoria, opis, nauczyciel, data, powiązanie z poprawą.
- `attendance` — data, numer lekcji, przedmiot, typ z Librusa, kategoria (`PRESENT` | `ABSENT` | `ABSENT_EXCUSED` | `LATE` | `RELEASED` | `SCHOOL_DUTY` | `OTHER`).
- `notes` (uwagi) — data, nauczyciel, kategoria, rodzaj (`POSITIVE` | `NEGATIVE` | `NEUTRAL`), treść.
- `announcements`, `messages` — data, autor lub nadawca, tytuł, treść, `readAt`.
- `lucky_numbers` — data, numer.
- `custom_events` — tytuł, start, koniec, cały dzień, kolor, notatka, przedmiot (opcjonalnie), szablon.
- `labels` — nazwa, kolor. `label_assignments` — etykieta, cel (`LESSON` | `SCHOOL_EVENT` | `CUSTOM_EVENT` | `SUBJECT_ALL_LESSONS`), odniesienie do celu, notatka. Lekcję identyfikuj przez (data, numer lekcji, przedmiot), bo ID z Librusa bywa niestabilne.
- `event_templates` — nazwa, domyślny czas trwania, kolor lub etykieta.
- `notifications` — typ, tytuł, treść, cel nawigacji, `postedAt`, `readAt` (centrum powiadomień w aplikacji).
- `sync_runs`.

Ustawienia są w DataStore (wartości domyślne w 12.8), dane logowania osobno i zaszyfrowane (10.3). Rekordy z Librusa mają `sourceKey`, `firstSeenAt`, `lastSeenAt`, `changedAt`.

## 6. Oceny i obliczenia

### 6.1 Co wchodzi do średniej
- Liczymy: zwykłe oceny 1–6 (z plusami i minusami) oraz oceny punktowe.
- Nie liczymy: symboli bez wartości (np, bz, nb, zw itp.), samodzielnych „+” i „−” (np. za aktywność; można to zmienić w ustawieniach), ocen opisowych, ocen proponowanych, śródrocznych i rocznych (te pokazujemy osobno) oraz ocen oznaczonych w Librusie jako nieliczone do średniej.
- Poprawy: domyślnie liczymy obie oceny, chyba że Librus oznacza pierwszą jako nieliczoną. Ustawienie: „licz obie” / „licz tylko poprawę” / „licz lepszą”.

### 6.2 Procent pojedynczej oceny
W tej kolejności:
1. Ocena punktowa: `p = punkty / maksimum × 100`.
2. Procent z opisu oceny (np. „85%” albo „17/20 pkt”) — domyślnie wyłączone. Zaproponuj włączenie, jeśli rekonesans pokaże, że nauczyciele tak wpisują.
3. Zwykła ocena: tabela przeliczeń podana przez użytkownika:

| Ocena | 6 | 5 | 4 | 3 | 2 | 1 |
|---|---|---|---|---|---|---|
| % | 100 | 90 | 75 | 50 | 40 | 0 |

- Wartości dla ocen 6–2 podał użytkownik. To te same liczby co progi w 6.4, więc np. sama czwórka daje dokładnie 75%, czyli prognozę 4.
- Jedynka to „poniżej 40%”, a do średniej potrzebna jest jedna liczba: domyślnie 0% (dolna granica przedziału), do zmiany w ustawieniach.
- Plusy i minusy domyślnie nie zmieniają wartości (4+ = 4− = 75%). W ustawieniach każdy symbol może dostać własną wartość.
- Symbol spoza tabeli: użyj wartości oceny bez znaku i zapisz ostrzeżenie w logu.
- Tabela jest edytowalna, z przyciskiem „Przywróć domyślne”.
- Przy każdej ocenie zapisuj i pokazuj źródło procentu: punkty, opis albo tabela.

### 6.3 Średnia przedmiotu
Liczona osobno dla okresów: I półrocze, II półrocze, cały rok.
- `MEAN_PERCENT` (domyślna): zwykła średnia arytmetyczna procentów wszystkich liczonych ocen, bez wag.
- `POINTS_SUM` (tylko gdy wszystkie liczone oceny przedmiotu są punktowe): `Σ punktów / Σ maksimów × 100`.
- `AUTO` oznacza `MEAN_PERCENT`.
- Jeśli Librus podaje własną średnią lub procent przedmiotu, zapisz go i pokaż obok. Przy różnicy powyżej 1 pp pokaż dyskretną informację: „Librus liczy inaczej — sprawdź metodę w ustawieniach przedmiotu”.
- Format: jedno miejsce po przecinku („78,4%”). Brak liczonych ocen → „Brak ocen”, nigdy 0%.
- Średnia ogólna (na górze zakładki Oceny): średnia ze średnich przedmiotów, które mają oceny.

### 6.4 Prognozowana ocena
- Progi (edytowalne): ≥ 100% → 6, ≥ 90% → 5, ≥ 75% → 4, ≥ 50% → 3, ≥ 40% → 2, < 40% → 1.
- Ustawienie „Zaokrąglaj średnią do pełnych procentów przed porównaniem z progiem” — domyślnie wyłączone.
- Pokazuj odległość do następnego progu („do 4 brakuje 3,2 pp”) i zapas nad obecnym („zapas 1,8 pp”).
- Kolory średnich i kafelków ocen według progu, spójne w obu motywach: 6 i 5 — odcienie zieleni, 4 — morski, 3 — żółty, 2 — pomarańczowy, 1 — czerwony.

### 6.5 Kalkulator „co jeśli”
Dla wybranego przedmiotu i okresu. Niczego nie zapisuje w danych.
- Tryb „Dodaj oceny”: dodajesz hipotetyczne oceny (zwykłą, punkty z maksimum albo %) i widzisz nową średnią oraz prognozę.
- Tryb „Cel”: „chcę mieć co najmniej X% (albo ocenę Y)”. Dla `MEAN_PERCENT` przy n ocenach o sumie procentów S:
  - wymagany wynik jednej kolejnej oceny: `p = X·(n + 1) − S`;
  - gdy `p > 100`: minimalna liczba ocen po 100%: `k = ⌈(X·n − S) / (100 − X)⌉` (dla X < 100); cel 100% jest osiągalny tylko wtedy, gdy wszystkie oceny mają 100%;
  - gdy `p ≤ 0`: komunikat „Masz zapas — nawet 0% nie obniży średniej poniżej X%”.
- Dla `POINTS_SUM` przy sprawdzianie za M punktów: `wymagane punkty = X/100 · (ΣM + M) − Σ punktów`.
- Wynik pokazuj też jako najniższą zwykłą ocenę z tabeli, która go spełnia (np. 68% → 4).
- Ta sama funkcja zasila podpowiedzi w zakładkach Sprawdziany i Important.

### 6.6 Średnia w czasie i trend
- Seria do wykresu: dla każdego dnia, w którym przybyła ocena, średnia ze wszystkich ocen do tego dnia (w danym okresie).
- Trend: średnia dziś minus średnia sprzed 28 dni, o ile wtedy były co najmniej 3 liczone oceny.

## 7. Frekwencja

- Mapowanie typów frekwencji z Librusa na kategorie powstaje w rekonesansie i jest edytowalne. Domyślnie do limitu nieobecności liczą się nieobecności nieusprawiedliwione i usprawiedliwione. Nie liczą się: spóźnienia (to obecność), zwolnienia, nieobecności z przyczyn szkolnych (np. zawody).
- Frekwencja % (ogólna i z przedmiotu; dla półrocza i roku) = (lekcje z wpisem − nieobecności liczone) ÷ lekcje z wpisem × 100. Jeśli Librus podaje własny procent, porównaj i zapisz różnicę w logu.
- Liczniki: spóźnienia, nieobecności nieusprawiedliwione (z listą: data, lekcja, przedmiot), nieobecności usprawiedliwione.
- Zapas nieobecności do 50% (dla przedmiotu, w bieżącym półroczu):
  - planowane lekcje ≈ lekcje, które już się odbyły + lekcje z typowego tygodnia planu od jutra do końca półrocza, bez znanych dni wolnych (z terminarza i z listy w ustawieniach: przerwa świąteczna, ferie);
  - limit = ⌊planowane ÷ 2⌋, zapas = limit − nieobecności liczone;
  - pokazuj „możesz opuścić jeszcze ok. N lekcji” z dopiskiem, że to szacunek;
  - daty półroczy z Librusa, a gdy ich nie ma — z ustawień.
- Podpowiedź w interfejsie: nieobecność przekraczająca połowę zajęć z przedmiotu może oznaczać nieklasyfikowanie.

## 8. Important — ostrzeżenia

Nazwa zakładki w aplikacji: „Ważne” (zmiana 1.10.2026 na prośbę użytkownika). W kodzie, w `PROGRESS.md` i dalej w tej specyfikacji funkcja nazywa się Important. Trzy poziomy: Krytyczne (czerwony), Uwaga (pomarańczowy), Do obserwacji (żółty). Poziom przedmiotu to najwyższy poziom spośród uruchomionych reguł; karta pokazuje wszystkie powody. Progi są edytowalne w ustawieniach.

| Reguła | Poziom | Domyślnie |
|---|---|---|
| Średnia poniżej progu oceny 2 | Krytyczne | < 40% |
| Proponowana ocena 1 lub zagrożenie od nauczyciela | Krytyczne | — |
| Frekwencja z przedmiotu poniżej 50% albo zapas nieobecności ≤ 0 | Krytyczne | — |
| Średnia w przedziale oceny 2* | Uwaga | 40–49,9% |
| Świeża ocena 1 lub 2 (albo wynik < 50%) do poprawy | Uwaga | ostatnie 14 dni |
| Frekwencja blisko granicy | Uwaga | < 60% albo zapas ≤ 3 lekcje |
| Spadek średniej | Do obserwacji | ≥ 5 pp w 28 dni |
| Średnia tuż nad progiem oceny 3* | Do obserwacji | 50–53% |
| Przedmiot oznaczony ręcznie jako trudny | Do obserwacji | — |
| Sprawdzian lub kartkówka z przedmiotu, który ma już ostrzeżenie | podnosi „Do obserwacji” do „Uwaga” i dodaje powód z odliczaniem | ≤ 7 dni |

\* Reguły dodane, żeby poziomy „Uwaga” i „Do obserwacji” miały sens przy średnich. Każdą regułę można wyłączyć w ustawieniach.

- „Do poprawy”: jeśli Librus pokazuje, że ocena została poprawiona, reguła się nie uruchamia. Użytkownik może też ręcznie oznaczyć ocenę jako „poprawiona / pomiń”.
- Karta przedmiotu: poziom (kolor, ikona i słowo), nazwa, średnia %, prognoza, powody z konkretami (data, wartość), podpowiedzi, akcje: „Oznacz jako trudny” / „Usuń oznaczenie”, „Ukryj ten powód” (wraca, gdy dane się zmienią), przejście do ocen przedmiotu. Sortowanie: poziom, potem najniższa średnia.
- Podpowiedzi powstają z szablonów (bez AI), na przykład:
  - „Żeby wyjść na 3 (50%), z najbliższego sprawdzianu (wt 14.10) potrzebujesz co najmniej 68% — w ocenach zwykłych to 4.”
  - „Jedna ocena nie wystarczy: potrzebujesz co najmniej 3 ocen po 100%. Zapytaj nauczyciela o poprawę.”
  - „Jedynka z kartkówki z 02.10 — zapytaj o termin poprawy.”
  - „Z chemii możesz opuścić jeszcze ok. 2 lekcje do końca półrocza.”
  - „Sprawdzian z fizyki za 3 dni, a to przedmiot z ostrzeżeniem.”
- Pusty stan: „Nic nie wymaga uwagi. Najbliższy sprawdzian: …”.

## 9. Powiadomienia

- Lokalne: tworzy je sama aplikacja po synchronizacji w tle. Nie ma serwera push.
- Kanały powiadomień Androida (użytkownik może je też wyłączać w ustawieniach systemu): Oceny, Sprawdziany i kartkówki, Przypomnienia, Zmiany w planie, Ważne, Szczęśliwy numerek, Skrzynka, Problemy z synchronizacją.
- Typy, każdy z przełącznikiem w ustawieniach aplikacji:
  - nowa ocena — „Nowa ocena z matematyki: 4 (75%)”;
  - nowy albo przeniesiony sprawdzian lub kartkówka;
  - przypomnienie o sprawdzianie dzień wcześniej (domyślnie o 18:00);
  - zmiana w planie na dziś lub jutro (zastępstwo, odwołana lekcja);
  - nowe ostrzeżenie „Krytyczne”;
  - szczęśliwy numerek równy mojemu numerowi w dzienniku;
  - nowa wiadomość, nowe ogłoszenie, nowa uwaga.
- Przypomnienia planuj jako jednorazowe zadania WorkManagera z opóźnieniem. Mogą przyjść kilka minut później — to akceptowalne. Bez „dokładnych alarmów”, które wymagają dodatkowych uprawnień.
- Godziny ciszy (domyślnie 22:00–7:00): powiadomienia z tego czasu idą rano jako jedno zbiorcze.
- Więcej niż 3 nowe oceny w jednej synchronizacji → jedno powiadomienie zbiorcze (grupa z podsumowaniem).
- Kliknięcie w powiadomienie otwiera właściwy ekran (deep link do trasy nawigacji).
- Zgoda na powiadomienia (Android 13+, `POST_NOTIFICATIONS`): prośba po pierwszym zalogowaniu, poprzedzona krótkim ekranem z wyjaśnieniem. Gdy użytkownik odmówi — informacja w Ustawieniach z przyciskiem do ustawień systemu.
- Centrum powiadomień w aplikacji (dzwonek): lista ostatnich zdarzeń, także gdy powiadomienia systemowe są wyłączone.

## 10. Budowa aplikacji

### 10.1 Moduły
- `:core` — czysty Kotlin/JVM, zero zależności od Androida (to go czyni przenośnym na później):
  - `model/` — klasy domenowe: przedmiot, lekcja, wpis terminarza, zadanie, ocena, frekwencja, uwaga, ogłoszenie, wiadomość, szczęśliwy numerek;
  - `source/DataSource.kt` — interfejs: logowanie, plan na tydzień, terminarz i zadania w zakresie dat, oceny, frekwencja, uwagi, ogłoszenia i wiadomości od danej chwili, treść wiadomości, szczęśliwy numerek, dane ucznia;
  - `source/librus/` — `LibrusSource`: HTTP (OkHttp z obsługą ciasteczek), JSON (kotlinx.serialization), HTML w razie potrzeby (Jsoup), mapowanie na modele;
  - `source/demo/` — `DemoSource` (patrz Etap 1);
  - `calc/` — obliczenia z sekcji 6–8;
  - `recon/` — rekonesans (raport ze struktury odpowiedzi Librusa).
- `:app` — Android:
  - `ui/` — motyw (`EclipseTheme`, tokeny, szkło), wspólne komponenty, ekrany z sekcji 12, logowanie;
  - `data/` — Room (encje, DAO), repozytoria, DataStore z ustawieniami;
  - `sync/` — `SyncWorker`, `ReminderWorker`, wykrywanie zmian, planowanie zadań;
  - `notify/` — kanały, budowanie powiadomień, godziny ciszy;
  - `security/` — magazyn danych logowania.
- Zależności składa ręczny `AppContainer` (bez Hilta). Ekrany: ViewModel + `StateFlow` z niezmiennym stanem.

### 10.2 Wspólny element kalendarza
Kalendarz, Pulpit i Sprawdziany korzystają z jednego modelu `CalendarItem`: rodzaj (`LESSON` | `TEST` | `QUIZ` | `HOMEWORK` | `TRIP` | `DAY_OFF` | `OTHER` | `CUSTOM`), tytuł, początek, koniec, cały dzień, przedmiot (z kolorem i skrótem), kolor typu, status (`NORMAL` | `SUBSTITUTION` | `CANCELLED` | `CHANGED` | `REMOVED`), etykiety z notatkami, `isImportant`, liczba dni do wydarzenia, `isNew`, szczegóły.

### 10.3 Bezpieczeństwo
- Dane logowania: DataStore zaszyfrowany biblioteką Tink (np. `androidx.datastore:datastore-tink`), klucz główny w Android Keystore. Nie używaj przestarzałego `EncryptedSharedPreferences`.
- Wyklucz dane logowania z kopii zapasowej Androida (`dataExtractionRules`); resztę danych kopia może obejmować.
- Tylko HTTPS. Bez analityki i bez zewnętrznych serwerów. W release: R8 i wyłączone logi debug.
- „Wyloguj i usuń dane” czyści dane logowania, bazę i zaplanowane zadania.

## 11. Wygląd — system wizualny

Wymagania użytkownika są stałe: glassmorphism, motyw jasny i ciemny z przełącznikiem, kolor akcentu do wyboru, kolory typów wydarzeń z przejściem w kolor przedmiotu. Resztę możesz dopracować, ale zmiany pokaż użytkownikowi w Etapie 2a.

Motyw przewodni wynika z nazwy: zaćmienie — ciemna tarcza, świecąca korona, nocne niebo. Wyrazisty jest jeden element (11.7), reszta interfejsu jest spokojna i uporządkowana.

### 11.1 Tło i szkło
- Ciemny motyw („noc”): tło gradientem od #0D1230 do #1B2150, 2–3 mocno rozmyte plamy światła w kolorze akcentu (jak korona), delikatny szum, żeby gradient nie tworzył pasów.
- Jasny motyw („świt”): tło od #EEF1FA do #F8F9FD, plamy akcentu słabsze.
- Szkło przez bibliotekę Haze (rozmycie tego, co pod spodem): tło jako źródło, panele z efektem rozmycia 16–24 dp i półprzezroczystym tintem (ciemny: biel 6–10% krycia; jasny: biel 55–70%), cienka ramka 1 dp (ciemny: biel 12%; jasny: granat 8%).
- Trzy poziomy powierzchni: duże panele (pasek boczny, Przybornik, górny pasek, arkusze) — z rozmyciem; karty na ekranach — bez rozmycia, tylko półprzezroczyste tło; okna dialogowe — najmniej przezroczyste.
- Pełne rozmycie działa od Androida 12. Na starszych i przy ustawieniu „Mniej przezroczystości” — matowe, mocniej kryjące powierzchnie bez rozmycia. Ta wersja też ma wyglądać dobrze.
- Tekst: #E8EAF6 i #A3A9CC w ciemnym motywie, #141A3A i #50577A w jasnym. Kontrast tekstu co najmniej 4,5:1 (WCAG AA). Gdy szkło pod tekstem jest zbyt przezroczyste, zwiększ krycie tła zamiast dodawać cienie pod tekstem.
- Aplikacja rysuje się pod paskami systemu (edge-to-edge); treść nie może wchodzić pod pasek stanu ani nawigacji — obsłuż wcięcia.

### 11.2 Kolor akcentu
- Presety: Korona #E9B949 (domyślny), Fiolet #8B5CF6, Błękit #3B82F6, Morski #14B8A6, Róż #EC4899, Zieleń #22C55E oraz własny kolor (próbnik albo kod hex).
- Akcent zasila: aktywną zakładkę, główne przyciski, zaznaczenia, plamy światła w tle i pierścień zaćmienia. Nie zasila kolorów typów wydarzeń.
- Schemat kolorów Material 3 buduj z koloru akcentu (paleta tonalna) i uzupełniaj własnymi tokenami. Kolory z tapety (dynamic color) wyłączone, żeby akcent zawsze był ten wybrany. Zmiana działa od razu.
- W jasnym motywie tekst i ikony w kolorze akcentu używają przyciemnionej wersji z kontrastem co najmniej 4,5:1.

### 11.3 Kolory typów wydarzeń (domyślne, edytowalne)

| Typ | Kolor | Ikona (Material Symbols Rounded) |
|---|---|---|
| Sprawdzian, praca klasowa, test | #EF4444 czerwony | fact_check |
| Kartkówka | #F97316 pomarańczowy | bolt |
| Zadanie domowe | #3B82F6 niebieski | menu_book |
| Wydarzenie szkolne (wycieczka, apel, zebranie, inne) | #14B8A6 morski | flag |
| Dzień wolny | #22C55E zielony | wb_sunny |
| Własne wydarzenie | #A78BFA lawendowy | star |
| Lekcja | neutralny (kolor tekstu, niskie krycie) | — |

Importuj tylko potrzebne ikony jako wektory, zamiast całej biblioteki ikon. Mapowanie kategorii z Librusa na typy powstaje w rekonesansie i jest edytowalne.

### 11.4 Kolory przedmiotów
Paleta 14 dobrze rozróżnialnych barw, dobrana do obu motywów, przypisywana automatycznie (deterministycznie po nazwie przedmiotu) i edytowalna w ustawieniach. Żaden przedmiot nie dostaje domyślnie koloru identycznego z kolorem sprawdzianu.

### 11.5 Blok wydarzenia i lekcji
- Lewa krawędź: pasek 3–4 dp w kolorze typu, pełne krycie.
- Tło: poziomy gradient (`Brush.horizontalGradient`) od koloru typu (ok. 15–20% krycia) po lewej, przez przezroczystość, do koloru przedmiotu (ok. 35–45% krycia) po prawej. Bez rozmycia.
- Sprawdzian: ikona typu, pogrubiony tytuł, delikatna poświata w kolorze typu i znacznik odliczania („za 3 dni”). Kartkówka: to samo, ze słabszą poświatą. Zadanie: bez poświaty.
- Lekcja: neutralny pasek, przejście w kolor przedmiotu, sala i nauczyciel mniejszym tekstem. Ważna lekcja: obwódka lub poświata w kolorze typu albo etykiety oraz kropka etykiety.
- Zastępstwo: przerywana ramka i znacznik „zastępstwo”. Odwołana: 50% krycia, przekreślona nazwa, znacznik „odwołana”.
- Kolor nigdy nie jest jedynym nośnikiem informacji: zawsze towarzyszy mu ikona albo słowo.

### 11.6 Typografia
- Nagłówki i duże liczby (procenty, odliczanie, logo): Unbounded — szeroki, geometryczny krój pasujący do motywu kosmosu. Używaj oszczędnie.
- Tekst interfejsu: Manrope.
- Oba kroje jako pliki w `res/font` (licencja OFL), żeby działały offline. Sprawdź polskie znaki (ą ć ę ł ń ó ś ź ż); jeśli któryś krój ich nie ma, wybierz inny z pełnym latin-ext i opisz zmianę użytkownikowi.
- Liczby w tabelach i statystykach z cyframi o stałej szerokości (`fontFeatureSettings = "tnum"`).
- Rozmiary w `sp`, żeby działało powiększanie czcionki w systemie. Skala typograficzna o stałym stosunku (np. 1,25).
- Bez CAPS LOCKA w etykietach, bez ozdobnych etykiet nad nagłówkami, bez numeracji „01 / 02 / 03” tam, gdzie treść nie jest sekwencją, bez strzałek doklejanych do tekstu przycisków.

### 11.7 Jeden wyrazisty element: zaćmienie
Na Pulpicie: ciemna tarcza z koroną w kolorze akcentu (rysowana na `Canvas`). Wokół tarczy pierścień postępu, w środku odliczanie do najbliższego sprawdzianu (w dniach, a w ostatniej dobie w godzinach) i nazwa przedmiotu. Im bliżej sprawdzianu, tym bardziej tarcza zakrywa koronę. Gdy w najbliższych 14 dniach nie ma sprawdzianów: pełna korona i napis „Brak sprawdzianów w najbliższych 2 tygodniach”. Ta sama tarcza z koroną to logo, ikona aplikacji (adaptacyjna, z wersją monochromatyczną) i ekran startowy.

### 11.8 Ruch
Oszczędnie. Jedna animacja wejścia: korona rozjaśnia się przy otwarciu Pulpitu. Poza tym animacje tylko jako odpowiedź na działanie (wysunięcie panelu, upuszczenie etykiety, zmiana widoku). Nie animuj każdej karty. Gdy w systemie animacje są wyłączone, aplikacja też ich nie pokazuje.

### 11.9 Teksty i dostępność
- Po polsku, w `strings.xml`, zwykłymi zdaniami. Przycisk mówi, co zrobi („Odśwież dane”, „Dodaj wydarzenie”, „Zapisz ustawienia”), a potwierdzenie używa tego samego słowa („Zapisano ustawienia”).
- Błędy mówią, co się stało i co zrobić: „Nie udało się zalogować do Librusa. Sprawdź login i hasło. Jeśli masz włączoną weryfikację dwuetapową, logowanie z tej aplikacji może nie działać.”
- Puste stany zachęcają do działania: „Brak własnych wydarzeń w tym tygodniu. Przeciągnij szablon z Przybornika albo dotknij wolnego miejsca w kalendarzu.”
- Opisy dla czytnika ekranu (TalkBack) przy ikonach, cele dotyku co najmniej 48 dp, każda akcja przeciągania ma alternatywę bez przeciągania.

## 12. Ekrany

### 12.0 Układ i nawigacja
- Pasek boczny (wysuwany z lewej, szklany): otwiera go ikona menu w lewym górnym rogu (gest przeciągnięcia od krawędzi może kolidować z systemowym gestem „wstecz”). Zawiera logo z tarczą, zakładki z plakietkami, a na dole Ustawienia, przełącznik motywu i czas ostatniej synchronizacji.
- Plakietki: liczba nowych ocen przy Oceny, nieprzeczytanych przy Skrzynce, ostrzeżeń krytycznych przy Ważne. Gdy są ostrzeżenia krytyczne, kropka pojawia się też na ikonie menu.
- Górny pasek: menu, tytuł ekranu, dzwonek, „Odśwież dane” ze stanem (animacja podczas synchronizacji, baner przy błędzie). W Kalendarzu dodatkowo przycisk Przybornika.
- Przeciągnięcie w dół odświeża dane na Pulpicie, w Kalendarzu, Sprawdzianach i Ocenach.
- Szeroki ekran (tablet, telefon poziomo): pasek boczny i Przybornik mogą być stale widoczne.
- Jeśli po testach na telefonie wysuwany pasek okaże się niewygodny, zaproponuj dolny pasek skrótów — tylko za zgodą użytkownika.
- W trybie demo widoczny znacznik „Dane przykładowe”.

Kalendarz, widok tygodnia:
```
┌────────────────────────────────┐
│ [≡] Kalendarz      [↻][!][P]   │
│ ‹ 6–10 paź ›    [M][T][D][L]   │
│      pn   wt   śr   cz   pt    │
│ 8:00 ▌Mat ▌Pol ▌Fiz ▌Ang ▌WF   │
│ 8:55 ▌Ang ▌Fiz*▌Che ▌Mat ▌Pol  │
│ 9:50 ▌His ▌WF  ▌Mat ▌Bio ▌Inf  │
│10:45 ▌Pol ▌Mat ▌His ▌Fiz ▌Che  │
│ …                              │
└────────────────────────────────┘
```
Legenda: [≡] pasek boczny, [↻] Odśwież dane, [!] powiadomienia, [P] Przybornik, [M][T][D][L] widoki (T aktywny), ▌ pasek koloru typu, * sprawdzian na tej lekcji.

Pasek boczny otwarty (kalendarz przyciemniony):
```
┌────────────────────┬───────────┐
│ ◐ Eclipse          │░░░░░░░░░░░│
│                    │░░░░░░░░░░░│
│ Pulpit             │░░░░░░░░░░░│
│ Kalendarz          │░░░░░░░░░░░│
│ Sprawdziany        │░░░░░░░░░░░│
│ Oceny              │░░░░░░░░░░░│
│ Statystyki         │░░░░░░░░░░░│
│ Ważne          2   │░░░░░░░░░░░│
│ Skrzynka       3   │░░░░░░░░░░░│
│ ────────────────   │░░░░░░░░░░░│
│ Ustawienia         │░░░░░░░░░░░│
│ Motyw: ciemny      │░░░░░░░░░░░│
│ Synchr.: 10:32     │░░░░░░░░░░░│
└────────────────────┴───────────┘
```

Przybornik otwarty (panel z prawej):
```
┌───────────┬────────────────────┐
│░░░░░░░░░░░│ Przybornik         │
│░░░░░░░░░░░│                    │
│░░░░░░░░░░░│ Etykiety           │
│░░░░░░░░░░░│ ● Ważne            │
│░░░░░░░░░░░│ ● Przynieść        │
│░░░░░░░░░░░│ ● Powtórzyć        │
│░░░░░░░░░░░│ + Nowa etykieta    │
│░░░░░░░░░░░│                    │
│░░░░░░░░░░░│ Szablony           │
│░░░░░░░░░░░│ ▭ Nauka, 60 min    │
│░░░░░░░░░░░│ ▭ Korepetycje      │
│░░░░░░░░░░░│                    │
│░░░░░░░░░░░│ Najbliższe         │
│░░░░░░░░░░░│ * Fizyka, za 3 dni │
└───────────┴────────────────────┘
```
Przytrzymaj żeton i przeciągnij na lekcję; panel chowa się, gdy zaczynasz przeciągać. Szkice pokazują układ, nie wygląd.

### 12.1 Pulpit (ekran startowy)
- Zaćmienie z odliczaniem (11.7); dotknięcie otwiera Sprawdziany.
- Dziś: lekcje po kolei z wyróżnioną bieżącą, zastępstwa i odwołania oznaczone. Po lekcjach sekcja pokazuje jutro.
- Nowe od ostatniej wizyty: oceny (z %), nowe i przeniesione sprawdziany, wiadomości, ogłoszenia.
- Important: liczba ostrzeżeń na każdym poziomie i trzy najważniejsze.
- Szczęśliwy numerek; gdy równa się mojemu numerowi — wyraźne wyróżnienie.
- Zadania domowe na najbliższe 3 dni.

### 12.2 Kalendarz
Szczegóły w sekcji 13.

### 12.3 Sprawdziany
- Nadchodzące wpisy pogrupowane: „Ten tydzień”, „Następny tydzień”, dalej po datach.
- Wpis: typ (kolor, ikona, słowo), przedmiot (kolor), temat lub opis z Librusa, data i numer lekcji, duże odliczanie („dziś, 3. lekcja”, „jutro”, „za 3 dni”, w ostatniej dobie w godzinach), obecna średnia % z przedmiotu i podpowiedź z kalkulatora („żeby utrzymać 4, potrzebujesz min. 71%”), przycisk otwierający kalkulator.
- Filtry: Sprawdziany, Kartkówki, Zadania domowe (domyślnie włączone dwa pierwsze).
- Przeniesione terminy oznaczone („przeniesiony z 12.10”), usunięte przekreślone.
- Sekcja „Minione” (zwinięta), z oceną, gdy już się pojawi.

### 12.4 Oceny
- Przełącznik okresu: I półrocze, II półrocze, Cały rok. Sortowanie: nazwa, najniższa średnia, ostatnia zmiana. Na górze średnia ogólna.
- Karta przedmiotu: nazwa z kolorem, duży % (Unbounded), prognozowana ocena, pasek 0–100% z kreskami progów i znacznikiem średniej, „do 4 brakuje 3,2 pp”, ostatnie oceny jako kafelki (kolor według %, punktowe jako „17/20”, kropka przy nowych), ikona ostrzeżenia, jeśli przedmiot jest w Important.
- Dotknięcie karty otwiera ekran przedmiotu: lista ocen (data, ocena, %, źródło %, kategoria, opis, nauczyciel, czy liczona), wykres średniej w czasie z liniami progów, oceny proponowane i końcowe, kalkulator „co jeśli” (6.5), ustawienia przedmiotu (kolor, skrót, metoda średniej, „trudny”).

### 12.5 Statystyki
Tylko elementy wybrane przez użytkownika:
- Frekwencja ogólna: duża liczba i podział (obecności, spóźnienia, usprawiedliwione, nieusprawiedliwione), przełącznik okresu.
- Frekwencja z przedmiotów: poziome paski posortowane rosnąco, linia 50%, strefa ostrzegawcza do 60%.
- Spóźnienia i nieusprawiedliwione nieobecności: liczniki i lista (data, lekcja, przedmiot).
- Średnia w czasie: linia średniej ogólnej i wybranych przedmiotów, pasy progów w tle (wykres Vico).
- Obciążenie: słupki sprawdzianów i kartkówek na tydzień (4 tygodnie wstecz, 8 naprzód); tydzień z co najmniej 3 sprawdzianami wyróżniony.
- Zapas nieobecności: przedmioty z „możesz opuścić jeszcze ok. N lekcji”, kolory według progów z sekcji 8.

### 12.6 Important
Szczegóły w sekcji 8.

### 12.7 Skrzynka
Zakładki Wiadomości, Ogłoszenia, Uwagi, Wysłane. Lista z podglądem, nieprzeczytane wyróżnione, uwagi z ikoną pozytywnej lub negatywnej. Wysłane pobierane są z Librusa na żądanie (przy wejściu w zakładkę i przyciskiem „Odśwież”), nie przez synchronizację w tle. Ogłoszenia i uwagi tylko do odczytu. Wiadomości można też pisać: przycisk „Napisz wiadomość” otwiera ekran w układzie jak w poczcie — wiersz „Do” z żetonami odbiorców, temat i treść. Dotknięcie „Do” otwiera osobny ekran z listą odbiorców z Librusa (nauczyciele, wychowawca, pedagog, sekretariat), z szukaniem i wyborem wielu osób. Przed wysłaniem zawsze pytanie z nazwą odbiorcy i tematem, bo wysłania nie da się cofnąć. Gdy Librus nie potwierdzi wysłania, aplikacja mówi, że wynik jest nieznany, i nie proponuje ponowienia — wiadomość mogła już pójść. Załączników aplikacja nie wysyła ani nie pobiera.

### 12.8 Ustawienia (z wartościami domyślnymi)
- Konto: kto jest zalogowany, „Wyloguj i usuń dane”.
- Wygląd: motyw (jasny, ciemny, systemowy — domyślnie systemowy), akcent (Korona), „Mniej przezroczystości” (wyłączone).
- Kolory: typy wydarzeń (11.3), przedmioty (automatycznie, edytowalne) i ich skróty.
- Etykiety i szablony: etykiety Ważne, Przynieść, Powtórzyć, Zapytać nauczyciela; szablony Nauka (60 min), Korepetycje (60 min), Trening (90 min); lista reguł „wszystkie lekcje przedmiotu” z możliwością usunięcia.
- Zasady oceniania: tabela przeliczeń (6.2) z „Przywróć domyślne”, progi (6.4), metoda średniej dla przedmiotów, procent z opisów (wyłączone), zaokrąglanie przed porównaniem z progiem (wyłączone), poprawy (licz obie), samodzielne „+” i „−” w średniej (nie).
- Frekwencja: mapowanie typów, daty półroczy, dni wolne.
- Important: progi i włączanie reguł (sekcja 8).
- Powiadomienia: stan zgody systemowej, typy (wszystkie włączone), godziny ciszy 22:00–7:00, przypomnienie o 18:00, „Wyślij testowe powiadomienie”.
- Synchronizacja: co 3 h, w godzinach 6:00–22:00, ostatnie uruchomienia i błędy, „Synchronizuj teraz”.
- Dane: tryb demo, mój numer w dzienniku, „Wyczyść dane z Librusa i pobierz od nowa” (dane użytkownika zostają).
- „Próbnik stylu” (tylko w wersji debug, patrz Etap 2a).

### 12.9 Logowanie (pierwsze uruchomienie)
- Tarcza zaćmienia, pola „Login do Librus Synergia” i „Hasło” (z pokazywaniem hasła), przycisk „Zaloguj”, link „Wypróbuj na danych przykładowych”.
- Krótka informacja: dane zostają na telefonie, hasło jest zaszyfrowane.
- Błędy z przyczyną: złe dane, brak internetu, Librus niedostępny, możliwa weryfikacja dwuetapowa.
- Po zalogowaniu: pierwsza synchronizacja z paskiem postępu, potem ekran z prośbą o zgodę na powiadomienia.

## 13. Kalendarz — szczegóły

- Widoki, przełączane przyciskami segmentowymi:
  - Tydzień (domyślny): własna siatka godzinowa w Compose — kolumny pn–pt (weekend tylko, gdy są na nim wydarzenia), wiersze według dzwonków; kolejne tygodnie przesunięciem w bok (`HorizontalPager`).
  - Dzień: jedna kolumna, większe bloki ze szczegółami.
  - Miesiąc: `HorizontalCalendar` z biblioteki kizitonwose Calendar z własnymi komórkami dni.
  - Lista: najbliższe 14 dni w `LazyColumn` z nagłówkami dni.
- Zakres godzin z dzwonków (np. 7:30–16:30), linia „teraz”, bieżąca lekcja wyróżniona.
- Bloki według 11.5. Na telefonie kolumny tygodnia są wąskie, więc bloki pokazują skrót przedmiotu i ikonę typu; pełne nazwy są w widoku dnia i w szczegółach.
- Sprawdzian z numerem lekcji pokazuj na bloku tej lekcji (znacznik i poświata), a nie jako osobny blok, który ją zasłania. Wpis bez numeru lekcji trafia na pasek całodniowy nad siatką.
- Widok miesiąca: dzień ze sprawdzianem ma tło zabarwione kolorem typu (przy kartkówce słabiej), inne wpisy jako kropki.
- Dotknięcie elementu otwiera arkusz szczegółów (przedmiot, godziny, sala, nauczyciel, opis z Librusa, historia zmian, etykiety z notatkami, powiązana ocena po sprawdzianie) z akcjami „Dodaj etykietę”, a przy własnych wydarzeniach także „Edytuj” i „Usuń”.
- Własne wydarzenia: dotknięcie wolnego miejsca tworzy wydarzenie z godziną z tego miejsca (tytuł, czas, kolor lub etykieta, notatka, opcjonalnie przedmiot) w arkuszu edycji; tam też zmiana godziny. Przesuwanie bloków palcem — opcjonalnie, jeśli okaże się proste. Wpisów z Librusa nie da się zmieniać.
- Przybornik (panel z prawej, przycisk [P] w Kalendarzu):
  - Etykiety: kolorowe żetony. Przytrzymaj i przeciągnij na lekcję lub wydarzenie; gdy przeciąganie się zaczyna, panel się chowa. Po upuszczeniu na lekcję pytanie: „Tylko ta lekcja” albo „Wszystkie lekcje: {przedmiot}”, do tego opcjonalna notatka (np. „cyrkiel”). Przycisk „Nowa etykieta” (nazwa, kolor). Technicznie: `Modifier.dragAndDropSource` i `Modifier.dragAndDropTarget` z Compose — sprawdź aktualne API w dokumentacji.
  - Szablony wydarzeń: przeciągnięte na dzień lub godzinę tworzą własne wydarzenie z domyślną długością, od razu otwarte do edycji.
  - Najbliższe sprawdziany (3–5) z odliczaniem.
  - Alternatywa bez przeciągania: „Dodaj etykietę” w arkuszu szczegółów lekcji.
- Ważne lekcje: automatycznie, gdy jest na nich sprawdzian lub kartkówka (gdy wpis nie ma numeru lekcji — wszystkie lekcje tego przedmiotu w tym dniu); ręcznie przez etykietę.

## 14. Etapy prac i kryteria akceptacji

### Etap 0 — przygotowanie
- Użytkownik utworzył projekt w Android Studio (Empty Activity, Kotlin DSL). Sprawdź go i narzędzia: Android Studio, JDK dołączony do Android Studio, git, emulator albo telefon z włączonym debugowaniem USB. Zapytaj o wersję Androida w telefonie i dobierz minSdk (domyślnie 26).
- Dodaj moduł `:core`, uporządkuj katalog wersji, dodaj `.gitignore` (m.in. `librus-dev.properties`, `local.properties`, `*.jks`, `*.keystore`), `librus-dev.properties.example`, `PROGRESS.md` (lista kroków wszystkich etapów oraz sekcje: Decyzje, Otwarte kwestie, Znane problemy) i szkic `README.md` po polsku. Załóż repozytorium git.
- `.claude/settings.json` z blokadą dostępu do pliku z hasłem:
  ```json
  { "permissions": { "deny": ["Read(./librus-dev.properties)", "Edit(./librus-dev.properties)"] } }
  ```
  Po utworzeniu poproś użytkownika o ponowne uruchomienie Claude Code.
- Uruchom pustą aplikację na emulatorze albo na telefonie, żeby od początku działała cała ścieżka: budowanie → instalacja → start.
- Dane logowania użytkownik wpisuje tylko w aplikacji (od 30.09.2026 także na potrzeby rekonesansu, patrz 4.2).

### Etap 1 — logika i dane
- 1a: rekonesans (4.2) i akceptacja mapowania przez użytkownika.
- 1b, w tej kolejności: modele i `DataSource` → `DemoSource` → obliczenia z testami → `LibrusSource` według mapowania → Room i repozytoria → `SyncWorker` z wykrywaniem zmian → magazyn danych logowania → powiadomienia lokalne (kanały, typy, godziny ciszy, przypomnienia) → prosty ekran diagnostyczny (liczby rekordów, ostatnie synchronizacje, „Synchronizuj teraz”, „Wyślij testowe powiadomienie”), żeby sprawdzić wszystko na telefonie przed budową interfejsu.
- `DemoSource`: realistyczny uczeń szkoły średniej z polskimi przedmiotami, dzwonkami i planem na 5 dni, zastępstwem i odwołaną lekcją, sprawdzianami i kartkówkami w najbliższych tygodniach, ocenami zwykłymi i punktowymi, frekwencją, wiadomościami, ogłoszeniami i uwagami. Daty liczone względem dzisiejszego dnia, stałe ziarno losowości. Scenariusze do Important: przedmiot poniżej 40%, przedmiot w przedziale 40–50%, przedmiot ze spadkiem średniej, przedmiot z frekwencją blisko 50%, świeża jedynka.
- Kryteria akceptacji:
  - `./gradlew :core:test` przechodzi, a testy obejmują: tabelę przeliczeń, obie metody średniej, progi, kalkulator w obu trybach (także przypadki „jedna ocena nie wystarczy” i „masz zapas”), frekwencję, zapas nieobecności, każdą regułę Important i trend;
  - rekonesans działa i nie wypisuje danych osobowych;
  - w trybie demo synchronizacja na emulatorze wypełnia bazę;
  - na telefonie pełna synchronizacja z Librusem kończy się sukcesem, a druga nie tworzy duplikatów ani fałszywych „nowych”;
  - błędne hasło daje czytelny komunikat po polsku;
  - dane użytkownika przetrwają ponowną synchronizację;
  - testowe powiadomienie dociera, a zadanie okresowe jest zaplanowane.

### Etap 2 — interfejs
- 2a: plan wyglądu według sekcji 11 (kolory obu motywów, szkło z wersją bez rozmycia, typografia, szkice ASCII ekranów telefonu), sprawdzony pod kątem tego, czy nie wygląda jak ogólny szablon pasujący do każdej aplikacji. Potem ekran „Próbnik stylu” z próbkami — szkło, przyciski, kafelki ocen, bloki kalendarza (sprawdzian, kartkówka, zadanie, lekcja, zastępstwo, odwołana), zaćmienie — w obu motywach i z przełącznikiem akcentu. Akceptacja użytkownika przed budową ekranów.
- 2b, w tej kolejności: motyw i szkielet (pasek boczny, górny pasek, nawigacja, logowanie, stan synchronizacji) → Pulpit → Kalendarz z Przybornikiem → Sprawdziany → Oceny z kalkulatorem → Statystyki → Important → Skrzynka → Ustawienia.
- Oceniaj wygląd na zrzutach ekranu z emulatora (`adb exec-out screencap -p > shot.png`) w obu motywach, z powiększoną czcionką i na wąskim ekranie (360 dp); poprawiaj przed pokazaniem wyniku użytkownikowi.
- Kryteria akceptacji: działa na danych demo i prawdziwych; oba motywy i zmiana akcentu na żywo; tekst na szkle czytelny (AA), także w wersji bez rozmycia; poprawny układ przy 360 dp i przy powiększonej czcionce; przeciąganie z Przybornika działa i ma alternatywę bez przeciągania; kalendarz przewija się płynnie; w Logcat brak błędów aplikacji; `./gradlew :app:assembleDebug` przechodzi.

### Etap 3 — telefon na co dzień
- Ikona aplikacji (adaptacyjna i monochromatyczna) oraz ekran startowy z tarczą zaćmienia.
- Wersja release podpisana własnym kluczem. Klucz i hasła poza repozytorium; przypomnij użytkownikowi o kopii klucza — bez niego nie da się zaktualizować zainstalowanej aplikacji bez utraty danych.
- Instalacja na telefonie przez kabel USB, krok po kroku (opcje programisty, debugowanie USB, instalacja z Android Studio albo `adb install`).
- Opcjonalnie: eksport i import własnych danych (etykiety, wydarzenia, ustawienia) do pliku JSON — przydaje się przy zmianie telefonu.
- Kryteria akceptacji: aplikacja działa na telefonie użytkownika; synchronizacja w tle działa przez cały dzień (widać to w historii synchronizacji); powiadomienia przychodzą zgodnie z ustawieniami, łącznie z godzinami ciszy; bez internetu widać ostatnie dane z godziną synchronizacji; `README.md` opisuje, jak zbudować i zainstalować nową wersję.

## 15. Poza zakresem (teraz)
Eksport do Google Calendar i plików .ics, wysyłanie wiadomości, konto rodzica i wielu użytkowników, publikacja w sklepach, funkcje AI, zmiany danych w Librusie, widżety na ekran główny (pomysł na później), wersja na komputer (sekcja 16).

## 16. Na później: wersja na komputer
- Strona internetowa nie może łączyć się z Librusem prosto z przeglądarki (przeglądarki blokują takie zapytania do cudzych serwisów), więc będzie potrzebować małego serwera w internecie, nie na komputerze użytkownika. Serwer uruchomi ten sam moduł `:core` i poda dane stronie.
- Alternatywa bez serwera: aplikacja na komputer (Compose Multiplatform Desktop), która korzysta z `:core` bezpośrednio.
- Dlatego już teraz `:core` nie ma żadnych zależności od Androida. Haze, Vico i kizitonwose Calendar mają wersje multiplatformowe, co ułatwi przeniesienie interfejsu.
- Decyzję podejmie użytkownik po Etapie 3.
