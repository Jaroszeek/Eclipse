# Eclipse — instrukcje dla Claude Code

Eclipse to prywatna aplikacja na Androida (.apk) jednego ucznia: kalendarz z planem lekcji i sprawdzianami, oceny w procentach, statystyki frekwencji i zakładka ostrzeżeń „Important”. Aplikacja sama łączy się z Librusem (Librus Synergia, konto ucznia) prosto z telefonu — bez serwera i bez hostingu. Dane zostają na telefonie. Wersja na komputer powstanie później, dlatego logika jest oddzielona od Androida.

- `SPEC.md` — pełna specyfikacja: funkcje, obliczenia, wygląd, etapy. Przed pracą nad funkcją przeczytaj jej sekcję.
- `PROGRESS.md` — postęp, decyzje, otwarte kwestie. Czytaj na początku każdej sesji, aktualizuj po każdym kroku.

## Z kim pracujesz
- Użytkownik jest uczniem i początkującym programistą. Pisz po polsku, prostym językiem. Termin techniczny objaśnij jednym zdaniem, gdy pojawia się pierwszy raz.
- Przed większym krokiem napisz w 1–3 zdaniach, co zrobisz i po co. Po kroku: co powstało, jak to sprawdzić i co dalej.
- Użytkownik pracuje w Android Studio. Gdy trzeba coś tam kliknąć (synchronizacja Gradle, emulator, uruchomienie), napisz dokładnie gdzie.
- Gdy coś się nie uda, wyjaśnij przyczynę w 1–2 zdaniach i napraw. Nie zostawiaj użytkownika z samym logiem błędu.
- Ważne decyzje (wygląd, zachowanie funkcji, dane) konsultuj: 2–3 opcje, jedna oznaczona jako polecana. Drobiazgi rozstrzygaj samodzielnie i zapisuj w `PROGRESS.md`.

## Sposób pracy
- Pracuj etapami z `SPEC.md` (sekcja 14). Kolejny etap zaczynaj dopiero po zgodzie użytkownika.
- Małe kroki: każdy kończy się działającym stanem, commitem i wpisem w `PROGRESS.md`.
- Najprostsze rozwiązanie, które spełnia SPEC. Bez funkcji, których SPEC nie opisuje.
- Nie zmieniaj `SPEC.md` bez zgody. Gdy SPEC jest niejasny albo nie pasuje do tego, jak naprawdę działa Librus, opisz problem i zaproponuj zmianę.
- Po etapie przejdź przez jego kryteria akceptacji i wypisz, co spełnione, a co nie.
- Interfejs rozwijaj w trybie demo (`DemoSource`), żeby nie obciążać Librusa.

## Bezpieczeństwo i prywatność — zawsze
- Hasło do Librusa użytkownik wpisuje tylko w aplikacji (ekran logowania; rekonesans też działa w aplikacji). Nie proś o hasło w czacie. Gdyby na komputerze pojawił się plik `librus-dev.properties`, nie czytaj go, nie wyświetlaj i nie edytuj.
- W aplikacji dane logowania są zaszyfrowane (DataStore + Tink, klucz w Android Keystore) i wyłączone z kopii zapasowej. Nie używaj przestarzałego `EncryptedSharedPreferences`.
- Do gita nie trafiają: `librus-dev.properties`, `local.properties`, klucze podpisu (`*.jks`, `*.keystore`) i ich hasła, surowe odpowiedzi z Librusa. Sprawdź `.gitignore` przed pierwszym commitem.
- Nie loguj (Logcat, pliki) haseł, tokenów ani ciasteczek sesji. W release wyłącz logi debug.
- Testując połączenie z Librusem, wypisuj strukturę, nazwy pól i liczby rekordów — bez treści wiadomości i uwag, bez nazwisk. Fixtures do testów anonimizuj.
- Tylko HTTPS. Aplikacja nie wysyła danych nigdzie poza Librusa (bez analityki, bez zewnętrznych serwerów).
- Szanuj serwery Librusa: synchronizacja w tle nie częściej niż co godzinę (domyślnie co 3 h), zapytania po kolei, ponawianie z rosnącym odstępem, jedna synchronizacja naraz.
- Aplikacja jest prywatna — nie publikuj jej w Google Play ani w innym sklepie.

## Stack
- Kotlin, Gradle z Kotlin DSL i katalogiem wersji (`gradle/libs.versions.toml`), Android Studio w najnowszej stabilnej wersji.
- `:core` — czysty Kotlin/JVM bez zależności od Androida: modele, klient Librusa (OkHttp z ciasteczkami, kotlinx.serialization, Jsoup w razie potrzeby), obliczenia, dane demo, rekonesans. Testy: JUnit 5 + kotlin.test.
- `:app` — Jetpack Compose + Material 3 (własny motyw), Navigation Compose, Room, DataStore, Tink, WorkManager, powiadomienia lokalne, Haze (szkło), kizitonwose Calendar (widok miesiąca), Vico (wykresy). Fonty Unbounded i Manrope w `res/font`.
- Zależności składa ręczny `AppContainer` (bez Hilta) — mniej magii dla początkującego.
- minSdk 31 (telefon użytkownika: Android 16), compileSdk i targetSdk najnowsze stabilne (teraz 37). AGP ograniczone wersją Android Studio. Strefa czasowa Europe/Warsaw, tydzień od poniedziałku, format pl-PL.
- Wersje bibliotek aktualne stabilne. Gdy nie masz pewności co do API biblioteki, sprawdź dokumentację, zamiast zgadywać.

