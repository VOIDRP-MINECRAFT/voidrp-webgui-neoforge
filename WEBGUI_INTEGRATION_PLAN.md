# WebGUI Integration Plan

Цель: перевести весь in-game UI со старых Bukkit инвентарных GUI / chat-команд на HTML/CSS/React страницы через мод WebGUI (MCEF/Chromium).

---

> **⚠️ АКТУАЛЬНЫЙ МОД: `voidrp-webgui-neoforge`**
>
> Репозиторий `voidrp-webgui` (Fabric-форк upstream) **архивирован** (2026-06-23) и больше не используется.
> Активная разработка ведётся исключительно в **`voidrp_webgui_neoforge/`** (нативный NeoForge порт).
> Все кастомизации (`run_command`, `open_gui`, `open_hud` каналы, MCEF зеркало) уже перенесены в NeoForge-версию.
> Если нужно применить upstream-обновления — портировать напрямую в `voidrp_webgui_neoforge/`, минуя Fabric-форк.

---

## Архитектурные решения

### Как это работает на нашем стеке

**Ядро сервера: NeoForge 1.21.1-232 + Sinytra Connector** (запускает Fabric моды на NeoForge).

**Исходники мода:** `voidrp_webgui_neoforge/` — **нативный NeoForge мод** (порт с Fabric, 2026-06-07). Собирается через `./gradlew jar`, выходной jar: `build/libs/webgui-1.3.0+mc1.21.1.jar` (75 КБ).

**MCEF:** `mcef-keksuccino-2.2.0-1.21.1-fabric.jar` остаётся в лаунчер-паке — Connector загружает его для CEF/Chromium. Нативный мод использует MCEF только при компиляции (`compileOnly`).

**Правильная архитектура:**
- Сервер: `webgui-1.3.0+mc1.21.1.jar` (нативный NeoForge) **активен** в `mods/`
- Клиент: тот же jar + `mcef-keksuccino-2.2.0-1.21.1-fabric.jar` в лаунчер-паке (`REQUIRED_LOCKED_MODS`)
- Сервер: Paper-плагин (`WebGuiBridgeService`) отправляет пакеты через **NeoForge reflection bridge** → `WebviewNetworking.openGui()` → `PacketDistributor.sendToPlayer()`. Fallback: `sendPluginMessage()` если мод не на classpath. Токен подписывается NeoForge модом из `config/webgui/server.json` (см. P1.12)
- Клиент: нативный NeoForge WebGUI мод получает пакеты по channel ID `webgui:open_web` / `webgui:set_main_menu` и рендерит браузер
- Каналы зарегистрированы с `.optional()` → handshake всегда проходит без ошибок

### Токены (auth)

Мод на **клиенте** декодирует токен из URL и позволяет JS читать его через `window.webgui`. Генерирует токен Paper-плагин (`WebGuiBridgeService.signUrl()`) и подставляет его в URL перед отправкой пакета клиенту.

Токен — HMAC-SHA256:
```
payload = base64url("1|<playerUUID>|<expiresAtEpoch>")
token   = payload + "." + base64url(HMAC-SHA256(payload, secret))
```

Секрет хранится в **двух** местах (не трёх):
- `config/webgui/server.json` → `tokenSecretBase64` — мод подписывает токены здесь
- `.env` бэкенда → `WEBGUI_TOKEN_SECRET_BASE64` — FastAPI верифицирует

### Транзакции, требующие Vault / инвентаря

Браузер не имеет доступа к Vault или инвентарю игрока. Схема:

- **READ** (просмотр книги ордеров, история сделок, баланс казны) → браузер напрямую в бэкенд через `webgui_token`
- **WRITE с деньгами** (buy order, cancel buy, donate) → браузер создаёт `pending_web_action` в бэкенде → плагин подхватывает на тике (раз в секунду), списывает через Vault, подтверждает в бэкенде
- **WRITE с предметами** (sell order) → плагин берёт предмет из руки по команде (`/pm sell 10 500`), затем открывает WebGUI уже с созданным ордером
- **Pickup** → кнопка "Забрать" в web-странице создаёт pending pickup action → плагин выдаёт на тике (или игрок вводит `/pm pickup`)

---

## Этап 0: Фундамент

### 0.1 Сборка и деплой WebGUI мода
- [x] Собрать нативный NeoForge jar: `cd voidrp_webgui_neoforge && ./gradlew jar`
- [x] Выходной jar: `build/libs/webgui-1.3.0+mc1.21.1.jar` (75 КБ, нативный NeoForge)
- [x] Добавить jar в клиентский модпак лаунчера (`/home/mironoouv/launcher/pack/mods/`)
- [x] `mcef-keksuccino-2.2.0-1.21.1-fabric.jar` оставить в лаунчер-паке — Connector грузит его для CEF
- [x] `generate_launcher_manifest.py` — webgui в `REQUIRED_LOCKED_MODS`, slug `"webgui"` совпадает с именем jar
- [x] **Серверный jar обновлён** — `webgui-1.3.0+mc1.21.1.jar` (NeoForge) в `minecraft_server/mods/`
- [x] `displayTest="IGNORE_ALL_VERSION"` добавлен в `neoforge.mods.toml` — исключает дисконнект при несовпадении версий (P1.11)
- [x] Channel registration в `WebGuiBridgeService` обёрнута в try-catch — исключает Channel conflict (P1.10)
- [ ] **Требуется рестарт сервера** — для загрузки нового NeoForge jar (`displayTest` применяется только после рестарта)
- [ ] **Клиент** — перезапустить лаунчер для скачивания нового jar (SHA256: `0C2373820B7ED0DDE973C6DE83C9332C57E66F88407BB6A2F79824DE1F393A43`)

### 0.2 Настройка `server.json` WebGUI мода
- [x] Запустить сервер один раз — мод создаст `config/webgui/server.json` с авто-сгенерированным секретом
- [x] Заполнить `server.json` (`enableTokens`, `autoHudOnJoin: true`, URL-а)
- [x] `WEBGUI_TOKEN_SECRET_BASE64` добавлен в `minecraft_backend/.env`
- [x] Добавить хелпер `service/WebGuiBridgeService.java` в плагин — `openGui/openHud` + удобные методы `openMarket`, `openNationMarket`, `openTreasury`, `openAlliance`, `openBattlepass`, `openQuests`
- [x] Добавить в `config.yml` плагина секцию `webgui:` с `enabled: false` и всеми URL-ами (включается вручную когда фронтенд-страницы готовы)
- [x] Горячая перезагрузка конфига: `/webgui reload` (OP 2) — перечитывает `config/webgui/server.json` без рестарта сервера

### 0.3 Верификация токена в бэкенде
- [x] `WEBGUI_TOKEN_SECRET_BASE64` добавлен в `.env` бэкенда
- [x] Создан `dependencies/webgui_auth.py` — `get_webgui_player(token, db) → PlayerAccount`
- [x] Верификация: HMAC-SHA256, проверка expiry, lookup по `minecraft_nickname_normalized`
- [x] `webgui_token_secret_base64` добавлен в `config.py` Settings
- [x] Токен в моде изменён: содержит nickname вместо UUID (наш форк не хранит Mojang UUID)

### 0.4 Auto HUD и Main Menu на join
- [x] Проверено — работает. `autoHudOnJoin` выключен (не нужен при входе)

---

## Этап 1: Player Market

Самый сложный существующий GUI — заменяем `PlayerMarketGuiService.java` (Bukkit inventory) на web.

