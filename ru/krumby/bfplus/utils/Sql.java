package ru.krumby.bfplus.utils;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.SneakyThrows;
import net.md_5.bungee.BungeeCord;
import ru.krumby.bfplus.config.Settings;
import ru.krumby.bfplus.BFPlus;
import ru.krumby.bfplus.BFPUser;

/**
 * @author Leymooo
 */
public class Sql
{

    private final BFPlus botFilter;
    private Connection connection;
    private boolean connecting = false;
    private long lastSync = System.currentTimeMillis();

    private final ScheduledExecutorService executor
        = Executors.newSingleThreadScheduledExecutor( new ThreadFactoryBuilder().setNameFormat( "BotFilter-SQL-thread" ).build() );
    private final Logger logger = BungeeCord.getInstance().getLogger();

    public Sql(BFPlus botFilter)
    {
        this.botFilter = botFilter;
        setupConnect();
    }

    @SneakyThrows
    private void setupConnect()
    {

        try
        {
            if ( executor.isShutdown() || connecting )
            {
                return;
            }
            connecting = true;
            if ( connection != null && connection.isValid( 1 ) )
            {
                return;
            }
            this.connection = null;
            logger.info( "§b§lBot§d§lFilter§b§l+§f Подключаюсь к базе данных..." );
            long start = System.currentTimeMillis();
            if ( Settings.IMP.SQL.STORAGE_TYPE.equalsIgnoreCase( "mysql" ) )
            {
                Settings.SQL s = Settings.IMP.SQL;
                connectToDatabase( String.format( "JDBC:mysql://%s:%s/%s?useSSL=false&useUnicode=true&characterEncoding=utf-8", s.HOSTNAME, s.PORT, s.DATABASE ), s.USER, s.PASSWORD );
            } else
            {
                Class.forName( "org.sqlite.JDBC" );
                connectToDatabase( "JDBC:sqlite:BFPlus/database.db", null, null );
            }
            logger.log( Level.INFO, "§b§lBot§d§lFilter§b§l+§f Подключено ({0} мс)", System.currentTimeMillis() - start );
            createTable();
            alterLastJoinColumn();
            clearOldUsers();
            loadUsers();
            startTasks();
        } catch ( SQLException | ClassNotFoundException e )
        {
            executor.schedule( this::setupConnect, 5, TimeUnit.SECONDS );
            logger.log( Level.WARNING, "Can not connect to database or execute sql: ", e );
            if ( connection != null )
            {
                Connection conn = connection;
                this.connection = null;
                conn.close();
            }
        } finally
        {
            connecting = false;
        }
    }


    private void connectToDatabase(String url, String user, String password) throws SQLException
    {
        this.connection = DriverManager.getConnection( url, user, password );
    }

    private void createTable() throws SQLException
    {
        String sql = "CREATE TABLE IF NOT EXISTS `Users` ("
            + "`Name` VARCHAR(16) NOT NULL PRIMARY KEY UNIQUE,"
            + "`Ip` VARCHAR(16) NOT NULL,"
            + "`LastCheck` BIGINT NOT NULL,"
            + "`LastJoin` BIGINT NOT NULL);";

        try ( PreparedStatement statement = connection.prepareStatement( sql ) )
        {
            statement.executeUpdate();
        }
    }

    private void alterLastJoinColumn()
    {
        try ( ResultSet rs = connection.getMetaData().getColumns( null, null, "Users", "LastJoin" ) )
        {
            if ( !rs.next() )
            {
                try ( Statement st = connection.createStatement() )
                {
                    st.executeUpdate( "ALTER TABLE `Users` ADD COLUMN `LastJoin` BIGINT NOT NULL DEFAULT 0;" );
                    st.executeUpdate( "UPDATE `Users` SET LastJoin = LastCheck" );
                }
            }
        } catch ( Exception e )
        {
            logger.log( Level.WARNING, "§b§lBot§d§lFilter§b§l+§f Ошибка при добавлении столбца в таблицу", e );
        }
    }

