# Rekonesans Librusa

Wykonany 30.09.2026 z telefonu (ekran „Rekonesans Librusa”, SPEC 4.2). Bez danych osobowych: tylko struktura, liczby, nazwy kategorii i symbole.

## Logowanie
- Stara droga (login Synergii → `api.librus.pl/OAuth` → ciasteczka → `synergia.librus.pl/gateway/api/2.0`) sprawdza hasło, ale dla konta połączonego z Kontem LIBRUS Synergia odsyła do portalu, a gateway odpowiada 401 „Request is denied”.
- Działa droga aplikacji mobilnej (Konto LIBRUS, e-mail): `portal.librus.pl/konto-librus/redirect/dru` → formularz `login/action` (pola `email`, `password`, ukryte `redirectTo`, `redirectCrc`, `_token` + nagłówek CSRF) → przekierowanie `app://librus?code=…` → `POST oauth2/access_token` → `GET api/v3/SynergiaAccounts` (1 konto: grupa `student`, stan `active`, `accessToken`) → `https://api.librus.pl/2.0/<zasób>` z `Authorization: Bearer`.
- Błędne dane: portal wraca na stronę logowania z komunikatem „Upewnij się, że nie wpisujesz tutaj loginu i hasła otrzymanych w szkole!…”.
- `Me.Refresh = 900` — token konta najpewniej trzeba odświeżać; przy 401 ponownie pobieramy `SynergiaAccounts` (portal wydaje też `refresh_token`).

## Rok szkolny i dzwonki
- `Classes`: początek roku 2026-09-01, koniec I półrocza 2027-01-17, koniec roku 2027-06-25. Daty półroczy są w Librusie — ustawienia to tylko zapas.
- Dzwonki z planu (`Timetables`): 1. 7:30–8:15, 2. 8:25–9:10, 3. 9:20–10:05, 4. 10:15–11:00, 5. 11:15–12:00, 6. 12:10–12:55, 7. 13:05–13:50, 8. 13:55–14:40, 9. 15:00–15:45. `Schools.LessonsRange` ma 15 przedziałów (pełna siatka szkoły).

## Zbiory danych → zasób → mapowanie → braki

