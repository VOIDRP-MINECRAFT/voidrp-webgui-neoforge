# 🖥️ VoidRP WebGUI (NeoForge Server)

> NeoForge 1.21.1 серверный мод — управляет встроенным браузером клиентов, отправляет URL и события.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen?logo=minecraft)
![NeoForge](https://img.shields.io/badge/NeoForge-21.1.x-orange)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![MCEF](https://img.shields.io/badge/MCEF-Chromium_embedded-blue)
![License](https://img.shields.io/badge/license-proprietary-red)

---

## 🗺️ Место в экосистеме

```
  Minecraft Server (NeoForge)
        │
  voidrp-webgui-neoforge  ◄── /webgui open <url> <player>
        │ S2C пакет (URL + режим)
        ▼
  Minecraft Client + voidrp-webgui
        │ встроенный Chromium (MCEF)
        ▼
  void-rp.ru (Vue 3) ←→ minecraft-backend (API)
```

Серверная часть отвечает за **отправку команд клиенту** — какую страницу открыть, в каком режиме (fullscreen/HUD), и за приём событий от страниц.

---

## ✨ Возможности

- **Открытие URL** — отправляет клиенту команду открыть конкретную страницу
- **Режимы отображения** — fullscreen (поверх игры) или HUD-overlay (прозрачный)
- **Серверные события → клиент** — `WebviewApi.emitToPage()` для обновления UI в реальном времени
- **Клиентские события → сервер** — обработка действий пользователя на странице
- **Контроль навигации** — сервер определяет разрешённые URL

---

## 📋 Требования

| Компонент | Версия |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.x |
| Java | 21 |

Клиентам необходим [voidrp-webgui](https://github.com/VOIDRP-MINECRAFT/voidrp-webgui) + MCEF.

---

## 🚀 Сборка и деплой

```bash
cd voidrp_webgui_neoforge
./gradlew build
# → build/libs/webgui-neoforge-*.jar

cp build/libs/webgui-neoforge-*.jar /path/to/minecraft_server/mods/
```

---

## 🛠️ API для других модов и плагинов

```java
// Открыть страницу игроку
WebviewApi.openUrl(player, "https://void-rp.ru/shop", WebviewMode.FULLSCREEN);

// Отправить событие в открытую страницу
WebviewApi.emitToPage(player, "event_name", "{\"key\":\"value\"}");

// Закрыть webview
WebviewApi.close(player);
```

---

## 🔗 Связанные репозитории

| Репо | Связь |
|---|---|
| [voidrp-webgui](https://github.com/VOIDRP-MINECRAFT/voidrp-webgui) | Клиентская часть (MCEF браузер) |
| [voidrp-site](https://github.com/VOIDRP-MINECRAFT/voidrp-site) | Сайт, который открывается в браузере |

---

<div align="center">
<a href="https://void-rp.ru">🌐 Сайт</a> ·
<a href="https://github.com/VOIDRP-MINECRAFT">🏠 Организация</a>
</div>