    private void clearOldUsers() throws SQLException
    {
        if ( Settings.IMP.SQL.PURGE_TIME <= 0 )
        {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.add( Calendar.DATE, -Settings.IMP.SQL.PURGE_TIME );
        long until = calendar.getTimeInMillis();
        int before = botFilter.getUsersCount();
        botFilter.getUserCache().entrySet().removeIf( (entry) -> entry.getValue().getLastJoin() < until );
        if ( ( before - botFilter.getUsersCount() ) > 0 )
        {
            logger.log( Level.INFO, "§b§lBot§d§lFilter§b§l+§f Удалено {0} аккаунтов из памяти", before - botFilter.getUsersCount() );
        }
        if ( this.connection != null )
        {
            try ( PreparedStatement statement = connection.prepareStatement( "DELETE FROM `Users` WHERE `LastJoin` < " + until + ";" ) )
            {
                int removed = statement.executeUpdate();
                if ( removed > 0 )
                {
                    logger.log( Level.INFO, "§b§lBot§d§lFilter§b§l+§f Удалено {0} аккаунтов из датабазы", removed );
                }
            }
        }
    }


    private void startTasks()
    {

        if ( Settings.IMP.SQL.PURGE_TIME > 0 )
        {
            executor.scheduleAtFixedRate( this::tryCleanUP, 1, 1, TimeUnit.MINUTES );
        }
        if ( Settings.IMP.SQL.SYNC_INTERVAL > 0 )
        {
            executor.scheduleAtFixedRate( this::syncUsers, Settings.IMP.SQL.SYNC_INTERVAL, Settings.IMP.SQL.SYNC_INTERVAL, TimeUnit.MILLISECONDS );
        }
    }

    private void syncUsers()
    {
        if ( connecting || connection == null )
        {
            return;
        }
        long curr = System.currentTimeMillis();
        try ( PreparedStatement statament = connection.prepareStatement( "SELECT * FROM `Users` WHERE `LastJoin` > " + lastSync + ";" );
              ResultSet set = statament.executeQuery() )
        {
            int synced = 0;
            while ( set.next() )
            {
                String name = set.getString( "Name" );
                String ip = set.getString( "Ip" );
                long lastCheck = set.getLong( "LastCheck" );
                long lastJoin = set.getLong( "LastJoin" );

                botFilter.removeUser( name );
                BFPUser BFPUser = new BFPUser( name, ip, lastCheck, lastJoin );
                botFilter.addUserToCache(BFPUser);
                synced++;
            }
            if ( synced > 0 )
            {
                logger.log( Level.INFO, "§b§lBot§d§lFilter§b§l+§f Синхронизировано ({0}) новых проверок", synced );
            }
            lastSync = curr;
        } catch ( Exception e )
        {
            logger.log( Level.WARNING, "§b§lBot§d§lFilter§b§l+§f Не удалось синхронизировать проверки", e );
            setupConnect();
        }
    }

    private void loadUsers() throws SQLException
    {
        try ( PreparedStatement statament = connection.prepareStatement( "SELECT * FROM `Users`;" );
              ResultSet set = statament.executeQuery() )
        {
            int i = 0;
            while ( set.next() )
            {
                String name = set.getString( "Name" );
                String ip = set.getString( "Ip" );
                if ( isInvalidName( name ) )
                {
                    removeUser( "REMOVE FROM `Users` WHERE `Ip` = '" + ip + "' AND `LastCheck` = '" + set.getLong( "LastCheck" ) + "';" );
                    continue;
                }
                long lastCheck = set.getLong( "LastCheck" );
                long lastJoin = set.getLong( "LastJoin" );
                BFPUser BFPUser = new BFPUser( name, ip, lastCheck, lastJoin );
                botFilter.addUserToCache(BFPUser);
                i++;
            }
            logger.log( Level.INFO, "§b§lBot§d§lFilter§b§l+§f Белый список игроков успешно загружен ({0})", i );
        }
    }

    private boolean isInvalidName(String name)
    {
        return name.contains( "'" ) || name.contains( "\"" );
    }

    private void removeUser(String sql)
    {
        if ( connection != null )
        {
            this.executor.execute( () ->
            {
                try ( PreparedStatement statament = connection.prepareStatement( sql ) )
                {
                    statament.execute();
                } catch ( SQLException ignored )
                {
                }
            } );
        }
    }

    public void saveUser(BFPUser BFPUser)
    {
        if ( connecting || isInvalidName( BFPUser.getName() ) )
        {
            return;
        }
        if ( connection != null )
        {
            this.executor.execute( () ->
            {
                final long timestamp = System.currentTimeMillis();
                String sql = "SELECT `Name` FROM `Users` where `Name` = '" + BFPUser.getName() + "' LIMIT 1;";
                try ( Statement statament = connection.createStatement();
                      ResultSet set = statament.executeQuery( sql ) )
                {
                    if ( !set.next() )
                    {
                        sql = "INSERT INTO `Users` (`Name`, `Ip`, `LastCheck`, `LastJoin`) VALUES "
                            + "('" + BFPUser.getName() + "','" + BFPUser.getIp() + "',"
                            + "'" + BFPUser.getLastCheck() + "','" + BFPUser.getLastJoin() + "');";
                        statament.executeUpdate( sql );
                    } else
                    {
                        sql = "UPDATE `Users` SET `Ip` = '" + BFPUser.getIp() + "', `LastCheck` = '" + BFPUser.getLastCheck() + "',"
                            + " `LastJoin` = '" + BFPUser.getLastJoin() + "' where `Name` = '" + BFPUser.getName() + "';";
                        statament.executeUpdate( sql );
                    }
                } catch ( SQLException ex )
                {
                    logger.log( Level.WARNING, "§b§lBot§d§lFilter§b§l+§f Не могу выполнить запрос к базе данных", ex );
                    logger.log( Level.WARNING, sql );
                    setupConnect();
                }
            } );
        }
    }

    public void tryCleanUP()
    {
        if ( Settings.IMP.SQL.PURGE_TIME > 0 )
        {
            try
            {
                clearOldUsers();
            } catch ( SQLException ex )
            {
                setupConnect();
                logger.log( Level.WARNING, "§b§lBot§d§lFilter§b§l+§f Не могу очистить пользователей", ex );
            }

        }
    }

    public void close()
    {
        this.executor.shutdownNow();
        try
        {
            if ( connection != null )
            {
                this.connection.close();
            }
        } catch ( SQLException ignore )
        {
        }
        this.connection = null;
    }
}
