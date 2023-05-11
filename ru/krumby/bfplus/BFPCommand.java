package ru.krumby.bfplus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.logging.Level;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import ru.krumby.bfplus.config.Settings;

public class BFPCommand extends Command
{

    public BFPCommand()
    {
        super( "botfilter", null, "bf", "ab", "antibot", "bfplus", "botfilterplus", "krumby", "velocitylove" );
    }

    @Override
    public void execute(CommandSender sender, String[] args)
    {
        if ( sender instanceof ProxiedPlayer )
        {
            sendStat( sender );
            return;
        }
        if ( args.length == 0 )
        {
            sender.sendMessage("§r--------------- §bBFPlus §cv" + Settings.IMP.BOT_FILTER_VERSION + "§r-----------------");
            sender.sendMessage("§r> §lbfplus reload §6- §aПерезагружить конфиг");
            sender.sendMessage("§r> §lbfplus stat §6- §aПоказать статистику");
            sender.sendMessage("§r> §lbfplus export §6- §aВыгрузить список игроков, которые прошли проверку");
            sender.sendMessage("§r> §lbfplus protection on/off §6- §aВключить или выключить ручной режим 'под атакой'");
            sender.sendMessage("§r--------------- §bBFPlus §cv" + Settings.IMP.BOT_FILTER_VERSION + "§r-----------------");
        } else if ( args[0].equalsIgnoreCase( "reload" ) )
        {
            BungeeCord.getInstance().getBotFilter().disable();
            BungeeCord.getInstance().setBotFilter( new BFPlus( false ) );
            sender.sendMessage( "§aКоманда выполнена" );
        } else if ( args[0].equalsIgnoreCase( "stat" ) || args[0].equalsIgnoreCase( "stats" ) || args[0].equalsIgnoreCase( "info" ) )
        {
            sendStat( sender );
        } else if ( args[0].equalsIgnoreCase( "export" ) )
        {
            export( sender, args );
            sender.sendMessage( "§aКоманда выполнена" );
        } else if ( args[0].equalsIgnoreCase( "protection" ) )
        {
            if ( args.length >= 2 )
            {
                boolean enable = args[1].equalsIgnoreCase( "on" );
                BungeeCord.getInstance().getBotFilter().setForceProtectionEnabled( enable );
                sender.sendMessage( "§aЗашита " + ( enable ? "включена" : "§cотключена" ) );
            }
        }
    }

    private void sendStat(CommandSender sender)
    {
        BFPlus botFilter = BungeeCord.getInstance().getBotFilter();
        sender.sendMessage("  §6§l→ §r§bBotFilter+ by Krumby#4894 / v" + Settings.IMP.BOT_FILTER_VERSION + "");
        sender.sendMessage("  §6§l→ §r§fОбнаружена атака: " + (botFilter.isUnderAttack() ? "§cДа" : "§aНет"));
        sender.sendMessage("  §6§l→ §r§fБотов на проверке: " + botFilter.getOnlineOnFilter());
        sender.sendMessage("  §6§l→ §r§fПрошло проверку: " + botFilter.getUsersCount());
        sender.sendMessage("  §6§l→ §r§fСкачать - https://dsc.gg/bfplus");
    }

    private void export(CommandSender sender, String[] args)
    {
        BFPlus botFilter = BungeeCord.getInstance().getBotFilter();

        if ( args.length == 1 )
        {
            sender.sendMessage( "§r> §lbotfilter export [TIME_IN_SECONDS] §6- §aвыгрузить список тех, кто прошёл"
                + " проверку за указаное время. укажите ALL чтобы получить за всё время." );
            sender.sendMessage( "§r> §lbotfilter export [TIME_IN_SECONDS] JOIN §6- §aвыгрузить список тех,"
                + " кто зашёл на сервер за указанное время (Учитывает и тех кто  также и прошёл проверку)." );
            return;
        }
        if ( args[1].equalsIgnoreCase( "all" ) )
        {
            List<String> out = new ArrayList<>( botFilter.getUsersCount() );
            botFilter.getUserCache().values().forEach( value ->
                out.add( value.getName() + "|" + value.getIp() + "|" + value.getLastCheck() + "|" + value.getLastJoin() )
            );
            exportToFile( out, args.length >= 3 && args[2].equalsIgnoreCase( "join" ) );
            return;
        }
        try
        {
            int seconds = Integer.parseInt( args[1] );
            boolean join = args.length >= 3 && args[2].equalsIgnoreCase( "join" );
            Calendar calendar = Calendar.getInstance();
            calendar.add( Calendar.SECOND, -seconds );
            long until = calendar.getTimeInMillis();

            List<String> out = new ArrayList<>( botFilter.getUsersCount() );
            botFilter.getUserCache().values().forEach( value ->
                {
                    if ( join )
                    {
                        if ( value.getLastJoin() >= until )
                        {
                            out.add( value.getName() + "|" + value.getIp() + "|" + value.getLastCheck() + "|" + value.getLastJoin() );
                        }
                    } else if ( value.getLastCheck() >= until )
                    {
                        out.add( value.getName() + "|" + value.getIp() + "|" + value.getLastCheck() + "|" + value.getLastJoin() );
                    }
                }
            );
            exportToFile( out, join );
        } catch ( Exception e )
        {
            sender.sendMessage( "§cУкажите число" );
        }
    }

    private void exportToFile(List<String> out, boolean join)
    {
        Path outFile = new File( "BotFilter", "whitelist.out." + ( join ? "join" : "" ) + ".txt" ).toPath();
        try
        {
            Files.write( outFile, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING );
        } catch ( IOException e )
        {
            BungeeCord.getInstance().getLogger().log( Level.WARNING, "§b§lBot§d§lFilter§b§l+§f Could not export ip's to file", e );
        }
    }

}