### 1.1 Бэкенд: роутер `/api/v1/game-ui/market`
- [x] Создать `apps/api/app/api/routes/game_ui_market.py` с `webgui_auth` dependency
- [x] `GET /game-ui/market/order-book/{item_key}` — книга ордеров для конкретного товара
- [x] `GET /game-ui/market/my-orders` — мои ордера (sell + buy) по UUID из токена
- [x] `GET /game-ui/market/items` — список товаров (как `list_items_summary`)
- [x] `GET /game-ui/market/trades` — история сделок
- [x] `POST /game-ui/market/pending-action` — создать pending действие (buy/cancel) для выполнения плагином
- [x] `GET /game-ui/market/pickup-ready` — сколько незабранных доставок у игрока

### 1.2 Бэкенд: pending web actions (новая таблица)
- [x] Модель `PlayerMarketWebAction` (id, player_uuid, action_type, payload_json, status, created_at)
- [x] Alembic миграция
- [x] Плагин поллит `GET /game-sync/market-web-actions` каждую секунду (`WebActionPollService`), исполняет через Vault + подтверждает через `/ack`

### 1.3 Плагин: замена GUI
- [x] `/shop`, `/market`, `/pm orders` → `webGuiBridge.openMarket(player)` когда `webgui.enabled: true`
- [x] `handlePickupCommand` — оставлен как есть (web кнопка → pending action → плагин доставляет)
- [x] `WebActionPollService` — поллер с `inFlight`-защитой, обрабатывает `buy`, `cancel_sell`, `cancel_buy`, `pickup`
- [ ] ~~Убрать `PlayerMarketGuiService.java`~~ — **не трогать** пока WebGUI не подтверждён стабильным; служит fallback при `webgui.enabled=false`
- [ ] ~~Убрать `PlayerMarketGuiListener.java`~~ — **не трогать**; маршрутизирует между WebGUI и старым GUI, без него команды не работают

### 1.4 Фронтенд: `/game-ui/market`
- [x] Маршрут `/game-ui/market` с `hidePublicShell: true` — нет навбара/футера
- [x] `GameUiMarketView.vue` — вкладки: Товары (книга ордеров + buy modal), Мои ордера, История, Получить
- [x] `gameUiMarketApi.js` — токен из `?webgui_token=` query param, подставляется в каждый запрос
- [x] Кнопка "Забрать всё" → `POST pending-action {action_type: "pickup"}`
- [x] Кнопка "Отменить ордер" → `POST pending-action {action_type: "cancel_sell"|"cancel_buy"}`
- [x] i18n ключи добавлены в `ru.js` и `en.js`
- [x] Реалтайм обновление через polling (каждые 5 сек, пока GUI открыт)

---

## Этап 2: HUD-оверлей

Постоянный прозрачный оверлей поверх игры. Показывает: баланс, нация, прогресс квестов, количество незабранных доставок.

### 2.1 Бэкенд: `/api/v1/game-ui/hud`
- [ ] `GET /game-ui/hud/snapshot` — компактный дата-объект: balance (из Vault — нужен плагин!), nation_name, quest_count, pending_deliveries
- [ ] Баланс через Vault невозможен из бэкенда → плагин пушит баланс в Redis/бэкенд на join и при изменении, HUD читает кэш

### 2.2 Плагин: sync баланса
- [ ] На PlayerJoinEvent и раз в 30 сек: `POST /game-ui/hud/balance-update {uuid, balance}`
- [ ] Vault EconomyChangeEvent → push обновления (если ESGUI/Vault поддерживает)

### 2.3 Фронтенд: `/game-ui/hud`
- [ ] `GameUiHudView.vue` — минималистичный оверлей (баланс, нация, иконка маркета с количеством незабранных)
- [ ] Polling каждые 10 сек
- [ ] CSS: полупрозрачный фон, не мешает gameplay, поддержка `pointer-events: none` в пассивном режиме

---

## Этап 3: Main Menu (клавиша F6)

Красивое серверное меню при нажатии F6.

### 3.1 Фронтенд: `/game-ui/menu`
- [x] `GameUiMenuView.vue` — полноэкранное меню: кнопки (Рынок, Казна, Альянсы, Боевой пропуск, Квесты, Сайт)
- [x] Кнопки открывают другие GUI через `window.webgui` JS bridge → `postToGame` с командой (`run_command`)

---

## Этап 4: Nation Market

### 4.1 Плагин
- [ ] `/nmarket` → `webGuiBridge.openGui(player, config.getNationMarketUrl())`
- [ ] Убрать `NationMarketGuiService.java`, `NationMarketGuiListener.java`
- [ ] Покупка через web → pending action → плагин исполняет

### 4.2 Бэкенд: `/api/v1/game-ui/nation-market`
- [ ] `GET /game-ui/nation-market/listings` — все активные листинги с фильтрами
- [ ] `POST /game-ui/nation-market/buy-action` — pending action на покупку
- [ ] Продавец создаёт листинг через плагин-команду (предмет из руки) — без изменений

### 4.3 Фронтенд: `/game-ui/nation-market`
- [ ] `GameUiNationMarketView.vue` — список листингов, фильтрация по нации/товару
- [ ] Кнопка "Купить" → pending action → `Ожидайте подтверждения…`

---

## Этап 5: Nation Treasury

### 5.1 Бэкенд: `/api/v1/game-ui/treasury`
- [ ] `GET /game-ui/treasury/summary` — баланс + последние транзакции нации игрока
- [ ] `POST /game-ui/treasury/donate-action` — pending action (плагин списывает через Vault)
- [ ] `POST /game-ui/treasury/withdraw-action` — только для офицеров/глав (проверить роль через нацию)

### 5.2 Плагин
- [ ] `/ntreasury` → `webGuiBridge.openGui(player, config.getTreasuryUrl())`
- [ ] Чат-команды `/ndonate` и `/nwithdraw` — оставить как запасной вариант

### 5.3 Фронтенд: `/game-ui/treasury`
- [ ] `GameUiTreasuryView.vue` — баланс нации, история транзакций, форма доната/вывода

---

## Этап 6: Battle Pass

Требует интеграции с `voidrp_battlepass` (Kotlin, Paper).

### 6.1 Бэкенд
- [ ] `GET /game-ui/battlepass/status` — прогресс игрока по UUID из webgui_token
- [ ] Данные берутся из существующей БД battle pass

### 6.2 Плагин (voidrp_battlepass)
- [ ] Добавить `WebGuiBridgeService` (либо как shared library, либо скопировать)
- [ ] `/bp` (или кнопка в меню) → `webGuiBridge.openGui(player, hudUrl + "/battlepass")`
- [ ] Убрать Bukkit inventory GUI

### 6.3 Фронтенд: `/game-ui/battlepass`
- [ ] `GameUiBattlePassView.vue` — текущий уровень, прогресс XP, награды

---

## Этап 7: Daily Quests

Аналогично Battle Pass.

### 7.1 Бэкенд
- [ ] `GET /game-ui/quests/today` — список квестов дня + прогресс игрока

### 7.2 Плагин (voidrp_daily_quests)
- [ ] `/quests` → `webGuiBridge.openGui(player, ...)`
- [ ] Убрать Bukkit inventory GUI

### 7.3 Фронтенд: `/game-ui/quests`
- [ ] `GameUiQuestsView.vue` — список квестов, прогресс-бары, статус выполнения

---

## Этап 8: Alliance

### 8.1 Бэкенд
- [ ] `GET /game-ui/alliance/my` — текущий альянс + список союзников
- [ ] `GET /game-ui/alliance/proposals` — входящие/исходящие предложения
- [ ] `POST /game-ui/alliance/vote-action` — голосование (pending action для плагина)

### 8.2 Плагин
- [ ] `/ally` → `webGuiBridge.openGui(player, config.getAllianceUrl())`
- [ ] Чат-команды оставить как есть

### 8.3 Фронтенд: `/game-ui/alliance`
- [ ] `GameUiAllianceView.vue` — участники, предложения, голосование

---

## Этап 9: CPM Cosmetics (voidrp-cpm-companion)

