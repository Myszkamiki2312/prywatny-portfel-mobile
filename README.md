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
- Dane portfela są zapisywane w Supabase, w tabeli `app_states`.
- Użytkownik widzi ekran logowania/rejestracji przez e-mail i hasło.
- Jeśli konto nie istnieje, aplikacja próbuje je utworzyć przez Supabase Auth.
- Lokalnie zostaje tylko sesja logowania oraz konfiguracja połączenia, nie główny stan portfela.

## Supabase

1. Utwórz projekt w Supabase.
2. W SQL Editor uruchom `docs/supabase-schema.sql`.
3. W `Authentication -> Providers -> Email` włącz logowanie e-mail/hasło.
4. Jeśli chcesz, żeby po rejestracji przychodził e-mail potwierdzający, zostaw włączone potwierdzanie e-mail.
5. Skopiuj `Project URL` i `anon public key` do `web/supabase-config.js` przed wydaniem APK.

## Release APK

Projekt ma własny workflow GitHub Actions `Android APK`.

- push do `main` buduje artefakt APK,
- tag `android-v...` publikuje publiczny release z plikiem `prywatny-portfel-mobile.apk`.