| Dane | Zasób | Mapowanie | Braki / uwagi |
|---|---|---|---|
| Przedmioty | `Subjects` (20) | `Name`, `Short` (np. Mat, Pol, SSO) → nazwa i skrót | Brak dubli: oceny zwykłe i punktowe wskazują te same przedmioty (`Subject.Id`), więc scalanie jest naturalne. |
| Plan lekcji | `Timetables?weekStart=<pn>` | `Timetable[data][nr lekcji][]`: `LessonNo`, `HourFrom/To`, `Subject.Name/Short`, `Teacher.FirstName/LastName`, `Classroom.Id` (→ `Classrooms.Symbol`), `IsCanceled` → `CANCELLED`, `IsSubstitutionClass` → `SUBSTITUTION` (+ `Org*`: pierwotna data, lekcja, przedmiot, nauczyciel, godziny) | `SubstitutionNote` puste. `Pages.Next/Prev` do sąsiednich tygodni. |
| Zastępstwa | `Calendars/Substitutions` (11) | `IsCancelled`, `IsShifted`, `Org*` | Duplikuje informacje z planu — źródłem jest plan; ten zasób tylko jako uzupełnienie. |
| Oceny zwykłe | `Grades` (8) | `Grade` (1–6 z ±, `np`, `nb`), `Date`, `Semester`, `Category.Id`, `AddedBy.Id` (→ `Users`), flagi `IsSemester`, `IsSemesterProposition`, `IsFinal`, `IsFinalProposition` → rodzaj `REGULAR`/`PROPOSED`/`SEMESTER`/`FINAL` | Symbole bez wartości (`np`, `nb`) nie wchodzą do średniej. |
| Kategorie ocen | `Grades/Categories` (493, wszystkich nauczycieli) | `Name`, `CountToTheAverage` → „liczona do średniej”, `Short` | `Weight` (1/2/3) istnieje, ale szkoła liczy bez wag — ignorujemy. |
| Komentarze ocen | `Grades/Comments` (4) | `Text` → opis oceny (`Grade.Id`) | Żaden komentarz nie zawiera „%” ani punktów — procent z opisu zostaje wyłączony (SPEC 6.2). |
| Oceny punktowe | `PointGrades` (8) | `GradeValue` (np. „18.00”) = punkty; maksimum = `ValueTo` z kategorii | — |
| Kategorie punktowe | `PointGrades/Categories` (537) | `Name`, `ValueFrom` (0), `ValueTo` (maks.), `CountToTheAverage` | — |
| Średnia z Librusa | `Grades/Averages`, `PointGrades/Averages` | — | Zwracają tylko `Status` — Librus nie podaje średniej, więc nie ma z czym porównać (SPEC 6.3). |
| Oceny opisowe, tekstowe, z zachowania | `DescriptiveGrades`, `TextGrades`, `BehaviourGrades` | — | Puste (0). |
| Frekwencja | `Attendances` (155) | `Date`, `LessonNo`, `Semester`, `Type.Id`, `Lesson.Id` → przedmiot przez `Lessons` | Wpis nie ma przedmiotu wprost — przedmiot z `Lessons` (`Lesson.Id` → `Subject.Id`). Są też wpisy obecności, więc „lekcje z wpisem” da się policzyć. |
| Typy frekwencji | `Attendances/Types` (9) | patrz niżej | — |
| Terminarz | `HomeWorks` (11) | `Date`, `LessonNo` (10/11), `TimeFrom/To`, `Subject.Id` (8/11), `Category.Id`, `Content` → opis | Nazwa „HomeWorks” to w Librusie terminarz, nie zadania domowe. |
| Kategorie terminarza | `HomeWorks/Categories` (13) | patrz niżej | — |
| Zadania domowe | `HomeWorkAssignments` | — | Puste (0) — szkoła chyba nie używa zadań domowych w Librusie. Sekcja zadań będzie pusta. |
| Dni wolne | `ClassFreeDays` (31), `SchoolFreeDays` (0) | `DateFrom/To`, `LessonNoFrom/To` (część dnia) | Zawiera dni wszystkich klas — filtrujemy po `Class.Id` ucznia (`Me.Class`). Nazwa typu (`Type.Id`) — do sprawdzenia przy `LibrusSource`. `TeacherFreeDays` → 403 (brak dostępu dla ucznia). |
| Nauczyciele | `Users` (137 pracowników) | `FirstName`, `LastName` | — |
| Szczęśliwy numerek | `LuckyNumbers` | `LuckyNumber`, `LuckyNumberDay` | Numeru ucznia w dzienniku nie ma w `Me` — wpisuje się go w ustawieniach (SPEC 12.8). |
| Uwagi | `Notes` (1) | `Date`, `Teacher.Id`, `Category.Id`, `Positive` (liczba) → `POSITIVE`/`NEGATIVE`/`NEUTRAL`, `Text` | Znaczenie wartości `Positive` do potwierdzenia przy kolejnych uwagach (0/1/2?). |
| Ogłoszenia | `SchoolNotices` (15) | `StartDate`, `EndDate`, `Subject` → tytuł, `Content`, `AddedBy.Id`, `WasRead` | — |
| Wiadomości | `wiadomosci.librus.pl` | — | Osobny serwis z własnym logowaniem — nie sprawdzony. Do zbadania przy `LibrusSource`. |

## Mapowanie typów frekwencji (SPEC 7) — zaakceptowane 30.09.2026

| Typ (skrót) | Kategoria | Do limitu nieobecności |
|---|---|---|
| Obecność (ob) | `PRESENT` | nie |
| Spóźnienie (sp) | `LATE` | nie |
| Nieobecność (nb) | `ABSENT` | tak |
| Nieobecność uspr. (u) | `ABSENT_EXCUSED` | tak |
| Zwolnienie (zw) | `RELEASED` | nie |
| zwolniony usprawiedliwiony (zu) | `RELEASED` | nie |
| wf zwolnienie (wf) | `RELEASED` | nie |
| uroczystość obecność (uo) | `PRESENT` | nie |
| uroczystość nieobecność (un) | `OTHER` | nie (decyzja użytkownika) |

## Mapowanie kategorii terminarza (SPEC 11.3) — zaakceptowane 30.09.2026

| Kategoria w Librusie | Typ |
|---|---|
| Sprawdzian | `TEST` |
| Poprawa | `TEST` |
| Kartkówka | `QUIZ` |
| Dzień bez zajęć edukacyjnych | `DAY_OFF` |
| Warsztaty integracyjne, Spotkanie patronackie Ericsson | `TRIP` (wydarzenie szkolne) |
| Zebranie rodziców, Zebranie Rady Rodziców, Dzień otwarty dla rodziców | `TRIP` (wydarzenie szkolne) |
| Powtórzenie wiadomości, ZAJĘCIA ON-LINE, Spotkanie / lekcja online, Kwarantanna - lekcje zdalne. | `OTHER` |
| każda nowa, nieznana kategoria | `OTHER` (edytowalne w ustawieniach) |
