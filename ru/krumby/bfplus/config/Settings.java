package ru.krumby.bfplus.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Settings extends Config
{

    @Ignore
    public static final Settings IMP = new Settings();

    @Comment(
        {
        "ВotFilterPlus by https://dsc.gg/bfplus"
        })
    @Final
    public final String ISSUES = "https://dsc.gg/bfplus";
    @Final
    public final String HELP = "https://dsc.gg/bfplus";
    @Final
    public String BOT_FILTER_VERSION = "2.2";

    @Create
    public MESSAGES MESSAGES;
    @Create
    public DIMENSIONS DIMENSIONS;
    @Create
    public GEO_IP GEO_IP;
    @Create
    public PING_CHECK PING_CHECK;
    @Create
    public SERVER_PING_CHECK SERVER_PING_CHECK;
    @Create
    public PROTECTION PROTECTION;
    @Create
    public SQL SQL;
    @Create
    public static CAPTCHA CAPTCHA;
    @Comment(
        {
        "Сколько игроков/ботов должно зайти за 1 минуту, чтобы включилась защита",
        "Рекомендуемые параметры когда нет рекламы: ",
        "До 150 онлайна - 25, до 250 - 30, до 350 - 35, до 550 - 40,45, выше - подстраивать под себя ",
        "Во время рекламы или когда токо, токо поставили защиту рекомендуется повышать эти значения"
        })
    public int PROTECTION_THRESHOLD = 14;
    @Comment("Как долго активна автоматическая защита? В миллисекундах. 1 сек = 1000")
    public int PROTECTION_TIME = 60000;
    @Comment("Проверять ли на бота при заходе на сервер во время бот атаки, не зависимо проходил ли проверку или нет")
    public boolean FORCE_CHECK_ON_ATTACK = true;
    @Comment("Показывать ли онлайн с фильтра")
    public boolean SHOW_ONLINE = true;
    @Comment("Сколько времени есть у игрока чтобы пройти защиту. В миллисекундах. 1 сек = 1000")
    public int TIME_OUT = 22700;
    @Comment("Включить ли фикс от 'Team 'xxx' already exist in this scoreboard'")
    public boolean FIX_SCOREBOARD_TEAMS = true;
    @Comment("Записывать ли IP адреса игроков/ботов которые провалили проверку в файл?")
    public boolean SAVE_FAILED_IPS_TO_FILE = true;

    public void reload(File file)
    {
        load( file );
        save( file );
    }

    @Comment("Не используйте '\\n', используйте %nl%")
    public static class MESSAGES
    {

        public String PREFIX = "&b&lBot&d&lFilter&b&l+ ";
        public String CHECKING = "%prefix%&7>> &aИдет проверка...";
        public String CHECKING_CAPTCHA = "%prefix%&7>> &eВведите код с карты в чат &7(Для открытия чата нажмите на русскую Е)";
        public String CHECKING_CAPTCHA_WRONG = "%prefix%&7>> &cВы ввели капчу неверно, у вас &a%s &c%s";
        public String SUCCESSFULLY = "%prefix%&7>> &aПроверка успешно пройдена.";
        public String KICK_MANY_CHECKS = "%prefix%%nl%%nl%&cВы слишком много раз заходили за последние 10 минут%nl%&6Попробуйте зайти через 15 минут.";
        public String KICK_NOT_PLAYER = "%prefix%%nl%&cВы не смогли пройти проверку%nl%&7&oПожалуйста, повторите попытку.";
        public String KICK_COUNTRY = "%prefix%%nl%%nl%&cВаша страна запрещена на серверве (Попробуйте зайти через несколько минут)";
        public String KICK_BIG_PING = "%prefix%%nl%%nl%&cУ вас слишком высокий пинг.";
        @Comment(
            {
            "Title%nl%Subtitle", "Оставьте пустым, чтобы отключить( прм: CHECKING_TITLE = \"\" )",
            "Отключение титлов может немного улучшить производительность"
            })
        public String CHECKING_TITLE = "";
        public String CHECKING_TITLE_SUS = "&rПроверка пройдена%nl%&aПриятной игры";
        public String CHECKING_TITLE_CAPTCHA = "";
    }

    @Comment("Включить или отключить GeoIp")
    public static class GEO_IP
    {

        @Comment(
            {
            "Когда работает проверка",
            "0 - Всегда",
            "1 - Только во время бот атаки",
            "2 - Отключить"
            })
        public int MODE = 1;
        @Comment(
            {
            "Как именно работает GeoIp",
            "0 - White list(Зайти могут только те страны, которые есть в списке)",
            "1 - Black list(Зайти могут только те страны, которых нет в списке)"
            })
        public int TYPE = 0;
        @Comment(
            {
            "Откуда качать GEOIP",
            "Меняйте ссылку если по какой-то причине не качается по этой",
            "Файл должен заканчиваться на .mmdb или быть запакован в .tar.gz"
            })
        public String NEW_GEOIP_DOWNLOAD_URL = "https://download.maxmind.com/app/geoip_download?edition_id=GeoLite2-Country&license_key=%license_key%&suffix=tar.gz";
        @Comment(
            {
            "Если ключ перестанет работать, то для того чтобы получить новый необходимо зарегестироваться на https://www.maxmind.com/",
            "и сгенерировать новый ключ на странице https://www.maxmind.com/en/accounts/current/license-key"
            })
        public String MAXMIND_LICENSE_KEY = "P5g0fVdAQIq8yQau";
        @Comment("Разрешённые странны")
        public List<String> ALLOWED_COUNTRIES = Arrays.asList( "RU", "UA", "BY", "KZ", "EE", "MD", "KG", "AZ", "LT", "LV", "GE", "PL" );
    }

    @Comment("Включить или отключить проверку на высокий пинг")
    public static class PING_CHECK
    {

        @Comment(
            {
            "Когда работает проверка",
            "0 - Всегда",
            "1 - Только во время бот атаки",
            "2 - Отключить"
            })
        public int MODE = 1;
        @Comment("Максимальный допустимый пинг")
        public int MAX_PING = 350;
    }

    @Comment("Включить или отключить проверку на прямое подключение")
    public static class SERVER_PING_CHECK
    {

        @Comment(
            {
            "Когда работает проверка",
            "0 - Всегда",
            "1 - Только во время бот атаки",
            "2 - Отключить",
            "По умолчанию отключено, по скольку работает не очень стабильно, во время сильных атак"
            })
        public int MODE = 2;
        @Comment("В течении какого времени можно заходить на сервер после получения мотд сервера")
        public int CACHE_TIME = 12;
        public List<String> KICK_MESSAGE = new ArrayList()
        {
            {
                add("&b&lBot&d&lFilter&b&l+ &7>> &aДобавьте сервер в список серверов и нажмите на Обновить/Refresh");

            }
        };
    }

    @Comment(
        {
        "Настройка как именно будет работать защита",
        "0 - Только проверка с помошью капчи",
        "1 - Проверка на падение + капча",
        "2 - Проверка на падение, если провалилась, то капча"
        })
    public static class PROTECTION
    {

        @Comment("Режим работы пока нет атаки")
        public int NORMAL = 1;
        @Comment("Режим работы во время атаки")
        public int ON_ATTACK = 1;
        @Comment(
            {
            "Включить ли постоянную проверку игроков при заходе?",
            "Включая эту функци, не забудьте увелечить лимиты у protection-threshold"
            })
        public boolean ALWAYS_CHECK = false;

        @Comment(
            {
            "Проверять ли игроков у которых ип 127.0.0.1?", "Может быть полезным при использовании Geyser",
            "0 - проверять", "1 - отключить проверку", "2 - проверять при каждом заходе"
            })
        public int CHECK_LOCALHOST = 0;

        @Comment("Отключить ли проверку для клиентов с Geyser-standalone? Тип авторищации должен быть floodgate.")
        public boolean SKIP_GEYSER = false;
        /*
        @Comment(
                {
                    "Когда работают дополнительные проверки по протоколу",
                    "    (Пакеты на которые клиент должен всегда отвечать)",
                    "0 - Всегда",
                    "1 - Только во время бот атаки",
                    "2 - Отключить"
                })
        public int ADDITIONAL_CHECKS = 1;
         */
    }

    @Comment("Настройка датабазы")
    public static class SQL
    {

        @Comment("Тип датабазы. sqlite или mysql")
        public String STORAGE_TYPE = "sqlite";
        @Comment("Через сколько дней удалять игроков из датабазы, которые прошли проверку и больше не заходили. 0 или меньше чтобы отключить")
        public int PURGE_TIME = 14;
        @Comment("Настройки для mysql")
        public String HOSTNAME = "127.0.0.1";
        public int PORT = 3306;
        public String USER = "user";
        public String PASSWORD = "password";
        public String DATABASE = "database";
        @Comment("Интервал в милисекундах, как часто синхронизировать базу данных если используется мультибанжа")
        public int SYNC_INTERVAL = -1;
    }

    @Comment("Настройка виртуального мира")
    public static class DIMENSIONS
    {
        @Comment(
            {
            "Какой мир использовать",
            "0 - Обычный мир",
            "1 - Ад",
            "2 - Энд"
            })
        public int TYPE = 0;
    }
    @Comment("Настройки капчи")
    public static class CAPTCHA
    {
        @Comment("Уровень поворота символов в капче")
        public static int ROTATE = 1;
        @Comment("Уровень волны на капче")
        public static int RIPPLE = 0;
        @Comment("Член")
        public static boolean outlineEnabled = true;
        @Comment("Шрифт который будет использоватся")
        public static String FONT_NAME = "Neutral Face";
        @Comment("Размер шрифта")
        public static int FONT_SIZE = 50;
        @Comment("Размер символов (Максимум 128x128)\n# По вертикали")
        public static int SIZEW = 120;
        @Comment("По горизонтали")
        public static int SIZEH = 80;
        @Comment("Будут ли символы разного градиента?")
        public static boolean RAINBOW = true;
        @Comment("Будет ли картинка на заднем фоне?")
        public static boolean BG = true;
        @Comment("Название заднего фона капчи")
        public static String BG_NAME = "background.png";
        @Comment("\n# Доп настройки\n#\n# Слот в котором будет находится карта (Левая - 45 Правая - 36)\n# https://wiki.vg/File:Inventory-slots.png\n# !!! Если ваш сервер работает на версии 1.8 то ставьте 36 слот !!!")
        public static int SLOT = 45;
        @Comment("Количество попыток на решение капчи (Меньше - лучше)")
        public static int ATTEMPS = 2;
    }
}