CPM — NeoForge мод. Не может напрямую слать plugin channel пакеты.  
Решение: Paper-сторона (VoidRpGameSync) регистрирует команду `/vc gui` и открывает WebGUI.

### 9.1
- [ ] VoidRpGameSync: новая команда `/vc gui <player>` → `webGuiBridge.openGui(player, cosmeticsUrl)`
- [ ] NeoForge мод посылает Paper-команду через RCON или Bukkit-совместимый механизм при `/vc list`

### 9.2 Бэкенд
- [ ] `GET /game-ui/cosmetics/my` — косметика игрока (из `players.json` CPM или перенести в БД)

### 9.3 Фронтенд: `/game-ui/cosmetics`
- [ ] `GameUiCosmeticsView.vue` — выданные косметики, слоты, кнопки экипировки/снятия

---

## Технические детали для разработки

### Открытие WebGUI из Paper-плагина (VoidRpGameSync)

WebGUI Fabric мод активен на сервере (нужен для mod-list negotiation), но Bukkit не может диспатчить Fabric-команды. Поэтому весь транспорт пакетов — через `WebGuiBridgeService` в плагине:

```java
// GUI (полный экран):
webGuiBridge.openGui(player, "https://void-rp.ru/game-ui/market");

// HUD (оверлей):
webGuiBridge.openHud(player, "https://void-rp.ru/game-ui/hud");

// Задать URL главного меню (клавиша F6):
webGuiBridge.sendMainMenuUrl(player, "https://void-rp.ru/game-ui/menu");
```

`WebGuiBridgeService.signUrl()` добавляет `?webgui_token=<signed>` к URL перед отправкой.
Пакеты отправляются через `player.sendPluginMessage()` — raw Custom Payload, без Fabric handshake.

```
Канал: webgui:open_web
Payload: VarInt(protocolVersion=1) + VarInt(mode 0=GUI / 1=HUD) + MCString(url_with_token)

Канал: webgui:set_main_menu
Payload: MCString(url)
```

### Token verification в FastAPI

```python
# dependencies/webgui_auth.py
import base64, hashlib, hmac, time
from fastapi import Query, HTTPException
from uuid import UUID

def get_webgui_player_uuid(
    webgui_token: str = Query(...)
) -> UUID:
    secret = base64.b64decode(settings.WEBGUI_TOKEN_SECRET_BASE64)
    dot = webgui_token.find(".")
    enc_payload, enc_sig = webgui_token[:dot], webgui_token[dot+1:]
    payload_bytes = base64.urlsafe_b64decode(enc_payload + "==")
    sig_bytes     = base64.urlsafe_b64decode(enc_sig + "==")
    expected_sig  = hmac.new(secret, payload_bytes, hashlib.sha256).digest()
    if not hmac.compare_digest(expected_sig, sig_bytes):
        raise HTTPException(401)
    version, uuid_str, exp_str = payload_bytes.decode().split("|")
    if int(exp_str) < time.time():
        raise HTTPException(401, "token expired")
    return UUID(uuid_str)
```

### Фронтенд: установка `@webgui/react`

```bash
# В VOIDRP-SITE/
yarn add @webgui/react
```

```js
// В игровых страницах
import { useWebGUIToken, useWebGUIClient } from '@webgui/react'

const token = useWebGUIToken()  // строка токена из ?webgui_token=
const client = useWebGUIClient() // { playerUuid, username, pos, ... }
```

---

---

## Обновление мода до v1.3.0 (2026-06-07)

Выполнен `git rebase upstream/main` — наши 3 кастомных коммита переложены поверх upstream v1.3.0.

### Что добавил upstream

**v1.2.0 — двунаправленные события (page↔game):**
- `WebviewServerEvents.java` — сервер регистрирует handler-ы на события от страницы
- `WebviewClientEmit.java` — клиент диспатчит события из S2C пакета в JS (`window.dispatchEvent`)
- Новые payload-ы: `WebviewEmitS2CPayload` (server→page) + `WebviewPageToServerPayload` (page→server)
- Публичный API: `WebviewApi.registerPageEventHandler(name, handler)`

**v1.3.0 — entity binding (WebGUI к сущностям):**
- `EntityBindingStore.java` — хранит UUID→{url, cancelInteraction} в `config/webgui/entity_bindings.json`
- `EntityInteractionListener.java` — правый клик по entity → открывает привязанный URL с плейсхолдерами `{entityId}`, `{entityType}`, `{playerName}`, `{playerUuid}`
- `EntityBinding.java`, `WebviewEntityContext.java`, `WebviewPlaceholders.java`
- Команды: `/webgui bind entity <selector> <url> [cancelInteraction]` и `/webgui unbind entity <selector>`
- В JS: `window.webgui.entity` — контекст открывшей entity (id, type, pos)

### Что сохранено из наших кастомизаций

| Коммит | Файл | Что сделали |
|--------|------|-------------|
| `configure for VoidRP` | `gradle.properties` | Прокси-настройки сохранены; `mod_version` обновлён до `1.3.0` |
| `add /webgui reload` | `WebviewCommands.java` | Конфликт с upstream: upstream уже добавил reload (вызывает `WebviewServerConfig.reload()` + `EntityBindingStore.load()`). Наш дублирующий reload удалён — upstream-версия лучше. |
| `payload type registration` | `WebGUIClient.java` + `WebGUIMod.java` | NeoForge-фикс **сохранён**: `registerPayloadTypes()` только в `onInitializeClient()`. Из `WebGUIMod.onInitialize()` убрано; добавлен новый `registerServerReceivers()` от upstream. |

### Артефакт требует пересборки

Jar пересобрать командой:
```bash
cd voidrp_webgui && ./gradlew build -P stonecutter.version=1.21.1
```
Новый jar: `versions/1.21.1/build/libs/webgui-1.3.0+mc1.21.1.jar`.
Задеплоить на сервер и обновить в лаунчер-паке (заменить `webgui-1.1.0+mc1.21.1.jar`).

---

## Нативный NeoForge порт мода (2026-06-07) — ФИНАЛЬНОЕ РЕШЕНИЕ P1.5

**Статус: ВЫПОЛНЕНО — канальная проблема устранена полностью**

Fabric-мод через Connector был заменён нативным NeoForge-модом. Это устранило все проблемы negotiation раз и навсегда.

### Почему P1.5-фикс (client-only registration) не помог до конца

P1.5-фикс переносил регистрацию S2C-каналов в `onInitializeClient()`. Однако в нашей сборке Connector (`connector-2.0.0-youer.2+1.21.1+dev-g481af41-full.jar`) каналы, зарегистрированные в `onInitializeClient()`, всё равно помечались как «клиент требует от сервера» в NeoForge negotiation. После P1.5-фикса сервер перестал объявлять S2C-каналы, а клиент их по-прежнему ожидал → дисконнект.

Нативный NeoForge использует `registrar.optional()` — каналы явно помечаются как не требующиеся ни от сервера, ни от клиента. Negotiation проходит чисто.

### Структура нового проекта

**Исходники:** `/home/mironoouv/minecraft/voidrp_webgui_neoforge/`  
**Сборка:** `./gradlew jar` → `build/libs/webgui-1.3.0+mc1.21.1.jar` (75 КБ)  
**Деплой:** заменить оба jar (сервер + лаунчер-пак); старый Fabric jar — `.disabled`

