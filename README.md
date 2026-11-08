# Asystent Mieszkania (Android) v0.2

Kotlin + Jetpack Compose. minSdk 23 (Android 6), targetSdk 35 (Android 15).

## Budowanie APK
1. Wgraj zawartość tego folderu do prywatnego repozytorium (Add file → Upload files).
   Folder `.github` GitHub pomija przy przeciąganiu: plik `.github/workflows/build.yml`
   utwórz ręcznie (Add file → Create new file), jeśli go jeszcze nie masz.
2. Zakładka Actions → Build APK → po zielonym znaczniku pobierz `asystent-debug-apk`.

Aktualizacja: nadpisz pliki w repozytorium (Upload files nadpisuje istniejące).
Usuń stary plik `app/src/main/java/app/asystent/mieszkania/AppViewModel.kt` (zastąpił go `Hub.kt`).

## Serwer WWW
Ustawienia → Serwer WWW → włącz. Aplikacja pokaże adres, np. `http://192.168.1.20:8080`.
Otwórz go w przeglądarce na innym urządzeniu w tej samej sieci WiFi.
Strona pokazuje te same ekrany co aplikacja, pozwala sterować światłem i pytać asystenta.
PIN (opcjonalny) chroni dane i sterowanie. Zarezerwuj stały adres IP telefonu w routerze.
