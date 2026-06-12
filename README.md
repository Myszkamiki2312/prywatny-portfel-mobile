# Prywatny Portfel Mobile (Android, cloud-first)

To jest osobny projekt Android dla mobilnej wersji Prywatnego Portfela.
Desktopowa aplikacja pozostaje bez zmian.

## Wymagania

- Android Studio (Hedgehog+)
- Android SDK 34
- JDK 17
- Projekt Supabase dla synchronizacji danych

## Uruchomienie APK / projektu

1. Otwórz w Android Studio folder projektu mobilnego.
2. Uzupełnij `web/supabase-config.js` wartościami z Supabase:
   - `url`
   - `anonKey`
3. W Supabase SQL Editor uruchom `docs/supabase-schema.sql`.
4. Poczekaj na synchronizację Gradle.
5. Uruchom aplikację na emulatorze lub telefonie.

## Jak to działa

- Frontend mobilny jest trzymany w `web/` i kopiowany do assets APK przy buildzie.
- Aplikacja Android to cienki WebView. Przy starcie odpala **wbudowany serwer Ktor na
  `http://127.0.0.1:18765`**, który serwuje frontend z assets i wystawia lokalne API `/api/*`
  (notowania, raporty, skaner, narzędzia) liczone z lokalnej bazy Room (mirror stanu).
- Dane portfela są źródłowo zapisywane w Supabase, w tabeli `app_states` (`CLOUD_ONLY_DATA`).
  Lokalna baza Room to tylko kopia robocza dla narzędzi offline oraz konfiguracja narzędzi
  (kopie, powiadomienia, alerty), trzymana w `kv_store`.
- Użytkownik widzi ekran logowania/rejestracji przez e-mail i hasło.
- Jeśli konto nie istnieje, aplikacja próbuje je utworzyć przez Supabase Auth.
- Lokalnie zostaje sesja logowania, konfiguracja połączenia i kopia robocza stanu, nie główne
  źródło prawdy portfela.

## Bezpieczeństwo lokalnego API

- Lokalne API `/api/*` wystawia cały stan portfela. Mimo bindowania na `127.0.0.1`, loopback jest
  osiągalny dla innych aplikacji na urządzeniu, dlatego każde żądanie `/api/*` wymaga nagłówka
  `X-Offline-Token`.
- Token jest losowany przy każdym starcie aplikacji (`SecureRandom`) i przekazywany do WebView
  wyłącznie w procesie, przez mostek JS `window.AndroidOffline.getOfflineToken()` — nigdy po HTTP.
- Statyczne assets UI pozostają otwarte (nie są tajne), chronione są tylko dane pod `/api/*`.
- Klucz `anon public` Supabase jest publiczny z założenia — dostęp do danych chroni RLS na tabeli
  `app_states` (patrz `docs/supabase-schema.sql`).

## Supabase

1. Utwórz projekt w Supabase.
2. W SQL Editor uruchom `docs/supabase-schema.sql`.
3. W `Authentication -> Providers -> Email` włącz logowanie e-mail/hasło.
4. Włącz `Confirm email`, jeśli nowe konta mają wymagać aktywacji z maila.
5. Skopiuj `Project URL` i `anon public key` do `web/supabase-config.js` przed wydaniem APK.
6. W `Authentication -> URL Configuration` dodaj Redirect URL:
   - `https://myszkamiki2312.github.io/prywatny-portfel/confirm-email.html`
   - `https://myszkamiki2312.github.io/prywatny-portfel/reset-password.html`

## Aktywacja konta

- Stare, już działające konta logują się normalnie.
- Nowe konto po rejestracji dostaje mail Supabase z linkiem do `confirm-email.html`.
- Po aktywacji użytkownik wraca do APK i loguje się swoim hasłem.
- Jeśli link wygasł, kliknij w aplikacji `Wyślij ponownie mail potwierdzający` i użyj najnowszego maila.

## Reset hasła

- Na ekranie logowania kliknij `Nie pamiętasz hasła?`.
- Aplikacja wyśle mail Supabase z linkiem do `reset-password.html`.
- Po zmianie hasła wróć do APK i zaloguj się nowym hasłem.
- Jeśli Gmail pokazuje błąd `otp_expired`, wyślij reset jeszcze raz i użyj najnowszego maila.

## Release APK

Projekt ma własny workflow GitHub Actions `Android APK`.

- push do `main` buduje artefakt APK,
- tag `android-v...` publikuje publiczny release z plikiem `prywatny-portfel-mobile.apk`.