```
voidrp_webgui_neoforge/
├── build.gradle                 — NeoGradle, compileOnly MCEF jar, archivesName='webgui'
├── gradle.properties            — neo_version=21.1.232, mod_id=webgui, mod_version=1.3.0
├── libs/mcef-keksuccino.jar     — MCEF Fabric jar только для компиляции; в рантайме Connector грузит оригинал
└── src/main/java/land/webgui/
    ├── WebGUIMod.java            — @Mod, регистрация сетевых каналов + серверных событий
    ├── WebviewNetworking.java    — ВСЕ каналы через .optional(), PacketDistributor для S2C
    ├── WebviewPayloads.java      — CustomPacketPayload + StreamCodec (NeoForge API)
    ├── WebGUIClientSetup.java    — @EventBusSubscriber CLIENT: FMLClientSetupEvent, RegisterKeyMappingsEvent
    ├── WebGUIClientForgeEvents.java — ClientTickEvent, RenderGuiEvent, InputEvent (FORGE bus)
    ├── WebGUIForgeEvents.java    — ServerStartingEvent, PlayerLoggedInEvent, RegisterCommandsEvent (FORGE bus)
    ├── WebHudOverlay.java        — HUD оверлей, рендер через RenderSystem + Tesselator (raw GL texture ID)
    ├── WebViewScreen.java        — extends Screen (Mojang mappings)
    ├── WebSession.java           — управление браузером (единый экземпляр)
    ├── WebviewClientBridge.java  — push client info в JS (window.webgui.client)
    ├── server/
    │   ├── WebviewServerEvents.java — CopyOnWriteArrayList вместо Fabric Event<T>
    │   ├── WebviewServerConfig.java — FMLPaths.CONFIGDIR вместо FabricLoader
    │   └── WebviewEntityContext.java — BuiltInRegistries вместо Fabric Registries
    ├── EntityBindingStore.java   — FMLPaths.CONFIGDIR
    └── mixin/
        ├── CefUtilMixin.java     — GPU флаги для CEF (remap=false)
        └── MouseHandlerMixin.java — @Mixin(MouseHandler.class) — Mojang-имя для Mouse
```

### Ключевые изменения при портировании (Fabric → NeoForge)

| Что | Fabric | NeoForge |
|-----|--------|----------|
| Mod entry | `ModInitializer.onInitialize()` | `@Mod` конструктор с `IEventBus modBus` |
| Client init | `ClientModInitializer.onInitializeClient()` | `FMLClientSetupEvent` на MOD bus |
| Events (server) | Fabric `ServerLifecycleEvents` | `ServerStartingEvent`, `ServerStartedEvent` (FORGE bus) |
| Events (client) | Fabric `ClientTickEvents` | `ClientTickEvent.Post` (FORGE bus) |
| Key bindings | `KeyBindingHelper.registerKeyBinding()` | `RegisterKeyMappingsEvent` (MOD bus) |
| Network channels | `PayloadTypeRegistry.playS2C()` | `RegisterPayloadHandlersEvent` + `.optional()` |
| S2C send | `ServerPlayNetworking.send()` | `PacketDistributor.sendToPlayer(player, payload)` |
| C2S receive | `ServerPlayNetworking.registerReceiver()` | handler в `registrar.playToServer()` |
| Payload type | `CustomPayload.Type` | `CustomPacketPayload.Type<T>` + `StreamCodec` |
| Config dir | `FabricLoader.getInstance().getConfigDir()` | `FMLPaths.CONFIGDIR.get()` |
| Mod version | `FabricLoader.getInstance().getModContainer()` | `ModList.get().getModContainerById()` |
| Entity registry | `Registries.ENTITY_TYPE.getId(type)` | `BuiltInRegistries.ENTITY_TYPE.getKey(type)` |
| GUI screen | `extends Screen` + Yarn mappings | `extends Screen` + Mojang mappings |
| `client.player.uuid` | `.getUuid()` | `.getUUID()` |
| Mixin mouse | `@Mixin(Mouse.class)` | `@Mixin(MouseHandler.class)` |
| Browser render | `browser.getTextureLocation()` → ResourceLocation | `browser.getRenderer().getTextureID()` → int |

### Почему `getTextureLocation()` нельзя использовать в NeoForge-компиляции

Метод `MCEFBrowser.getTextureLocation()` в Fabric jar возвращает `net.minecraft.class_2960` — intermediary-имя `ResourceLocation`/`Identifier`. В NeoForge classpath этого класса нет → компилятор падает. Решение: рендерить через raw GL texture ID:

```java
int texId = browser.getRenderer().getTextureID();
RenderSystem.setShader(GameRenderer::getPositionTexShader);
RenderSystem.setShaderTexture(0, texId);
var buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
var mat = context.pose().last().pose();
buf.addVertex(mat, 0, h, 0).setUv(0, 1);
buf.addVertex(mat, w, h, 0).setUv(1, 1);
buf.addVertex(mat, w, 0, 0).setUv(1, 0);
buf.addVertex(mat, 0, 0, 0).setUv(0, 0);
BufferUploader.drawWithShader(buf.buildOrThrow());
```

### Ключевой фикс в WebviewNetworking.java

```java
private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
    PayloadRegistrar registrar = event.registrar("1").optional();  // ← ВСЕ каналы опциональны
    registrar.playToClient(OpenWebS2CPayload.TYPE, OpenWebS2CPayload.STREAM_CODEC, (payload, ctx) -> ...);
    registrar.playToClient(WebUIMainMenuPayload.TYPE, ...);
    registrar.playToClient(WebviewEmitS2CPayload.TYPE, ...);
    registrar.playToClient(WebviewEntityContextS2CPayload.TYPE, ...);
    registrar.playToServer(WebviewPageEventC2SPayload.TYPE, ..., (payload, ctx) ->
        ctx.enqueueWork(() -> WebviewServerEvents.fire((ServerPlayer)ctx.player(), payload.channel(), payload.jsonPayload())));
}
```

`.optional()` — NeoForge не требует эти каналы ни от сервера ни от клиента при handshake.

### Состояние файлов после деплоя

```
minecraft_server/mods/
  webgui-1.3.0+mc1.21.1.jar          ← нативный NeoForge (активный)
  webgui-1.3.0+mc1.21.1.jar.disabled ← старый Fabric (отключён)
  webgui-1.1.0+mc1.21.1.jar.old      ← очень старый (для истории)

launcher/pack/mods/
  webgui-1.3.0+mc1.21.1.jar          ← нативный NeoForge (активный)
  mcef-keksuccino-2.2.0-1.21.1-fabric.jar  ← MCEF Fabric jar (MCEF нативки)
```

**Важно:** `mcef-keksuccino-2.2.0-1.21.1-fabric.jar` в лаунчер-паке должен оставаться — Connector грузит его для инициализации CEF/Chromium. Нативный WebGUI мод не содержит MCEF.

### Обновление манифеста

Jar назван `webgui-1.3.0+mc1.21.1.jar` (то же имя, что и Fabric jar) → `generate_launcher_manifest.py` распознаёт его по slug `"webgui"` в `REQUIRED_LOCKED_MODS` без изменений в скрипте.

```bash
python3 scripts/generate_launcher_manifest.py
# → mods/webgui-1.3.0+mc1.21.1.jar  ->  WebGUI  (жёлтый = required locked)
```

---

## Этап 10: Кастомизации исходного кода мода

Репозиторий у нас локально — можем менять мод под свои задачи. Изменения делаем в **`voidrp_webgui_neoforge/`** (нативный NeoForge порт, `voidrp_webgui/` архивирован), собираем `./gradlew jar`.

### 10.1 Добавить кастомные каналы page→game bridge

`WebviewPageToClientBridge.java` сейчас поддерживает только "close" и "log". Добавить:

- [x] `{"channel": "run_command", "command": "/pm pickup"}` — выполнить чат-команду от имени игрока (клиентски)
- [x] `{"channel": "open_gui", "url": "https://..."}` — открыть другой GUI прямо из страницы без запроса к серверу
- [x] `{"channel": "open_hud", "url": "https://..."}` — аналогично для HUD

