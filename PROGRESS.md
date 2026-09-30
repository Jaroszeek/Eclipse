# Eclipse — postęp prac

Aktualizowane po każdym kroku. Szczegóły etapów: `SPEC.md`, sekcja 14.

## Etap 0 — przygotowanie
- [x] Sprawdzenie projektu i narzędzi (Android Studio 2026.1.1, JDK 21 z Android Studio, git 2.51, emulator „gympixel” z Androidem 15)
- [x] Moduł `:core` (czysty Kotlin/JVM, JUnit 5 + kotlin.test)
- [x] Katalog wersji uporządkowany, biblioteki w najnowszych stabilnych wersjach
- [x] `.gitignore`, `.claude/settings.json`
- [x] `PROGRESS.md`, szkic `README.md`
- [x] Repozytorium git i pierwszy commit
- [x] Prywatne repozytorium na GitHubie (Jaroszeek/Eclipse) i wysłanie kodu
- [x] Pusta aplikacja uruchomiona na emulatorze („gympixel”, Android 15)

## Etap 1 — logika i dane
- [x] 1a: rekonesans w aplikacji: ekran logowania + raport z przyciskiem „Kopiuj raport” (logowanie sprawdzone na emulatorze zmyślonym loginem — Librus odpowiada „Nieprawidłowy login i/lub hasło”)
- [x] 1a: użytkownik uruchamia rekonesans na telefonie i wkleja raport
  - 2026-09-30, próba 5: logowanie e-mailem działa, wszystkie zasoby API 2.0 odpowiadają (poza `TeacherFreeDays` — 403, niepotrzebne).
  - 2026-09-30, próba 1: logowanie OK, ale każdy zasób gateway → 401 „Request is denied”. Wersja 2 rekonesansu pokazuje przebieg logowania i próbuje dokończyć sesję (goTo, strona ucznia, /loguj/przenies).
  - Próba 3: po poprawce Synergia i tak odsyła do portalu (konto połączone z Kontem LIBRUS). Decyzja użytkownika: logowanie e-mailem przez Konto LIBRUS (SPEC 4.1 zaktualizowana). Wersja 4 sprawdzona na emulatorze zmyślonym e-mailem: portal odpowiada, błędne dane dają czytelny komunikat.
  - Próba 2: logowanie każe iść pod goTo = /OAuth/Authorization/2FA (krok logowania, nie weryfikacja dwuetapowa); wejście od razu pod Grant odsyła do portalu. Gateway nadal 401. Wersja 3: logowanie idzie pod goTo, rekonesans sprawdza też strony HTML dziennika (plan B z SPEC 4.1).
- [x] 1a: `docs/librus-rekonesans.md`
- [x] 1a: akceptacja mapowania przez użytkownika
- [x] 1b: modele i `DataSource` (`core/model/Models.kt`, `core/source/DataSource.kt`; funkcje blokujące, wołane w tle)
- [x] 1b: `DemoSource` (uczeń technikum, dzwonki jak w szkole użytkownika, rok szkolny demo zaczyna się 10 tygodni przed dziś; scenariusze Important: chemia < 40%, fizyka 40–50%, historia ze spadkiem, biologia z frekwencją ok. 53%, świeża jedynka z matematyki, geografia 53%)
- [x] 1b: obliczenia z testami (sekcje 6–8): `core/calc/Grades.kt`, `Attendance.kt`, `Important.kt`; 24 testy w `:core:test`
- [x] 1b: `LibrusSource` według mapowania (test na zmyślonych odpowiedziach o strukturze z rekonesansu; przy 401 jedno ponowne logowanie)
- [x] 1b: Room i repozytoria (`app/data`: jedna tabela `records` na dane z Librusa jako JSON modeli z `:core` + tabele danych użytkownika; `SettingsStore` w DataStore; `AppContainer`)
- [x] 1b: `SyncWorker` z wykrywaniem zmian (`core/sync/Changes.kt` z testem; zakresy dat jak w SPEC 4.3; usuwanie tylko w pobranym zakresie; jedna synchronizacja naraz — Mutex; okno 6–22 dla okresowej)
- [x] 1b: magazyn danych logowania (DataStore + Tink AES-256-GCM, klucz główny w Android Keystore; wyłączony z kopii i przenosin)
- [x] 1b: powiadomienia lokalne (8 kanałów, centrum powiadomień w tabeli `notifications`, godziny ciszy z porannym podsumowaniem, przypomnienie dzień przed sprawdzianem, > 3 oceny → jedno zbiorcze, po 3 nieudanych synchronizacjach jedno powiadomienie)
- [x] 1b: ekran diagnostyczny (logowanie e-mailem z zapisem zaszyfrowanym, tryb demo, liczby rekordów, historia synchronizacji, stan zadania w tle, „Synchronizuj teraz” — nie częściej niż co 2 min, „Wyślij testowe powiadomienie”, zgoda na powiadomienia, rekonesans, „Wyloguj i usuń dane”). Na emulatorze w demo: baza wypełniona, druga synchronizacja bez nowych/zmienionych, testowe powiadomienie dociera, zadanie okresowe zaplanowane.
- [x] Kryteria akceptacji Etapu 1 — 30.09.2026 użytkownik potwierdził na telefonie: pełna synchronizacja z Librusem, druga bez duplikatów, czytelny błąd złego hasła, testowe powiadomienie.

