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
Na Windowsie zamiast `./gradlew` wpisz `gradlew.bat`. W zwykłym terminalu (poza Android Studio) ustaw najpierw `JAVA_HOME` na JDK z Android Studio: `C:\Program Files\Android\Android Studio\jbr`.

| Co | Komenda |
|---|---|
| Testy logiki | `./gradlew :core:test` |
| Wersja debug (do pracy) | `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/` |
| Wersja release (na co dzień) | `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk` |
| Instalacja release przez kabel | `./gradlew :app:installRelease` |
| Lint | `./gradlew :app:lintDebug` |

## Jak zbudować i zainstalować nową wersję na telefonie

### Przez kabel USB (zalecane)
1. W telefonie: Ustawienia → Informacje o telefonie → Informacje o oprogramowaniu → 7 razy stuknij **Numer wersji**. Wróć, wejdź w **Opcje programisty** i włącz **Debugowanie USB**.
2. Podłącz telefon kablem i na telefonie kliknij **Zezwól** przy pytaniu o debugowanie USB.
3. W Android Studio otwórz terminal (View → Tool Windows → Terminal) i wpisz `gradlew.bat :app:installRelease`. Albo: panel **Build Variants** (lewy dolny róg) → dla `app` wybierz `release` → zielony trójkąt **Run 'app'**.
4. Nowa wersja zastępuje starą; dane zostają.

### Bez kabla
1. `gradlew.bat :app:assembleRelease`.
2. Skopiuj `app/build/outputs/apk/release/app-release.apk` na telefon (np. przez Dysk Google) i otwórz go w aplikacji Pliki → **Zainstaluj**. Pierwszy raz telefon poprosi o zgodę na instalowanie z tego źródła.

Przed każdą nową wersją zwiększ `versionCode` w `app/build.gradle.kts` (np. 1 → 2) — telefon przyjmuje aktualizację tylko z wyższym numerem.

### Wersja debug a release
Obie wersje mają ten sam identyfikator aplikacji, ale różne podpisy. Przejście z debug na release (albo odwrotnie) wymaga odinstalowania poprzedniej wersji — znikną wtedy dane na telefonie (logowanie, etykiety, własne wydarzenia); dane z Librusa pobiorą się od nowa.

## Klucz podpisu wersji release — zrób kopię!
- Klucz i hasła leżą **poza repozytorium**: `C:\Users\jasio\.eclipse-signing\` — pliki `eclipse-release.jks` (klucz) i `keystore.properties` (hasła, wygenerowane losowo). Gradle czyta je automatycznie.
- Nie wrzucaj ich do gita ani na GitHuba (`.gitignore` blokuje `*.jks` i `keystore.properties`).
- **Zrób kopię całego folderu** `.eclipse-signing` (np. na pendrive i do prywatnej chmury albo menedżera haseł). Bez tego klucza nie da się zaktualizować zainstalowanej aplikacji — trzeba by ją odinstalować i stracić dane zapisane na telefonie.

## Dane logowania
Login (e-mail Konta LIBRUS) i hasło wpisuje się tylko w aplikacji; są zaszyfrowane (Tink, klucz w Android Keystore) i wyłączone z kopii zapasowej.

## Dokumenty
- `SPEC.md` — specyfikacja
- `PROGRESS.md` — postęp i decyzje
- `CLAUDE.md` — instrukcje dla Claude Code
- `docs/librus-rekonesans.md` — wyniki rekonesansu i mapowanie danych
- `docs/licenses/` — licencje fontów (OFL)