**Статус: ВЫПОЛНЕНО** — реализовано в `WebviewPageToClientBridge.java` (NeoForge порт). Ключ `"command"` вместо `"text"` в плане.

Это позволяет кнопкам в main menu (F6) открывать другие GUI напрямую, без round-trip через плагин.

```java
case "run_command" -> {
    String text = obj.has("text") ? obj.get("text").getAsString() : "";
    if (!text.isBlank()) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (mc.player != null) mc.player.networkHandler.sendChatCommand(text.startsWith("/") ? text.substring(1) : text);
        });
    }
}
case "open_gui" -> {
    String url = obj.has("url") ? obj.get("url").getAsString() : "";
    if (!url.isBlank()) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.setScreen(new WebViewScreen(url)));
    }
}
```

### 10.2 Зеркало для скачивания Chromium (MCEF)
- [x] Скачан `windows_amd64.tar.gz` (119 МБ, commit `eaeb3d4370aa3526ee237ad1981ad59af3de4dd1`) и размещён на `https://void-rp.ru/launcher/mcef/java-cef-{commit}/`
- [x] Создан `config/mcef.properties` в лаунчер-паке: `download-mirror=https://void-rp.ru/launcher/mcef` — лаунчер доставляет его игрокам автоматически
- [x] Манифест обновлён — `config/mcef.properties` раздаётся через лаунчер
- [x] Исправлен формат файла `windows_amd64.tar.gz.sha256` на зеркале: был в формате PowerShell `Get-FileHash` (с заголовками `Algorithm / Hash / Path`) — MCEF ожидает только голый hex-хеш. Файл перезаписан: `aaff42ca9a0bf59f2f2590a1262f5facba6db68a90e63a31c926782750610862`
- [ ] Скачать и добавить `linux_amd64.tar.gz` и `macos_amd64.tar.gz` для других платформ (если понадобится)

### 10.3 Возможные будущие кастомизации (по необходимости)
- Кастомный стартовый URL при отсутствии сервера (bundled_web/index.html → наша страница)
- Добавить `{"channel": "chat", "text": "..."}` — написать в чат без выполнения команды
- Блокировка определённых URL (whitelist: только `void-rp.ru` и `localhost`)
- Кастомная пустая страница вместо дефолтной при пустом URL

---

## Порядок выполнения

1. **Этап 0** — без него ничего не работает
2. **Этап 10.1** — сразу после 0, нужен для main menu
3. **Этап 1** — самый большой и сложный, даёт понимание всего паттерна
4. **Этап 2** — HUD проще всего, наглядный результат
5. **Этап 3** — Main Menu быстро и эффектно
6. **Этапы 4-8** — можно делать параллельно, они независимы
7. **Этап 9** — CPM последним, там нестандартная интеграция

---

## Проблемные места и решения

### P1 (критические — блокируют интеграцию)

---

#### P1.1 `Bukkit.dispatchCommand("webgui gui ...")` — несовместим с Connector

**Статус: РЕШЕНО — Решение B применено (2026-06-06)**

**Причина:** `Bukkit.dispatchCommand()` не достигает Fabric-команд, зарегистрированных через Connector — команды попадают в vanilla Brigadier, но не в Bukkit CommandMap.

**Применённое решение (B):** `WebGuiBridgeService.java` переписан на прямую отправку plugin-channel пакетов через `player.sendPluginMessage()`. Токен генерируется в плагине той же HMAC-SHA256 логикой, что и в моде — секрет читается из `config/webgui/server.json`.
```
Канал: webgui:open_web
Payload: VarInt(1) + VarInt(mode 0/1) + MCString(url_with_token)

Канал: webgui:set_main_menu  
Payload: MCString(url)
```

---

#### P1.2 `ServerPlayConnectionEvents.JOIN` через Connector — может не срабатывать

**Проблема:** `WebviewJoinHud` слушает Fabric-событие входа игрока. Если Connector не транслирует его в NeoForge PlayerLoggedInEvent → игрок не получает HUD и mainMenuUrl при входе.

**Решение:** Добавить fallback в Paper-плагин — `PlayerJoinEvent` с задержкой в 3 секунды:
```java
@EventHandler
public void onJoin(PlayerJoinEvent e) {
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (e.getPlayer().isOnline())
            webGuiBridge.sendMainMenuUrl(e.getPlayer(), config.getMenuUrl());
            webGuiBridge.openHud(e.getPlayer(), config.getHudUrl());
    }, 60L); // 3 сек
}
```
Если WebviewJoinHud уже сработал — клиент просто получит тот же URL дважды (безвредно, WebGUI перезагружает HUD, но это ок).

**Статус: ВЫПОЛНЕНО** — `WebGuiPlayerJoinListener.java` добавлен, отправляет `sendMainMenuUrl` при входе (60-tick delay). URL берётся из `config.getWebGuiMenuUrl()` (`webgui.urls.menu`).

---

#### P1.3 Token expiry при долгой сессии

**Проблема:** TTL токена по умолчанию 900 секунд. Игрок работает с рынком 20+ минут → все API-запросы начинают возвращать 401 → страница «ломается».

**Решение A (простое):** Поднять TTL в `server.json` до 7200 (2 часа). Для наших use-case достаточно.

**Решение B (если нужен refresh):** Добавить endpoint `POST /game-ui/auth/refresh` — принимает валидный (но близкий к expiry) токен, возвращает новый. Фронтенд вызывает его при 401 или за 60 сек до истечения. Но это требует хранить expiry в JS — добавляет сложность. Для v1 хватит A.

**Действие:** `tokenTtlSeconds: 7200` в `server.json`.

---

#### P1.5 NeoForge channel negotiation error — клиент дисконнектился при входе

**Статус: РЕШЕНО ОКОНЧАТЕЛЬНО — нативный NeoForge мод (2026-06-07)**  
*(промежуточный P1.5-фикс через `onInitializeClient()` не помог в нашей сборке Connector — см. раздел «Нативный NeoForge порт»)*

**Ошибка в логе клиента (до фикса):**
```
[Render thread/WARN] [ModMismatchDisconnectedScreen]: Channel [webgui:open_web] failed to connect:
Клиент хочет, чтобы полезная нагрузка передавалась в: CLIENTBOUND, но сервер ее не поддерживает!
[Render thread/INFO]: Client disconnected with reason: Клиент несовместим! Используйте NeoForge 21.1.232
```

**Полная цепочка причин (оригинальный апстрим мод):**
1. Сервер: Connector загружает webgui.jar → `WebGUIMod.onInitialize()` → `WebviewNetworking.registerPayloadTypes()` — каналы регистрируются в Fabric channel negotiation
2. Клиент: Connector загружает тот же jar → `WebGUIMod.onInitialize()` тоже запускается → те же каналы регистрируются **в common-init**
3. Когда Connector видит S2C-канал, зарегистрированный в common-init клиента, он помечает его как «клиент требует от сервера» в NeoForge negotiation
4. При handshake сервер не отвечает в нужном формате → `ModMismatchDisconnectedScreen` → дисконнект

**Фикс применён в `voidrp_webgui/src/main/java/land/webgui/WebGUIMod.java`:**

```java
@Override
public void onInitialize() {
    // Register S2C payload types server-side only.
    // On the client this is done in WebGUIClient.onInitializeClient() to prevent
    // Connector from marking these channels as "required from server" in NeoForge's
    // handshake negotiation (the server doesn't announce them, causing disconnect).
    if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
        WebviewNetworking.registerPayloadTypes();
    }
    // ...
}
```

```java
// WebGUIClient.java — регистрация каналов ТОЛЬКО в client-init
@Override
public void onInitializeClient() {
    WebviewNetworking.registerPayloadTypes();
    // ...
}
```

