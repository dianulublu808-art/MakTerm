package com.rk.i18n

import java.util.Locale

/**
 * Lightweight runtime translation for UI labels that are written inline in Compose code.
 * Resource-backed strings are translated through values-xx/strings.xml; this table covers
 * the remaining literals so the whole interface can be localized without touching layout code.
 */
object Localization {

    private val russian: Map<String, String> = mapOf(
        // Terminal / sessions
        "New" to "Новый",
        "New session" to "Новая сессия",
        "Sessions" to "Сессии",
        "Customize" to "Настроить",
        "Liquid glass terminal" to "Терминал с жидким стеклом",
        "Run script?" to "Запустить скрипт?",
        "Session type" to "Тип сессии",
        "Run" to "Запустить",
        "Terminal" to "Терминал",

        // Dialogs
        "New Custom Session" to "Новая сессия",
        "Session name" to "Имя сессии",
        "Shell script path" to "Путь к shell-скрипту",
        "Save" to "Сохранить",
        "Cancel" to "Отмена",
        "Delete" to "Удалить",
        "Deleting…" to "Удаление…",
        "Reinstall root filesystem" to "Переустановить систему",
        "Deletes the installed Linux system and downloads it again" to "Удаляет установленный Linux и загружает его заново",
        "All files inside the Linux system will be deleted. This cannot be undone." to "Все файлы внутри Linux будут удалены. Отменить это нельзя.",
        "Apply" to "Применить",
        "Preview" to "Предпросмотр",
        "HEX" to "HEX",
        "Hue" to "Оттенок",
        "Saturation" to "Насыщенность",
        "Brightness" to "Яркость",
        "Liquid glass" to "Жидкое стекло",

        // Setup
        "Root Access Detected" to "Обнаружен root-доступ",
        "Root access was found. Run the terminal with chroot (faster, requires root for every session) or proot (no root required, slightly slower)? You can change this later in Settings." to
            "Обнаружен root-доступ. Запускать терминал через chroot (быстрее, root нужен для каждой сессии) или proot (без root, немного медленнее)? Это можно изменить позже в настройках.",
        "Chroot" to "Chroot",
        "Proot" to "Proot",
        "Checking root access..." to "Проверка root-доступа…",
        "Setup Failed:" to "Ошибка установки:",
        "Downloading root filesystem..." to "Загрузка корневой файловой системы…",
        "Extracting root filesystem..." to "Распаковка корневой файловой системы…",
        "Not enough free space for the root filesystem" to "Недостаточно свободного места для корневой файловой системы",
        "The root filesystem could not be extracted" to "Не удалось распаковать корневую файловую систему",
        "Choose distribution" to "Выбор дистрибутива",
        "Alpine Linux" to "Alpine Linux",
        "Arch Linux ARM" to "Arch Linux ARM",
        "Small and fast (bundled)" to "Небольшой и быстрый (в комплекте)",
        "Full Arch Linux, downloaded once (~830 MB)" to "Полноценный Arch Linux, загружается один раз (~830 МБ)",
        "Install" to "Установить",
        "Installing" to "Установка",
        "Network Error" to "Ошибка сети",

        // Settings
        "Execution Mode" to "Режим выполнения",
        "Login Shell" to "Командная оболочка",
        "Distro default" to "По умолчанию в дистрибутиве",
        "ash on Alpine, bash on Arch" to "ash в Alpine, bash в Arch",
        "/bin/bash (runs apk add bash if missing)" to "/bin/bash (при отсутствии выполнит apk add bash)",
        "/bin/sh" to "/bin/sh",
        "/bin/ash" to "/bin/ash",
        "ash" to "ash",
        "bash" to "bash",
        "sh" to "sh",
        "Custom Sessions" to "Пользовательские сессии",
        "Add Custom Session" to "Добавить сессию",
        "Liquid glass, colours, wallpaper, terminal background and keyboard" to
            "Жидкое стекло, цвета, обои, фон терминала и клавиатура",
        "About" to "О приложении",
        "Material 3 liquid glass terminal" to "Терминал Material 3 liquid glass",

        // Built-in file server / admin panel
        "Server" to "Сервер",
        "Start server" to "Запустить сервер",
        "Stop server" to "Остановить сервер",
        "Share files from your phone over Wi-Fi" to "Раздавайте файлы с телефона по Wi-Fi",
        "Not connected to Wi-Fi" to "Нет подключения к Wi-Fi",
        "Open in browser" to "Открыть в браузере",
        "Copy link" to "Скопировать ссылку",
        "Link copied" to "Ссылка скопирована",
        "Requests served" to "Обслужено запросов",
        "Port" to "Порт",
        "Stop the server to change this" to "Остановите сервер, чтобы изменить",
        "Shared folder" to "Общая папка",
        "Require password" to "Требовать пароль",
        "Protect the file server with a password" to "Защитить файловый сервер паролем",
        "Password" to "Пароль",
        "No password set" to "Пароль не задан",
        "Login" to "Логин",
        "Use a port from 1024 to 65535" to "Порт от 1024 до 65535",
        "Shared folder path" to "Путь к папке",
        "Absolute path, for example /sdcard" to "Абсолютный путь, например /sdcard",
        "Enter a folder path" to "Введите путь к папке",
        "Folder not found" to "Папка не найдена",
        "Folder is not readable" to "Нет доступа к папке",
        "Login: admin. Leave empty to disable protection." to "Логин: admin. Оставьте пустым, чтобы отключить защиту.",
        "Server error" to "Ошибка сервера",

        // System dashboard / hosting
        "System status" to "Состояние системы",
        "Disk" to "Диск",
        "Battery" to "Батарея",
        "Charging" to "Зарядка",
        "Uptime" to "Время работы",
        "Hosting" to "Хостинг",
        "Add process" to "Добавить процесс",
        "Run a Telegram bot, a script or any background command" to
            "Запустить Telegram-бота, скрипт или любую фоновую команду",
        "Running" to "Работает",
        "Stopped" to "Остановлен",
        "Auto-restart" to "Автозапуск",
        "No processes yet" to "Пока нет процессов",
        "Add a command above to host it in the background" to
            "Добавьте команду выше, чтобы держать её в фоне",
        "Linux tools" to "Инструменты Linux",
        "Install into the Linux system" to "Установить в систему Linux",
        "Backup Linux system" to "Резервная копия Linux",
        "Create a tar.gz archive in /sdcard/Download" to
            "Создать архив tar.gz в /sdcard/Download",
        "New process" to "Новый процесс",
        "Edit process" to "Изменить процесс",
        "Name" to "Название",
        "Command" to "Команда",
        "Working directory" to "Рабочая папка",
        "Port (optional)" to "Порт (необязательно)",
        "Run in Linux" to "Запускать в Linux",
        "Uncheck to run in the Android shell" to
            "Снимите галочку, чтобы запускать в оболочке Android",
        "Auto-restart on crash" to "Перезапуск при сбое",
        "Restart up to 5 times after an unexpected exit" to
            "До 5 перезапусков после неожиданного завершения",
        "Enter a name" to "Введите название",
        "Enter a command" to "Введите команду",
        "Logs" to "Логи",
        "No logs yet" to "Логов пока нет",
        "Clear" to "Очистить",
        "Close" to "Закрыть",
        "Edit" to "Изменить",
        "Delete process?" to "Удалить процесс?",
        "The process will be stopped and removed from the list." to
            "Процесс будет остановлен и удалён из списка.",
        "Task" to "Задача",
        "Starting…" to "Запуск…",

        // Updates
        "Updates" to "Обновления",
        "Check for updates" to "Проверить обновления",
        "Checking for updates…" to "Проверка обновлений…",
        "Downloading update…" to "Загрузка обновления…",
        "Latest version installed" to "Установлена последняя версия",
        "Update available:" to "Доступно обновление:",
        "Current version:" to "Текущая версия:",
        "GitHub repository" to "Репозиторий GitHub",
        "Could not check for updates" to "Не удалось проверить обновления",
        "Update failed" to "Не удалось обновить",
        "You are up to date" to "Обновления не нужны",
        "Retry" to "Повторить",
        "Downloading…" to "Загрузка…",
        "Download and install" to "Скачать и установить",
        "GitHub" to "GitHub",
        "Later" to "Позже",
        "This release has no APK attached. Open the release page to download it manually." to
            "В этом релизе нет APK. Откройте страницу релиза и скачайте файл вручную.",
        "Rootless Linux" to "Linux без root",
        "Alpine, Android and custom sessions via proot or chroot" to
            "Alpine, Arch, Android и свои сессии через proot или chroot",
        "Liquid glass · Material 3 · rootless Linux" to "Liquid glass · Material 3 · Linux без root",
        "Language" to "Язык",
        "System default" to "Как в системе",
        "English" to "Английский",
        "Russian" to "Русский",
        "Distribution" to "Дистрибутив",
        "Installed distribution" to "Установленный дистрибутив",
        "Terminal behaviour" to "Поведение терминала",
        "Keep screen on" to "Не выключать экран",
        "Keep screen on while the terminal is open" to "Держать экран включённым при открытом терминале",
        "Reduce motion" to "Меньше анимации",
        "Disable the animated liquid background" to "Отключить анимированный жидкий фон",
        "Reset appearance" to "Сбросить оформление",
        "Reset appearance?" to "Сбросить оформление?",
        "Restore the default liquid glass look and terminal colours" to
            "Вернуть стандартный вид liquid glass и цвета терминала",
        "Your wallpaper and font files are kept. Every appearance option returns to its default value." to
            "Обои и файлы шрифтов сохранятся. Все параметры оформления вернутся к значениям по умолчанию.",
        "Reset" to "Сбросить",

        // Customization
        "Glass" to "Стекло",
        "Interface" to "Интерфейс",
        "App background" to "Фон приложения",
        "App background colour" to "Цвет фона приложения",
        "Accent colour" to "Акцентный цвет",
        "Custom accent colour" to "Свой акцентный цвет",
        "Override the palette with your own colour" to "Заменить палитру своим цветом",
        "Used by buttons, highlights and the liquid background" to
            "Используется для кнопок, выделений и жидкого фона",
        "Background style" to "Стиль фона",
        "Background colour" to "Цвет фона",
        "Solid colour behind every screen" to "Сплошной цвет на всех экранах",
        "Wallpaper" to "Обои",
        "Remove wallpaper" to "Удалить обои",
        "Wallpaper blur" to "Размытие обоев",
        "Wallpaper dim" to "Затемнение обоев",
        "Wallpaper alpha" to "Прозрачность обоев",
        "Liquid speed" to "Скорость жидкости",
        "Liquid intensity" to "Интенсивность жидкости",
        "Translucent, rounded surfaces across the app" to
            "Полупрозрачные скруглённые панели по всему приложению",
        "Glass opacity" to "Прозрачность стекла",
        "Corner radius" to "Радиус углов",
        "Rim brightness" to "Яркость канта",
        "Terminal background" to "Фон терминала",
        "Terminal padding" to "Отступы терминала",
        "Text colour" to "Цвет текста",
        "Terminal text" to "Текст терминала",
        "Terminal text colour" to "Цвет текста терминала",
        "Custom text colour" to "Свой цвет текста",
        "Foreground used by the terminal" to "Цвет текста в терминале",
        "Cursor colour" to "Цвет курсора",
        "Blinking cursor and selection handles" to "Мигающий курсор и маркеры выделения",
        "Remove image" to "Удалить изображение",
        "Dim" to "Затемнение",
        "Terminal colour" to "Цвет терминала",
        "Solid colour behind the terminal" to "Сплошной цвет под терминалом",
        "Opacity" to "Непрозрачность",
        "Gradient dim" to "Затемнение градиента",
        "Liquid glass studio" to "Студия liquid glass",
        "Make MakTerm truly yours — colours, glass, wallpaper and terminal." to
            "Сделайте MakTerm своим — цвета, стекло, обои и терминал.",
        "ANSI palette" to "Палитра ANSI",
        "Layout JSON" to "JSON раскладки",
        "delete" to "удалить",

        // Misc
        "Please wait..." to "Подождите…",
        "Could not load image" to "Не удалось загрузить изображение",
        "Failed to load font" to "Не удалось загрузить шрифт",
        "OK" to "ОК",
        "Notification for Terminal Service" to "Уведомление службы терминала",
    )

    fun languageTag(): String = Locale.getDefault().language

    fun isRussian(): Boolean = languageTag() == "ru"

    /** Returns the localized variant of [text] for the current locale. */
    fun translate(text: String): String = if (isRussian()) russian[text] ?: text else text
}

/** Short helper used across Compose screens. */
fun t(text: String): String = Localization.translate(text)