## Etap 2 — interfejs
- [x] 2a: plan wyglądu (użytkownik przeszedł dalej bez uwag — „wykonaj kolejne 2 polecenia”)
- [x] 2a: ekran „Próbnik stylu” (debug; zrzuty wysłane użytkownikowi, praca idzie dalej, uwagi można zgłosić w każdej chwili)
- [x] 2b: motyw i szkielet (pasek boczny, górny pasek, nawigacja, logowanie, stan synchronizacji)
- [x] 2b: Pulpit
- [x] 2b: Kalendarz z Przybornikiem (tydzień/dzień/miesiąc/lista, arkusz szczegółów, własne wydarzenia, etykiety i szablony przeciągane oraz bez przeciągania)
- [x] 2b: Sprawdziany (filtry, grupy, odliczanie, podpowiedź z kalkulatora, przeniesione i usunięte, minione z oceną)
- [x] 2b: Oceny z kalkulatorem (okresy, sortowanie, średnia ogólna, karty, ekran przedmiotu z wykresem Vico, kalkulator w obu trybach, ustawienia przedmiotu, „poprawiona / pomiń”)
- [ ] 2b: Statystyki
- [ ] 2b: Important
- [ ] 2b: Skrzynka
- [ ] 2b: Ustawienia
- [ ] Kryteria akceptacji Etapu 2

## Etap 3 — telefon na co dzień
- [ ] Ikona aplikacji i ekran startowy
- [ ] Podpisana wersja release
- [ ] Instalacja na telefonie przez USB
- [ ] Kryteria akceptacji Etapu 3