**Почему это работает:** Connector не помечает каналы, зарегистрированные в `onInitializeClient()`, как «обязательные от сервера» — только те, что в `onInitialize()`. На сервере регистрация в common-init корректна (сервер объявляет, что умеет слать эти пакеты).

**Итоговое состояние:**
- Сервер: webgui.jar активен → Connector загружает, регистрирует каналы серверной стороне → NeoForge mod-list negotiation проходит (мод есть на обоих концах)
- Клиент: каналы зарегистрированы в `onInitializeClient()` → Connector не требует их от сервера при handshake → дисконнекта нет
- Сервер: `WebGuiBridgeService.sendPluginMessage()` отправляет raw bytes (Bukkit не диспатчит Fabric-команды — P1.1)

**Урок (что НЕ делать):** Удаление webgui.jar с сервера ломает всё сильнее — NeoForge mod-list negotiation видит, что у клиента есть мод `webgui`, а у сервера нет → все клиенты дисконнектятся сразу при входе. Мод должен быть на обоих концах.

---

#### P1.6 MCEF не инициализируется — неверный формат SHA256 файла на зеркале

**Статус: РЕШЕНО (2026-06-06)**

**Причина:** Файл `/var/www/void-rp/launcher/mcef/java-cef-eaeb3d4370aa3526ee237ad1981ad59af3de4dd1/windows_amd64.tar.gz.sha256` был сгенерирован PowerShell-командой `Get-FileHash` — содержал заголовки таблицы:
```
Algorithm       Hash                                                                   Path
---------       ----                                                                   ----
SHA256          AAFF42CA9A0BF59F2F2590A1262F5FACBA6DB68A90E63A31C926782750610862       D:\a\...
```
MCEF (keksuccino 2.2.0) при верификации скачанного архива читает этот файл как строку и сравнивает с вычисленным хешем. Формат не совпадал → MCEF считал архив повреждённым или не скачанным → `MCEF failed to initialize!`.

**Решение:** Перезаписать файл на голый lowercase hex-хеш (один `\n` в конце):
```
aaff42ca9a0bf59f2f2590a1262f5facba6db68a90e63a31c926782750610862
```

**Файл:** `/var/www/void-rp/launcher/mcef/java-cef-eaeb3d4370aa3526ee237ad1981ad59af3de4dd1/windows_amd64.tar.gz.sha256`

---

#### P1.7 MCEF не инициализируется — Windows file lock на CEF-нативках

**Статус: РЕШЕНО (2026-06-06)**

**Обнаружено:** при анализе полного лога запуска клиента (02:31–02:34).

**Симптом:** В логе видно, что MCEF успешно скачивает `windows_amd64.tar.gz` с GitHub (~119 МБ), но при распаковке падает:
```
[02:32:20] [MCEF-Downloader/ERROR] [MCEF/]: Failed to download or extract JCEF
java.io.FileNotFoundException: C:\Users\MIRON\AppData\Local\VoidRpLauncher\game\mods\mcef-libraries\windows_amd64\chrome_100_percent.pak
(Процесс не может получить доступ к файлу, так как этот файл занят другим процессом)
    at com.cinemamod.mcef.MCEFDownloader.extractTarGz(MCEFDownloader.java:518)
```

**Причина:** CEF/Chrome-процессы от предыдущей сессии (или зависшего `jcef_helper.exe`) держат файлы `chrome_*.pak` открытыми. Когда MCEF пытается перезаписать их при распаковке — Windows отклоняет операцию (file is in use).

**Решение (на клиенте):**
1. Полностью закрыть игру и лаунчер
2. Открыть Task Manager → завершить все процессы `chrome.exe`, `jcef_helper.exe`, `Chromium Embedded Framework`
3. Удалить директорию: `C:\Users\MIRON\AppData\Local\VoidRpLauncher\game\mods\mcef-libraries\windows_amd64\`
4. Запустить игру заново — MCEF скачает и распакует CEF чисто

**Дополнительно — зеркало не применяется:**  
В логе MCEF качает с `https://github.com/Keksuccino/mcef_resources/...`, а не с `https://void-rp.ru/launcher/mcef`. Значит `config/mcef.properties` либо не доставляется клиенту лаунчером, либо MCEF 2.2.0 читает его по другому пути.  
Нужно проверить: после запуска существует ли `C:\Users\MIRON\AppData\Local\VoidRpLauncher\game\config\mcef.properties` на клиенте.

---

#### P1.9 `"environment": "client"` в fabric.mod.json — Connector не грузит мод на сервере

**Статус: РЕШЕНО (2026-06-06)**

**Причина:** При пересборке jar из исходников (`voidrp_webgui/`) шаблон `src/main/resources/fabric.mod.json` содержал `"environment": "client"`. Это сигнализирует Connector, что мод только для клиента → DependencyResolver исключает его из серверных кандидатов → `WebGUI common init` не вызывается → мод отсутствует в NeoForge mod list → все клиенты дисконнектятся (`multiplayer.disconnect.incompatible`).

Диагноз по логу:
- Рабочая сессия 5: `Dependency resolution found 2 candidates to load` + `[webgui/]: WebGUI common init (S2C payloads, commands).`
- Сломанная сессия: `Dependency resolution found 1 candidates to load` + нет `WebGUI common init`
- `unzip -p webgui.jar fabric.mod.json` показал `"environment": "client"` в новом jar vs `"environment": "*"` в рабочем

**Решение:**
1. `voidrp_webgui/src/main/resources/fabric.mod.json` — изменено `"environment": "client"` → `"environment": "*"`
2. Рабочий jar восстановлен (`cp webgui.jar.disabled webgui.jar`) — у него `"environment": "*"`
3. Connector-кэш очищен, сервер перезапущен → `WebGUI common init` появился в логе

**Правило:** `"environment"` в `fabric.mod.json` нашего мода ВСЕГДА должно быть `"*"`. Значение `"client"` говорит Connector: «на сервере не грузить». Для NeoForge mod-list negotiation мод обязан быть зарегистрирован на обоих концах.

---

#### P1.8 Старый магазин открывается — Paper загружал старый jar из-за конфликта имён

**Статус: РЕШЕНО (2026-06-06)**

**Причина:** В папке `plugins/` одновременно лежали два jar с одним plugin name `VoidRpGameSync`:
- `VoidRpGameSync.jar` (Jun 6 01:18) — новый, с `WebGuiBridgeService`
- `voidrp-game-sync-paper-1.4.0-all.jar` (Jun 5 16:12) — старый, без `WebGuiBridgeService.class`

Paper логировал `Ambiguous plugin name 'VoidRpGameSync'` и загружал старый jar. Старый jar не знал о WebGUI → `PlayerMarketShopCommand` открывал Bukkit inventory GUI всегда.

Диагноз: в логах отсутствовало `Loaded webgui token config (ttl=...)` при включении плагина — признак того, что `WebGuiBridgeService` не инициализировался.

**Решение:**
```bash
rm plugins/voidrp-game-sync-paper-1.4.0-all.jar
rm plugins/.paper-remapped/voidrp-game-sync-paper-1.4.0-all.jar
mcrcon ... "plugman reload VoidRpGameSync"
```

После перезагрузки в логе появилось: `Loaded webgui token config (ttl=900s)`.

**Правило:** после `./gradlew shadowJar` → `cp build/libs/*.jar plugins/VoidRpGameSync.jar` старый build-output jar НЕ нужен в `plugins/` — удалять перед деплоем.

---

#### P1.10 Channel conflict — Paper-плагин и NeoForge мод регистрируют одни и те же каналы

**Статус: РЕШЕНО (2026-06-07)**

**Симптом в логе сервера:**
```
[Server thread/ERROR] [com.mohistmc.youer.Youer/]: Channel conflict: webgui:open_web, protocol: PLAY
[Server thread/ERROR] [com.mohistmc.youer.Youer/]: Channel conflict: webgui:set_main_menu, protocol: PLAY
```

