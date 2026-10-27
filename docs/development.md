# Сборка и разработка

[← Главная](../README.md) · [Установка готового APK](installation.md)

## Структура и требования

Основное приложение находится в wear/, необязательный телефонный relay — в mobile/. Gradle Wrapper и каталог зависимостей включены в исходники. Нужны JDK 17+, Android SDK Platform 37 и интернет для первой загрузки зависимостей. Версии инструментов указаны в gradle/libs.versions.toml и gradle/wrapper/gradle-wrapper.properties.

## Для разработчиков: собственное OAuth-приложение

> Этот раздел нужен только для самостоятельной сборки, форка или изменения Client ID. Пользователи APK со страницы Releases могут его пропустить.

1. Откройте [Yandex OAuth](https://oauth.yandex.ru/) и создайте приложение **для авторизации пользователей**. Для production заполните название, контакты и сведения, которые увидит пользователь.
2. В доступах выберите только:

   - `iot:view` — просмотр умного дома;
   - `iot:control` — управление устройствами, группами и сценариями.

3. В платформе «Веб-сервисы» добавьте Redirect URI для системного входа через телефон:

   ```text
   https://wear.googleapis.com/3p_auth/com.dynorixz.smarthome
   ```

   Для входа непосредственно в браузере часов и совместимости сохраните также:

   ```text
   dynorixzsmarthome://oauth/callback
   ```

   Для китайской сборки Wear OS понадобится `https://wear.googleapis-cn.com/3p_auth/com.dynorixz.smarthome`. `OAuthRequest` выбирает адрес для текущих часов. Scheme, host, port и path должны совпадать. HTTPS callback принимает системное приложение часов на телефоне и передаёт ответ в `RemoteAuthClient`; токен часы получают сами по PKCE. В приложении проверяются адрес возврата, срок сессии и `state`.

4. Для Android-платформы укажите package name `com.dynorixz.smarthome` и SHA-256 fingerprint сертификата debug или release сборки. Если консоль разделяет Android-платформу и Web services, Redirect URI добавляется в список Redirect URI, а package/fingerprint — в Android-платформу.
5. Скопируйте Client ID. Секрет приложения не копируйте: native client использует PKCE, и официальный token exchange допускает `client_id + code_verifier` без `client_secret`.

Официальные источники: [протокол пользовательского Smart Home API](https://yandex.ru/dev/dialogs/smart-home/doc/ru/concepts/platform-protocol), [получение authorization code и PKCE](https://yandex.com/dev/id/doc/en/codes/code-url), [регистрация OAuth-приложения](https://yandex.com/dev/id/doc/en/register-auth).

Параметры системного возврата и текущего сертификата описаны в [примечаниях по OAuth](oauth-1.4.0-setup.md).

## Для разработчиков: настройка Client ID

Скопируйте пример локальной конфигурации:

```powershell
Copy-Item local.properties.example local.properties
```

Отредактируйте `local.properties`:

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
YANDEX_CLIENT_ID=YOUR_REAL_CLIENT_ID
```

`local.properties`, keystore-файлы и другие credentials исключены из Git. Client ID сам по себе не является секретом, но хранится в единственной локальной точке настройки. Access token в build configuration не попадает.

## Сборка из исходников

Debug APK:

```powershell
.\gradlew.bat :wear:assembleDebug :mobile:assembleDebug
```

Результаты:

```text
wear/build/outputs/apk/debug/wear-debug.apk
mobile/build/outputs/apk/debug/mobile-debug.apk
```

Release build включает R8 и shrink resources. Для подписанных APK задайте четыре переменные окружения перед запуском Gradle:

```powershell
$env:DYNORIXZ_KEYSTORE_PATH='C:\secure\dynorixz-release.jks'
$env:DYNORIXZ_STORE_PASSWORD='<store-password>'
$env:DYNORIXZ_KEY_ALIAS='<key-alias>'
$env:DYNORIXZ_KEY_PASSWORD='<key-password>'
.\gradlew.bat :wear:assembleRelease :mobile:assembleRelease
```

Без этих переменных Gradle создаст unsigned release APK. Не коммитьте keystore или пароли. Подписывайте оба модуля одним ключом.

На Windows AGP может отказываться работать из каталога с нелатинскими символами. В проекте включён `android.overridePathCheck=true`; если сторонний инструмент всё равно некорректно обрабатывает путь, используйте checkout с ASCII-путём или временно подключите каталог буквой диска.


## Проверки

```text
.\gradlew.bat :wear:testDebugUnitTest :wear:lintDebug
```

В готовой версии 1.8.0 пройдены 143 теста. Самостоятельная сборка с другим сертификатом не обновляет официальный APK поверх.