## Architektura
- `:core` nie importuje nic z Androida — ten sam kod obsłuży później wersję na komputer.
- Dane z Librusa pobiera tylko interfejs `DataSource` w `:core`: `LibrusSource` i `DemoSource`. Reszta aplikacji nie wie, skąd są dane.
- W `:app`: ekrany (Compose, ViewModel + `StateFlow`) → repozytoria → Room i DataStore. `SyncWorker` (WorkManager): pobierz przez `DataSource` → znormalizuj → zapisz (upsert) → wykryj zmiany → powiadomienia.
- Dane użytkownika (własne wydarzenia, etykiety, szablony, kolory, „trudny”, ustawienia) leżą w osobnych tabelach i synchronizacja ich nie nadpisuje.
- Obliczenia (średnie %, progi, frekwencja, zapas nieobecności, reguły Important, kalkulator „co jeśli”) to czyste funkcje w `:core` z testami. Ekrany niczego nie przeliczają.

## Struktura (docelowa)
```
eclipse/
├── CLAUDE.md, SPEC.md, PROGRESS.md, README.md
├── .claude/settings.json
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml
├── core/   src/main/kotlin/ (model, source/librus, source/demo, calc, recon), src/test/
├── app/    src/main/ (ui, data, sync, notify, security), res/
└── docs/   librus-rekonesans.md
```

## Komendy
Na Windowsie `gradlew.bat` zamiast `./gradlew`. W terminalu poza Android Studio ustaw `JAVA_HOME` na JDK z Android Studio (`C:\Program Files\Android\Android Studio\jbr`). Uzupełnij, gdy powstaną:
- Testy logiki: `./gradlew :core:test`
- Rekonesans Librusa: w aplikacji debug, ekran „Rekonesans Librusa” (użytkownik kopiuje raport i wkleja go do czatu; raport ma tylko strukturę)
- Build debug: `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/`
- Instalacja na podłączonym telefonie lub emulatorze: `./gradlew :app:installDebug`
- Lint: `./gradlew lint`
- Zrzut ekranu do oceny wyglądu: `adb exec-out screencap -p > shot.png`

## Konwencje
- Nazwy w kodzie po angielsku, teksty interfejsu po polsku w `strings.xml`, komentarze po polsku tylko tam, gdzie kod sam się nie tłumaczy.
- Idiomatyczny Kotlin, bez `!!`. Stan ekranu jako niezmienna klasa danych.
- Teksty w UI: zwykłe zdania (bez CAPS LOCKA), przycisk mówi, co zrobi („Odśwież dane”, „Dodaj wydarzenie”), błąd mówi, co się stało i jak to naprawić.
- Commity małe, opis po polsku w trybie rozkazującym, np. „Dodaj obliczanie średniej procentowej”.
- Przed commitem przechodzi `./gradlew :core:test`; po zmianach w `:app` także `./gradlew :app:assembleDebug`.

## Pułapki tego projektu
- Szkoła liczy średnią procentową, bez wag. Zwykłe oceny → % według tabeli użytkownika (6 = 100, 5 = 90, 4 = 75, 3 = 50, 2 = 40, 1 = 0 — jedynka domyślnie), punktowe → punkty ÷ maksimum. Szczegóły: `SPEC.md`, sekcja 6.
- W Librusie przedmiot może występować dwa razy (oceny zwykłe i punktowe) — scalaj w jeden przedmiot.
- Librus nie ma oficjalnego API dla uczniów. Klienta buduj na podstawie rekonesansu, nie zgaduj pól. Wzorce do nauki: szkolny-android (Kotlin), librus-apix (Python), librus-api-rewrited (JS) — pisz własny kod, nie kopiuj (licencje).
- Konta z weryfikacją dwuetapową mogą nie logować się nieoficjalną drogą — uwzględnij to w komunikacie błędu.
- Pełne rozmycie (Haze) działa od Androida 12; niżej i przy „Mniej przezroczystości” — matowe tło. Rozmycie tylko na dużych panelach, nigdy na każdym bloku kalendarza.
- WorkManager uruchamia zadania okresowe najczęściej co 15 minut, a system może je opóźniać — zawsze pokazuj czas ostatniej synchronizacji.
- Android 13+: zgoda na powiadomienia (`POST_NOTIFICATIONS`) — poproś w dobrym momencie, z wyjaśnieniem. Aplikacja rysuje się pod paskami systemu (edge-to-edge) — obsłuż wcięcia.