**Причина:** Youer (Mohist/Connector) видит двойную регистрацию одного канала:
1. NeoForge мод `webgui` регистрирует `webgui:open_web` / `webgui:set_main_menu` через `RegisterPayloadHandlersEvent`
2. Paper-плагин `VoidRpGameSync` тоже регистрирует их через `plugin.getServer().getMessenger().registerOutgoingPluginChannel()`

**Решение:** `WebGuiBridgeService.java` — заворачиваем `registerOutgoingPluginChannel` в try-catch. NeoForge уже зарегистрировал канал; если Bukkit кидает или Youer конфликтует — перехватываем и игнорируем:
```java
private void registerChannel(String channel) {
    try {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, channel);
    } catch (Exception e) {
        // Channel already registered by NeoForge mod — not an error
    }
}
```

**Файл:** `voidrp_gamesync_plugin/.../service/WebGuiBridgeService.java`

---

#### P1.11 Версия мода: клиент `1.3.0mc1.21.1` vs сервер `1.3.0` — дисконнект при входе

**Статус: РЕШЕНО (2026-06-07)**

**Симптом в логе сервера:**
```
[main/WARN] [net.neoforged.neoforge.common.CommonHooks/WP]: The following mods have version differences that were not resolved:
mcef (version 2.2.0 -> MISSING)
webgui (version 1.3.0mc1.21.1 -> 1.3.0)
Things may not work well.
```

**Причина:** Дефолтный `displayTest = "MATCH_VERSION"` в NeoForge требует точного совпадения версий мода между клиентом и сервером:
- Сервер: NeoForge мод, `neoforge.mods.toml` → `version="${mod_version}"` = `"1.3.0"`
- Клиент (до обновления лаунчера): ещё старый Fabric jar с `fabric.mod.json` → `"version": "1.3.0+mc1.21.1"` (Connector кодирует `+` без знака → `1.3.0mc1.21.1`)
- NeoForge не принимает несовпадение → дисконнект

**Решение:** Добавить `displayTest = "IGNORE_ALL_VERSION"` в `[[mods]]` секцию `neoforge.mods.toml`:
```toml
[[mods]]
modId="${mod_id}"
version="${mod_version}"
displayName="${mod_name}"
...
displayTest="IGNORE_ALL_VERSION"
```
Теперь NeoForge не проверяет версию мода при handshake. Совместимость гарантируется каналами (они `.optional()`) а не версией.

**Файл:** `voidrp_webgui_neoforge/src/main/resources/META-INF/neoforge.mods.toml`

**После фикса:** пересобрать (`./gradlew jar`), задеплоить на сервер и в лаунчер-пак, обновить манифест (`generate_launcher_manifest.py`), перезапустить сервер.

---

#### P1.12 `/shop` не открывает WebGUI — sendPluginMessage не доходит до NeoForge клиента (2026-06-23)

**Статус: РЕШЕНО (2026-06-23)**

**Симптом:** Команда `/shop` выполняется без ошибок, но браузер у клиента не открывается. В логах ничего не было (логирования не существовало).

**Причина:** На Mohist Paper-плагин отправлял пакет через `player.sendPluginMessage()` (Bukkit plugin messaging). NeoForge 1.21.1 на клиенте регистрирует `webgui:open_web` как кастомный payload через `RegisterPayloadHandlersEvent`. Пакет, отправленный через Bukkit plugin messaging, не проходит через NeoForge's `PacketDistributor` и может не быть корректно роутан к зарегистрированному хендлеру клиента.

**Решение:** В `WebGuiBridgeService.java` добавлен reflection-bridge:
1. Пробуем вызвать `land.webgui.WebviewNetworking.openGui(serverPlayer, url)` через рефлексию — на Mohist NeoForge мод и Paper плагин на одном classpath
2. `WebviewNetworking.openGui()` использует `PacketDistributor.sendToPlayer()` — корректный NeoForge путь с токен-подписью
3. Если reflection не удался (мод не загружен) — fallback на `sendPluginMessage` как раньше

```java
private boolean tryViaForge(Player player, String url, int mode) {
    try {
        Object serverPlayer = player.getClass().getMethod("getHandle").invoke(player);
        Class<?> cls = Class.forName("land.webgui.WebviewNetworking");
        Class<?> spClass = Class.forName("net.minecraft.server.level.ServerPlayer");
        cls.getMethod(mode == MODE_GUI ? "openGui" : "openHud", spClass, String.class)
           .invoke(null, serverPlayer, url);
        return true;
    } catch (Exception e) {
        // fallback to sendPluginMessage
        return false;
    }
}
```

**Дополнительно:** Добавлено подробное логирование во все ключевые точки:
- Сервер (NeoForge): `WebviewNetworking.openGui/openHud/openGuiForEntity()` — INFO лог с именем игрока и URL
- Сервер (NeoForge): `withPlayerToken()` — WARN если секрет не настроен
- Клиент (NeoForge): `WebGUIClientHandlers.handleOpenPayload()` — INFO при получении пакета, WARN если MCEF не готов
- Плагин: `WebGuiBridgeService.openGui/sendWebPacket()` — INFO при каждой отправке

**Файлы:**
- `voidrp_webgui_neoforge/src/main/java/land/webgui/WebviewNetworking.java`
- `voidrp_webgui_neoforge/src/main/java/land/webgui/WebGUIClientHandlers.java`
- `voidrp_gamesync_plugin/src/main/java/ru/voidrp/gamesync/service/WebGuiBridgeService.java`

**После фикса:** пересобрать оба мода, задеплоить NeoForge jar на сервер и в лаунчер-пак, горячо перезагрузить плагин (`plugman reload VoidRpGameSync`), обновить манифест. NeoForge jar требует рестарт сервера.

---

#### P1.4 UUID vs nickname в бэкенде

**Проблема:** WebGUI-токен содержит `playerUUID`. Все наши сервисы (PlayerMarketService, NationSyncService и т.д.) работают с `minecraft_nickname` / `minecraft_nickname_normalized`. В `player_accounts` есть поле `minecraft_uuid`? Нужно проверить.

**Решение:** `webgui_auth.py` делает JOIN при верификации и сразу возвращает `player_name`:
```python
async def get_webgui_player(
    webgui_token: str = Query(...),
    session: Session = Depends(get_session),
) -> PlayerAccount:
    uuid = _verify_token(webgui_token)  # -> UUID
    account = session.execute(
        select(PlayerAccount).where(PlayerAccount.minecraft_uuid == str(uuid))
    ).scalar_one_or_none()
    if not account:
        raise HTTPException(404, "player not found")
    return account
```
Если `minecraft_uuid` нет в таблице — добавить через миграцию + начать заполнять при sync.

**Действие:** Проверить схему `player_accounts` до начала разработки game-ui роутов.

---

### P2 (важные — влияют на UX)

---

#### P2.1 Vault-транзакции через web — latency

**Проблема:** pending_web_action + polling раз в секунду = до 1 сек задержки. Для отмены ордера или доната это заметно хуже, чем текущее мгновенное выполнение.

**Решение:** Не использовать pending_web_action для операций, где уже есть plugin-команды. Вместо этого кнопка в WebGUI → `{"channel": "run_command", "text": "/pm cancel <id>"}` → плагин выполняет мгновенно и пишет в чат. Polling нужен только для по-настоящему браузерных операций (buy order через форму без команды).

**Итог:** pending_web_action используется только для новых операций без аналога в командах (например, buy order прямо из order book через форму).

---

#### P2.2 Vue vs `@webgui/react`

**Проблема:** `@webgui/react` — React-только. Наш фронтенд Vue 3. React хуки в Vue не работают.