## Decyzje
- 2026-09-29: telefon użytkownika ma Androida 16, więc `minSdk = 31` (Android 12). Pełne rozmycie szkła (Haze) działa na każdym obsługiwanym telefonie; wersja matowa zostaje tylko dla ustawienia „Mniej przezroczystości”.
- 2026-09-29: `compileSdk` i `targetSdk` = 37 (Android 17), bo tego wymagają najnowsze biblioteki AndroidX. Platforma 37 doinstalowana w Android SDK.
- 2026-09-29: AGP zostaje w wersji 9.2.1, bo nowszej nie obsługuje Android Studio 2026.1.1. Kotlin 2.4.20.
- 2026-09-29: usunięte przykładowe testy z szablonu i ich biblioteki (JUnit 4, Espresso). Testy logiki są w `:core` (JUnit 5).
- 2026-09-29: biblioteki `:core` (OkHttp, kotlinx.serialization, coroutines) dochodzą do katalogu wersji w kroku, który ich używa.
- 2026-09-29: logowanie do Librusa loginem Synergii, bez weryfikacji dwuetapowej — znana droga OAuth z SPEC 4.1 powinna działać.
- 2026-09-30: rekonesans działa w aplikacji zamiast `./gradlew :core:recon` z plikiem `librus-dev.properties` (prośba użytkownika — nie był przy komputerze; hasło nie leży w pliku). SPEC 4.2 zaktualizowana. Plik wzoru usunięty; wpis w `.gitignore` i blokada w `.claude/settings.json` zostają na wszelki wypadek.
- 2026-09-30: mapowanie z `docs/librus-rekonesans.md` zaakceptowane; „uroczystość nieobecność” nie liczy się do limitu nieobecności.
- 2026-09-30: obliczenia — samodzielne „+” i „−” liczą się tylko, gdy mają własną wartość w tabeli symboli (to jest przełącznik z SPEC 12.8). Procent z opisu oceny pominięty (rekonesans: opisy nie zawierają procentów). Ustawienie „poprawy” (licz obie / tylko poprawę / lepszą) odłożone — Librus nie pokazał powiązania oceny z poprawą; na razie liczone są obie, a ręczne „poprawiona / pomiń” działa w Important.
- 2026-09-30: reguła „zagrożenie od nauczyciela” pominięta — w API nie ma takiej informacji; zostaje „proponowana ocena 1”.
- 2026-09-30: baza — dane z Librusa w jednej tabeli `records` (typ, klucz, JSON, czasy zmian) zamiast osobnej tabeli na każdy rodzaj: jedno miejsce wykrywania zmian, mniej kodu; danych jest mało, więc filtrujemy w pamięci. Dane użytkownika w osobnych tabelach. Ustawienia jako jeden JSON w DataStore.
- 2026-09-30: szyfrowanie danych logowania stabilną biblioteką Tink 1.23.0 zamiast `androidx.datastore:datastore-tink` — ta jest dostępna tylko jako 1.3.0-alpha. Efekt jak w SPEC 10.3.
- 2026-09-30: `backup_rules.xml` usunięty — przy minSdk 31 Android używa tylko `data_extraction_rules.xml`.
- 2026-09-30: treść powiadomienia o ocenie: tytuł „Nowa ocena”, treść „matematyka: 4 (75%)” (Librus podaje nazwy w mianowniku, więc „z matematyki” wymagałoby odmiany).
- 2026-09-30: Etap 2 — Haze 2.0.1 (`haze-blur`), Vico 3.3.1, kizitonwose Calendar 2.10.1, navigation-compose 2.10.2 (API sprawdzone w źródłach bibliotek). Rozmycie tylko na dużych panelach; Przybornik ma własne źródło szkła, bo Haze nie rozmywa warstwy, w której sam leży.
- 2026-09-30: Kalendarz — oś czasu od pierwszego do ostatniego dzwonka (rozszerzana o własne wydarzenia); widok listy pomija zwykłe lekcje bez zmian i etykiet, żeby lista 14 dni była czytelna.
- 2026-09-30: aplikacja wymusza polskie zasady odmiany liczebników (telefon może mieć inny język), bo jest tylko po polsku.
- 2026-09-30: `LibrusClient` ma własny prosty magazyn ciasteczek w pamięci; zapisywanie sesji dojdzie w `LibrusSource`.
- 2026-09-29: kod trafia do prywatnego repozytorium na GitHubie (konto Jaroszeek), autor commitów: Jaroszek.

## Otwarte kwestie
Z `SPEC.md`, sekcja 2:
1. Czy 4 to na pewno od 75%? Czy średnia jest zaokrąglana przed porównaniem z progiem?
2. Ile procent liczy się jedynka (domyślnie 0%) i czy plusy i minusy coś zmieniają (domyślnie nie)?
3. Przy ocenach punktowych: średnia z procentów czy suma punktów? (porównać z procentem z Librusa)
4. ~~Wersja Androida~~ — Android 16.
5. ~~Login e-mailem czy loginem Synergii~~ — loginem Synergii.

## Znane problemy
- Wiadomości (wiadomosci.librus.pl) jeszcze niepodłączone — osobny serwis z własnym logowaniem; `LibrusSource.messages()` zwraca pustą listę.
- Zadania domowe: `HomeWorkAssignments` puste w szkole użytkownika — mapowanie dopiero po pierwszym wpisie.
- Uwagi: znaczenie `Positive` (1 = pozytywna, 0 = negatywna) do potwierdzenia.
- Emulator „gympixel” ma wyłączoną grafikę sprzętową (`hw.gpu.enabled = no`) i startuje bardzo wolno. Z opcją `-gpu host` Android wstaje w ok. 20 s. Stała poprawka: Device Manager → gympixel → Edit → Graphics: Hardware.
