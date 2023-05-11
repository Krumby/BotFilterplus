package ru.krumby.bfplus.captcha;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.logging.Level;
import lombok.experimental.UtilityClass;
import net.md_5.bungee.BungeeCord;
import ru.krumby.bfplus.captcha.generator.BFPPainter;
import ru.krumby.bfplus.captcha.generator.map.CraftMapCanvas;
import ru.krumby.bfplus.captcha.generator.map.MapPalette;
import ru.krumby.bfplus.caching.CachedCaptcha;
import ru.krumby.bfplus.caching.PacketUtils;
import ru.krumby.bfplus.config.Settings;
import ru.krumby.bfplus.packets.MapDataPacket;


@UtilityClass
public class BFPGenerator {
    Random rnd = new Random();
    public static String answer;
    public void generateImages() {
        Font[] fonts = new Font[]
                {
                        new Font(Settings.CAPTCHA.FONT_NAME, Font.PLAIN, Settings.CAPTCHA.FONT_SIZE),
                };

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        BFPPainter painter = new BFPPainter();
        MapPalette.prepareColors();
        for (int i = 100; i <= 999; i++) {
            executor.execute(() ->
            {
                answer = randomAnswer();
                BufferedImage image = painter.draw(fonts[rnd.nextInt(fonts.length)], randomNotWhiteColor(), answer);
                final CraftMapCanvas map = new CraftMapCanvas();
                map.drawImage(0, 0, image);
                MapDataPacket packet = new MapDataPacket(0, (byte) 0, map.getMapData());
                PacketUtils.captchas.createCaptchaPacket(packet, answer);
            });
        }

        long start = System.currentTimeMillis();
        ThreadPoolExecutor ex = (ThreadPoolExecutor) executor;
        while (ex.getActiveCount() != 0) {
            BungeeCord.getInstance().getLogger().log(Level.INFO, "§b§lBot§d§lFilter§b§l+§f §8|| §6[{0}/900] Капча: ( " + answer + " )", 900 - ex.getQueue().size() - ex.getActiveCount());
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ex1) {
                BungeeCord.getInstance().getLogger().log(Level.WARNING, "§b§lBot§d§lFilter§b§l+§f Не могу сгенерировать капчу. Выключаю банджу", ex1);
                System.exit(0);
                return;
            }
        }
        CachedCaptcha.generated = true;
        executor.shutdownNow();
        System.gc();
        BungeeCord.getInstance().getLogger().log(Level.INFO, "§b§lBot§d§lFilter§b§l+§f Капча сгенерированна за {0} мс", System.currentTimeMillis() - start);
    }


    private Color randomNotWhiteColor() {
        Color color = MapPalette.colors[rnd.nextInt(MapPalette.colors.length)];

        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();

        if (r == 255 && g == 255 && b == 255) {
            return randomNotWhiteColor();
        }
        if (r == 220 && g == 220 && b == 220) {
            return randomNotWhiteColor();
        }
        if (r == 199 && g == 199 && b == 199) {
            return randomNotWhiteColor();
        }
        if (r == 255 && g == 252 && b == 245) {
            return randomNotWhiteColor();
        }
        if (r == 220 && g == 217 && b == 211) {
            return randomNotWhiteColor();
        }
        if (r == 247 && g == 233 && b == 163) {
            return randomNotWhiteColor();
        }
        return color;
    }

    private static String randomAnswer() {
        String[] b = new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a", "s", "d", "f", "g", "h", "j", "k", "l", "z", "x", "c", "v", "b", "n", "m"};
        int a = rnd.nextInt(35) + 1;
        int s = rnd.nextInt(35) + 1;
        int d = rnd.nextInt(35) + 1;
        if (rnd.nextBoolean()) {
            return b[a] + b[s] + b[d];
        }
        return b[a] + b[s] + b[d];
    }
}