**Решение:** Написать Vue-composables поверх нативного `window.webgui` API (которое inject-ится модом в каждую страницу):

```js
// src/composables/useWebGui.js
import { ref, onMounted, onUnmounted, computed } from 'vue'

export function useWebGuiClient() {
  const client = ref(window.webgui?.client ?? null)
  const handler = (e) => { client.value = e.detail }
  onMounted(() => window.addEventListener('webgui:client', handler))
  onUnmounted(() => window.removeEventListener('webgui:client', handler))
  return client  // { playerUuid, username, dimension, pos, server }
}

export function useWebGuiToken() {
  return new URLSearchParams(window.location.search).get('webgui_token') ?? ''
}

export function isInMod() {
  return typeof window !== 'undefined' && typeof window.webgui !== 'undefined'
}

export function postToGame(payload) {
  // window.cefQuery — CEF message router bridge
  return new Promise((resolve, reject) => {
    if (!window.cefQuery) return reject('not in mod')
    window.cefQuery({
      request: typeof payload === 'string' ? payload : JSON.stringify(payload),
      onSuccess: resolve,
      onFailure: (code, msg) => reject(msg),
    })
  })
}
```

**Действие:** Создать `VOIDRP-SITE/src/composables/useWebGui.js` до разработки первой game-ui страницы.

---

#### P2.3 webgui_token в POST-запросах

**Проблема:** Токен приходит как URL query param — GET запросы тривиальны. Для POST-запросов (создать ордер, отменить) нужно явно передавать.

**Решение:** `useWebGuiToken()` сохраняет токен в памяти при монтировании страницы. `apiRequest()` в `apiBase.js` получает новый опциональный параметр `webguiToken` и подставляет его как `Authorization: WebGUI <token>`. FastAPI dependency умеет читать и из query-param, и из этого header.

```python
def get_webgui_player(
    webgui_token: str | None = Query(None),
    authorization: str | None = Header(None),
    ...
):
    raw = webgui_token or (
        authorization.removeprefix("WebGUI ") if authorization?.startswith("WebGUI ") else None
    )
```

---

#### P2.4 CORS для MCEF браузера

**Проблема:** MCEF делает запросы с origin = `https://void-rp.ru` (если страница там). FastAPI уже настроен на CORS, но нужно убедиться что `https://void-rp.ru` в `allow_origins`.

**Решение:** Проверить `apps/api/app/main.py` CORS settings. Добавить `https://void-rp.ru` если не добавлен. Избегать `null` origin — всегда использовать https URL для game-ui страниц, не local file.

---

#### P2.5 Игрок без WebGUI — команда ничего не открывает

**Проблема:** Если `/pm` диспатчит `webgui gui PlayerName url`, но у игрока не установлен WebGUI — пакет уходит в никуда. Игрок ничего не видит, думает что команда не работает.

**Решение A (простое):** WebGUI обязателен в модпаке (`REQUIRED_LOCKED_MODS`). Лаунчер не позволяет войти без него.

**Решение B (дополнительно):** При отправке WebGUI-пакета плагин пишет в чат `§8[Рынок] Открываем интерфейс...`. Если GUI не открылся — игрок хотя бы видит попытку и понимает что нужен мод. Сообщение не информирует об ошибке явно, но даёт контекст.

---

### P3 (архитектурные риски — важно при масштабировании)

---

#### P3.1 Pending web action: несобранные action-ы при дисконнекте

**Проблема:** Игрок создал pending_web_action (например, buy order на 100,000 монет), деньги ещё не списаны, и вышел до того как плагин успел обработать. Action висит в БД бесконечно. При следующем входе плагин может случайно его выполнить спустя час.

**Решение:**
- Поле `expires_at` (3–5 минут от создания) на каждом pending_web_action
- Плагин проверяет `expires_at` перед выполнением — просроченные игнорирует
- Cron в бэкенде раз в час удаляет просроченные записи

---

#### P3.2 Connector + WebGUI: несовместимость версий

**Проблема:** Connector и WebGUI независимо обновляются. Обновление одного может сломать другое.

**Решение:**
- Держать WebGUI под своим контролем (репозиторий у нас) — не обновлять автоматически
- Перед обновлением Connector — тестировать на dev-сервере
- Зафиксировать версию Connector в модпаке

---

#### P3.3 Несколько открытых GUI одновременно

**Проблема:** WebGUI поддерживает один GUI-экран и один HUD. Если игрок открывает `/pm` и тут же `/nmarket` — второй перезаписывает первый без предупреждения.

**Решение:** Встроенная навигация через `open_gui` channel из страницы (этап 10.1) — переходы между разделами не создают новый GUI-контекст, а меняют URL в существующем Chromium. Для этого нужен Vue Router с client-side navigation.

---

## Расширяемость

### E1 Единый SPA вместо отдельных URL

**Сейчас (в плане):** каждый раздел — отдельный URL (`/game-ui/market`, `/game-ui/treasury`). При переходе между ними Chromium перегружает страницу — повторная загрузка бандла, потеря стейта.

**Лучше:** Единый SPA на `/game-ui` с Vue Router client-side навигацией.
- Плагин всегда открывает `/game-ui?section=market` (или `/game-ui/market` — SPA router перехватывает)
- Chromium загружает JS/CSS один раз
- Токен остаётся в памяти при навигации
- Добавление нового раздела = один новый Vue view + route, без изменений в плагине

**Действие:** Настроить Vue Router в режиме `createWebHashHistory()` (hash-based, без серверного SSR) для game-ui части сайта.

---

### E2 WebGuiBridgeService как Bukkit-сервис

**Проблема:** Battle pass и daily quests — отдельные плагины. Им тоже нужно открывать WebGUI.

**Решение:** Зарегистрировать `WebGuiBridgeService` в Bukkit Services Manager из VoidRpGameSync:
```java
// В VoidRpGameSyncPlugin.onEnable():
getServer().getServicesManager().register(
    WebGuiBridgeService.class, webGuiBridgeService, this, ServicePriority.Normal);

// В voidrp_battlepass:
WebGuiBridgeService bridge = Bukkit.getServer().getServicesManager()
    .getRegistration(WebGuiBridgeService.class)?.getProvider();
if (bridge != null) bridge.openGui(player, "https://void-rp.ru/game-ui#battlepass");
```
Если VoidRpGameSync не загружен — graceful degradation (открываем старый Bukkit GUI).

---

### E3 Добавление нового game-ui раздела — чеклист

Когда нужно добавить новый раздел (например, `/game-ui#lotteries`):
1. **Фронтенд:** Новый Vue view + route в `src/router/game-ui.js`
2. **Кнопка в Main Menu:** Добавить кнопку в `GameUiMenuView.vue` с `postToGame({channel:"open_gui", url:"...#lotteries"})`
3. **Бэкенд:** Новый FastAPI router с `get_webgui_player` dependency — подключить в `main.py`
4. **Плагин (если нужна команда):** Одна строка в `WebGuiBridgeService` + диспатч в нужном месте
5. **Плагин (если нужны Vault-операции):** Добавить тип в `pending_web_action`, обработать в поллере

---

### E4 Токен для обычного сайта (VOIDRP-SITE)

В будущем можно использовать webgui_token для авторизации игрока на полноценном сайте без пароля: игрок в лаунчере → сайт открывается с токеном → вход автоматический. Для этого уже готова вся инфраструктура токен-верификации.

---

### E5 Реалтайм-обновления в HUD через SSE

**Сейчас:** HUD поллит бэкенд каждые 10 секунд.

**В будущем:** Server-Sent Events (SSE) — бэкенд пушит обновления баланса, квестов, уведомлений. MCEF поддерживает SSE (стандартный браузерный EventSource). Потребует `asyncio` в FastAPI роуте. Переход прозрачен для фронтенда — меняем polling на EventSource без других изменений.
