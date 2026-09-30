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
- [ ] 1b: `LibrusSource` według mapowania
- [ ] 1b: Room i repozytoria
- [ ] 1b: `SyncWorker` z wykrywaniem zmian
- [ ] 1b: magazyn danych logowania (DataStore + Tink)
- [ ] 1b: powiadomienia lokalne
- [ ] 1b: ekran diagnostyczny
- [ ] Kryteria akceptacji Etapu 1

## Etap 2 — interfejs
- [ ] 2a: plan wyglądu i akceptacja
- [ ] 2a: ekran „Próbnik stylu” i akceptacja
- [ ] 2b: motyw i szkielet (pasek boczny, górny pasek, nawigacja, logowanie, stan synchronizacji)
- [ ] 2b: Pulpit
- [ ] 2b: Kalendarz z Przybornikiem
- [ ] 2b: Sprawdziany
- [ ] 2b: Oceny z kalkulatorem
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
- Emulator „gympixel” ma wyłączoną grafikę sprzętową (`hw.gpu.enabled = no`) i startuje bardzo wolno. Z opcją `-gpu host` Android wstaje w ok. 20 s. Stała poprawka: Device Manager → gympixel → Edit → Graphics: Hardware.
