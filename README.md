# Eclipse

Prywatny planer szkolny na Androida na danych z Librus Synergia: plan lekcji ze sprawdzianami, oceny w procentach, frekwencja i ostrzeżenia „Important”. Aplikacja sama łączy się z Librusem z telefonu — bez serwera. Dane zostają na telefonie.

Aplikacja jest prywatna i nie trafia do żadnego sklepu.

## Wymagania
- Android Studio 2026.1 lub nowsze (z dołączonym JDK 21)
- Telefon z Androidem 12 lub nowszym albo emulator

## Moduły
- `core` — czysty Kotlin: modele, klient Librusa, obliczenia, dane demo, rekonesans.
- `app` — aplikacja na Androida (Jetpack Compose).

## Komendy
Na Windowsie zamiast `./gradlew` wpisz `gradlew.bat`.

| Co | Komenda |
|---|---|
| Testy logiki | `./gradlew :core:test` |
| Budowanie wersji debug | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/` |
| Instalacja na telefonie lub emulatorze | `./gradlew :app:installDebug` |

## Dane logowania do rekonesansu
Skopiuj `librus-dev.properties.example` jako `librus-dev.properties` i wpisz login i hasło. Plik jest w `.gitignore` i nie trafia do repozytorium.

## Dokumenty
- `SPEC.md` — specyfikacja
- `PROGRESS.md` — postęp i decyzje
- `CLAUDE.md` — instrukcje dla Claude Code
