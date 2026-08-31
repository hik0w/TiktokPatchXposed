# TiktokPatchXposed

LSPosed-модуль с набором исправлений и дополнительных функций для TikTok.

Текущая версия модуля: **3.11** (`com.golda.patchertiktok`). Работа проверена с TikTok **46.7.3** (`com.zhiliaoapp.musically`).

## Возможности

- Подмена SIM-карты и оператора на Германию для доступа к региональным функциям при сохранении русского языка приложения.
- Российский профиль запросов ленты рекомендаций (`RU`, `25001`, `ru-RU`) и дополнительная фильтрация роликов, явно помеченных TikTok как немецкоязычные.
- Скачивание видео без водяного знака.
- Скрытие рекламы в ленте и при запуске TikTok, включая TopView, псевдорекламу и рекламу, добавленную после загрузки страницы.
- Фильтрация LIVE-трансляций и скрытие кнопки LIVE в левом верхнем углу.
- Постоянно доступная полоса перемотки для обычных видео, включая короткие ролики.
- Скрытие видео с серверной пометкой «Ваши вероятные знакомые» без отключения обычных новых рекомендаций.
- Исправление входа через Google на основе идеи патча ReVanced для TikTok.
- Автопродление только уже существующих огоньков, вошедших в период риска `SECONDARY_ACTIVE`. Обычным диалогам и активным огонькам системное сообщение не отправляется.
- Защита от повторной отправки: каждый успешно обработанный диалог сохраняется до следующего календарного дня.
- Запуск автопродления в 12:00 по локальному времени устройства. Если Android выгрузил TikTok, модуль делает попытку разбудить приложение и выполняет пропущенный запуск при следующем открытии в течение 12 часов.
- Оптимизированные UI-хуки работают только в основном процессе TikTok. Постоянного сервиса, фонового опроса, обработчика загрузки системы и требования отключать экономию батареи нет.

## Региональный профиль

- Страна и SIM: `DE` / `de`
- MCC/MNC: `26201`
- Оператор: `Telekom.de`
- Язык приложения: `ru-RU`
- Язык рекомендаций: `ru`
- Сигналы запросов ленты: `RU` / `25001`; используется локальное время устройства
- Остальные региональные сигналы TikTok: `DE` / `26201` / `Telekom.de`
- Часовой пояс приложения: локальный часовой пояс устройства

## Совместимость

Основные маппинги автогоньков проверены на TikTok 46.7.3. В модуле оставлены резервные маппинги для TikTok 46.6.3, 46.5.3, 46.4.3 и 46.4.1.

После обновления TikTok внутренние обфусцированные классы могут измениться. Перед использованием автогоньков на новой версии TikTok желательно дождаться подтверждения совместимости модуля.

## Установка

1. Установите APK модуля.
2. Включите модуль для TikTok в LSPosed.
3. Принудительно остановите TikTok и откройте его заново.

## Важно

- Использование Xposed-модулей может привести к нестабильной работе приложения или блокировке аккаунта. Все изменения применяются на ваш риск.
- Автогоньки и фильтрация рекламы/LIVE включены по умолчанию.
- Экспериментальный перевод комментариев удалён из стабильной сборки.
- Для новых рекомендаций подмена применяется только к запросам ленты. Кэшированные ролики с явной меткой языка `de` фильтруются локально, но на рекомендации также могут влиять история аккаунта, IP-адрес и действия пользователя.
- Изменение скорости воспроизведения не добавлено: для него пока нет стабильного Xposed-хука, не зависящего от версии TikTok.

---

## English

An LSPosed module with fixes and additional features for TikTok.

Current module version: **3.11** (`com.golda.patchertiktok`). Verified with TikTok **46.7.3** (`com.zhiliaoapp.musically`).

### Features

- Germany SIM/operator spoof for regional feature availability while keeping the app language Russian.
- Russian recommendation-feed request profile (`RU`, `25001`, `ru-RU`) plus fallback filtering for items explicitly tagged by TikTok as German-language content.
- Download videos without a watermark.
- Startup and in-feed ad filtering, including TopView, pseudo-ad markers and ads inserted after a page is loaded.
- LIVE feed filtering and removal of the top-left LIVE button.
- An always-available seekbar for normal videos, including short clips.
- Filtering for videos marked as "People you may know" without disabling ordinary fresh recommendations.
- Google Auth Fix based on the ReVanced TikTok Google login patch idea.
- Auto streak renewal only for existing streaks that entered TikTok's at-risk `SECONDARY_ACTIVE` window. Normal conversations and healthy active streaks are skipped.
- Daily per-conversation duplicate protection.
- Auto streak processing at 12:00 in the device's local time, with best-effort wake-up and a 12-hour catch-up window when TikTok is reopened.
- UI hooks run only in TikTok's main process. The module does not use a persistent service, polling daemon, boot receiver or battery-optimization exemption.

### Region profile

- Country and SIM: `DE` / `de`
- MCC/MNC: `26201`
- Operator: `Telekom.de`
- App locale: `ru-RU`
- Recommendation language: `ru`
- Recommendation-feed signals: `RU` / `25001`; the device's local time is used
- Other TikTok market signals: `DE` / `26201` / `Telekom.de`
- App time zone: the device's local time zone

### Compatibility and installation

Primary auto-streak mappings were verified with TikTok 46.7.3. Fallback mappings are included for TikTok 46.6.3, 46.5.3, 46.4.3 and 46.4.1.

1. Install the module APK.
2. Enable it for TikTok in LSPosed.
3. Force-stop and reopen TikTok.

Internal obfuscated classes may change after a TikTok update. Verify compatibility before relying on auto streak with a newer TikTok version.

### Important

- Xposed modules may cause account restrictions or unstable app behavior. Use this module at your own risk.
- Auto streak and ad/LIVE filtering are enabled by default.
- Experimental comment translation was removed from the stable build.
- Region spoofing applies only to recommendation-feed requests. Cached items explicitly tagged `de` are filtered locally, but account history, IP location and interactions may still affect recommendations.
- Playback-speed controls were not added because there is no stable version-independent Xposed hook yet.
