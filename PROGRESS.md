# Eclipse — postęp prac

Aktualizowane po każdym kroku. Szczegóły etapów: `SPEC.md`, sekcja 14.

## Etap 0 — przygotowanie
- [x] Sprawdzenie projektu i narzędzi (Android Studio 2026.1.1, JDK 21 z Android Studio, git 2.51, emulator „gympixel” z Androidem 15)
- [x] Moduł `:core` (czysty Kotlin/JVM, JUnit 5 + kotlin.test)
- [x] Katalog wersji uporządkowany, biblioteki w najnowszych stabilnych wersjach
- [x] `.gitignore`, `.claude/settings.json`
- [x] `PROGRESS.md`, szkic `README.md`
- [x] Repozytorium git i pierwszy commit
- [ ] Prywatne repozytorium na GitHubie (Jaroszeek/Eclipse) i wysłanie kodu
- [x] Pusta aplikacja uruchomiona na emulatorze („gympixel”, Android 15)

## Etap 1 — logika i dane
- [x] 1a: rekonesans w aplikacji: ekran logowania + raport z przyciskiem „Kopiuj raport” (logowanie sprawdzone na emulatorze zmyślonym loginem — Librus odpowiada „Nieprawidłowy login i/lub hasło”)
- [ ] 1a: użytkownik uruchamia rekonesans na telefonie i wkleja raport
- [ ] 1a: `docs/librus-rekonesans.md` i akceptacja mapowania
- [ ] 1b: modele i `DataSource`
- [ ] 1b: `DemoSource`
- [ ] 1b: obliczenia z testami (sekcje 6–8)
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
