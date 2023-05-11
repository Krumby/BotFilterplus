package ru.krumby.bfplus;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.UserConnection;
import net.md_5.bungee.Util;
import net.md_5.bungee.compress.PacketDecompressor;
import net.md_5.bungee.netty.ChannelWrapper;
import net.md_5.bungee.protocol.PacketWrapper;
import net.md_5.bungee.protocol.Protocol;
import net.md_5.bungee.protocol.packet.Chat;
import net.md_5.bungee.protocol.packet.ClientChat;
import net.md_5.bungee.protocol.packet.ClientSettings;
import net.md_5.bungee.protocol.packet.KeepAlive;
import net.md_5.bungee.protocol.packet.PluginMessage;
import ru.krumby.bfplus.caching.CachedCaptcha;
import ru.krumby.bfplus.caching.PacketUtils;
import ru.krumby.bfplus.caching.PacketsPosition;
import ru.krumby.bfplus.config.Settings;
import ru.krumby.bfplus.utils.FailedUtils;
import ru.krumby.bfplus.utils.IPUtils;
import ru.krumby.bfplus.utils.ManyChecksUtils;

/**
 * @author Leymooo
 */
@EqualsAndHashCode(callSuper = false, of =
    {
    "name"
    })
public class Connector extends MoveHandler
{

    private static final Logger LOGGER = BungeeCord.getInstance().getLogger();
    private static final int MAX_PLUGIN_MESSAGES_BYTES = 160000; //160 KB

    public static int TOTAL_TICKS = 100;
    private static long TOTAL_TIME = ( TOTAL_TICKS * 50 ) - 100; //TICKS * 50MS

    private final BFPlus botFilter;
    private final String name;
    private final String ip;
    @Getter
    private final int version;
    private final ThreadLocalRandom random = ThreadLocalRandom.current();
    @Getter
    private UserConnection userConnection;
    @Getter
    @Setter
    private BFPlus.CheckState state = BFPlus.CheckState.CAPTCHA_ON_POSITION_FAILED;
    @Getter
    private Channel channel;
    private String captchaAnswer;
    private int aticks = 0, sentPings = 0, attemps = Settings.CAPTCHA.ATTEMPS;
    @Getter
    private long joinTime = System.currentTimeMillis();
    private long lastSend = 0, totalping = 9999;
    private boolean markDisconnected = false;
    private int pluginMessagesBytes = 0;

    public Connector(UserConnection userConnection, BFPlus botFilter)
    {
        this.botFilter = botFilter;
        this.state = this.botFilter.getCurrentCheckState();
        this.name = userConnection.getName();
        this.channel = userConnection.getCh().getHandle();
        this.userConnection = userConnection;
        this.version = userConnection.getPendingConnection().getVersion();
        this.userConnection.setClientEntityId( PacketUtils.CLIENTID );
        this.userConnection.setDimension( 0 );
        this.ip = IPUtils.getAddress( this.userConnection ).getHostAddress();

        if ( Settings.IMP.PROTECTION.SKIP_GEYSER && botFilter.isGeyser( userConnection.getPendingConnection() ) )
        {
            this.state = BFPlus.CheckState.ONLY_CAPTCHA;
        }
    }


    public void spawn()
    {
        this.botFilter.incrementBotCounter();
        if ( !Settings.IMP.PROTECTION.ALWAYS_CHECK )
        {
            ManyChecksUtils.IncreaseOrAdd( IPUtils.getAddress( this.userConnection ) );
        }
        if ( state == BFPlus.CheckState.CAPTCHA_ON_POSITION_FAILED )
        {
            PacketUtils.spawnPlayer( channel, userConnection.getPendingConnection().getVersion(), false, false );
            PacketUtils.titles[0].writeTitle( channel, version );
        } else
        {
            PacketUtils.spawnPlayer( channel, userConnection.getPendingConnection().getVersion(), state == BFPlus.CheckState.ONLY_CAPTCHA, true );
            sendCaptcha();
            PacketUtils.titles[1].writeTitle( channel, version );
        }
        sendPing();
        LOGGER.log( Level.INFO, toString() + " has connected" );

    }

    @Override
    public void exception(Throwable t) throws Exception
    {
        markDisconnected = true;
        if ( state == BFPlus.CheckState.FAILED )
        {
            channel.close();
        } else
        {
            this.userConnection.disconnect( Util.exception( t ) );
        }
        disconnected();
    }

    @Override
    public void handle(PacketWrapper packet) throws Exception
    {
        //There are no unknown packets which player will send and will be longer than 2048 bytes during check
        if ( packet.packet == null && packet.buf.readableBytes() > 2048 )
        {
            failed( PacketUtils.KickType.BIG_PACKET, "Sent packet larger than 2048 bytes (" + packet.buf.readableBytes() + ")" );
        }
    }

    @Override
    public void disconnected(ChannelWrapper channel) throws Exception
    {
        switch ( state )
        {
            case ONLY_CAPTCHA:
            case ONLY_POSITION:
            case CAPTCHA_POSITION:
                String info = "(BFPlus) [" + name + "|" + ip + "] left from server during check";
                LOGGER.log( Level.INFO, info );
                FailedUtils.addIpToQueue( ip, PacketUtils.KickType.LEAVED );
                break;
        }
        botFilter.removeConnection( null, this );
        disconnected();
    }

    @Override
    public void handlerChanged()
    {
        disconnected();
    }

    private void disconnected()
    {
        channel = null;
        userConnection = null;
    }

    public void completeCheck()
    {
        if ( System.currentTimeMillis() - joinTime < TOTAL_TIME && state != BFPlus.CheckState.ONLY_CAPTCHA )
        {
            if ( state == BFPlus.CheckState.CAPTCHA_POSITION && aticks < TOTAL_TICKS )
            {
                channel.writeAndFlush( PacketUtils.getCachedPacket( PacketsPosition.SETSLOT_RESET ).get( version ), channel.voidPromise() );
                state = BFPlus.CheckState.ONLY_POSITION;
            } else
            {
                if ( state == BFPlus.CheckState.CAPTCHA_ON_POSITION_FAILED )
                {
                    changeStateToCaptcha();
                } else
                {
                    failed( PacketUtils.KickType.FAILED_FALLING, "Too fast check passed" );
                }
            }
            return;
        }
        int devide = lastSend == 0 ? sentPings : sentPings - 1;
        if ( botFilter.checkBigPing( totalping / ( devide <= 0 ? 1 : devide ) ) )
        {
            failed( PacketUtils.KickType.PING, "Big ping" );
            return;
        }
        state = BFPlus.CheckState.SUCCESSFULLY;
        PacketUtils.titles[2].writeTitle( channel, version );
        channel.flush();
        botFilter.removeConnection( null, this );
        sendMessage( PacketsPosition.CHECK_SUS_MSG );
        botFilter.saveUser( getName(), IPUtils.getAddress( userConnection ), true );
        PacketDecompressor packetDecompressor = channel.pipeline().get( PacketDecompressor.class );
        if ( packetDecompressor != null )
        {
            packetDecompressor.checking = false;
        }
        userConnection.setNeedLogin( false );
        userConnection.getPendingConnection().finishLogin( userConnection, true );
        markDisconnected = true;
        LOGGER.log( Level.INFO, "[BFPlus] Игрок (" + name + "|" + ip + ") успешно прошёл проверку" );
    }

    @Override
    public void onMove()
    {
        if ( lastY == -1 || state == BFPlus.CheckState.FAILED || state == BFPlus.CheckState.SUCCESSFULLY || onGround )
        {
            return;
        }
        if ( state == BFPlus.CheckState.ONLY_CAPTCHA )
        {
            if ( lastY != y && waitingTeleportId == -1 )
            {
                resetPosition( true );
            }
            return;
        }
        // System.out.println( "lastY=" + lastY + "; y=" + y + "; diff=" + formatDouble( lastY - y ) + "; need=" + getSpeed( ticks ) +"; ticks=" + ticks );
        if ( formatDouble( lastY - y ) != getSpeed( ticks ) )
        {
            if ( state == BFPlus.CheckState.CAPTCHA_ON_POSITION_FAILED )
            {
                changeStateToCaptcha();
            } else
            {
                failed( PacketUtils.KickType.FAILED_FALLING, "Failed position check" );
            }
            return;
        }
        if ( y <= 60 && state == BFPlus.CheckState.CAPTCHA_POSITION && waitingTeleportId == -1 )
        {
            resetPosition( false );
        }
        if ( aticks >= TOTAL_TICKS && state != BFPlus.CheckState.CAPTCHA_POSITION )
        {
            completeCheck();
            return;
        }
        if ( state == BFPlus.CheckState.CAPTCHA_ON_POSITION_FAILED || state == BFPlus.CheckState.ONLY_POSITION )
        {
            ByteBuf expBuf = PacketUtils.expPackets.get( aticks, version );
            if ( expBuf != null )
            {
                channel.writeAndFlush( expBuf, channel.voidPromise() );
            }
        }
        ticks++;
        aticks++;
    }

    private void resetPosition(boolean disableFall)
    {
        if ( disableFall )
        {
            channel.write( PacketUtils.getCachedPacket( PacketsPosition.PLAYERABILITIES ).get( version ), channel.voidPromise() );
        }
        waitingTeleportId = 9876;
        channel.writeAndFlush( PacketUtils.getCachedPacket( PacketsPosition.PLAYERPOSANDLOOK_CAPTCHA ).get( version ), channel.voidPromise() );
    }

    @Override
    public void handle(Chat chat) throws Exception
    {
        handleChat( chat.getMessage() );

    }

    @Override
    public void handle(ClientChat chat) throws Exception
    {
        handleChat( chat.getMessage() );
    }

    private void handleChat(String message)
    {
        if ( state != BFPlus.CheckState.CAPTCHA_ON_POSITION_FAILED )
        {
            if ( message.length() > 256 )
            {
                failed( PacketUtils.KickType.FAILED_CAPTCHA, "Too long message" );
                return;
            }
            if ( message.replace( "/", "" ).equals( captchaAnswer ) )
            {
                completeCheck();
            } else if ( --attemps != 0 )
            {
                sendMessage( ( attemps == 2 ? PacketsPosition.CAPTCHA_FAILED_2_MSG : PacketsPosition.CAPTCHA_FAILED_1_MSG ) );
                sendCaptcha();
            } else
            {
                failed( PacketUtils.KickType.FAILED_CAPTCHA, "Failed captcha check" );
            }
        }
    }

    @Override
    public void handle(ClientSettings settings) throws Exception
    {
        this.userConnection.setSettings( settings );
        this.userConnection.setCallSettingsEvent( true );
    }

    @Override
    public void handle(KeepAlive keepAlive) throws Exception
    {
        if ( keepAlive.getRandomId() == PacketUtils.KEEPALIVE_ID )
        {
            if ( lastSend == 0 )
            {
                failed( PacketUtils.KickType.PING, "Tried send fake ping" );
                return;
            }
            long ping = System.currentTimeMillis() - lastSend;
            totalping = totalping == 9999 ? ping : totalping + ping;
            lastSend = 0;
        }
    }

    @Override
    public void handle(PluginMessage pluginMessage) throws Exception
    {

        pluginMessagesBytes += ( pluginMessage.getTag().length() * 4 );
        pluginMessagesBytes += ( pluginMessage.getData().length );

        if ( pluginMessagesBytes > MAX_PLUGIN_MESSAGES_BYTES )
        {
            failed( PacketUtils.KickType.BIG_PACKET, "Bad PluginMessage's" );
            return;
        }

        if ( !userConnection.getPendingConnection().relayMessage0( pluginMessage ) )
        {
            userConnection.addDelayedPluginMessage( pluginMessage );
        }

    }

    public void sendPing()
    {
        if ( this.lastSend == 0 && !( state == BFPlus.CheckState.FAILED || state == BFPlus.CheckState.SUCCESSFULLY ) )
        {
            lastSend = System.currentTimeMillis();
            sentPings++;
            channel.writeAndFlush( PacketUtils.getCachedPacket( PacketsPosition.KEEPALIVE ).get( version ) );
        }
    }

    private void sendCaptcha()
    {
        CachedCaptcha.CaptchaHolder captchaHolder = PacketUtils.captchas.randomCaptcha();
        captchaAnswer = captchaHolder.getAnswer();
        channel.write( PacketUtils.getCachedPacket( PacketsPosition.SETSLOT_MAP ).get( version ), channel.voidPromise() );
        captchaHolder.write( channel, version, true );
    }

    private void changeStateToCaptcha()
    {
        state = BFPlus.CheckState.ONLY_CAPTCHA;
        joinTime = System.currentTimeMillis() + 3500;
        channel.write( PacketUtils.getCachedPacket( PacketsPosition.SETEXP_RESET ).get( version ), channel.voidPromise() );
        PacketUtils.titles[1].writeTitle( channel, version );
        resetPosition( true );
        sendCaptcha();
    }

    public String getName()
    {
        return name.toLowerCase();
    }

    public boolean isConnected()
    {
        return userConnection != null && channel != null && !markDisconnected && userConnection.isConnected();
    }

    public void failed(PacketUtils.KickType type, String kickMessage)
    {
        state = BFPlus.CheckState.FAILED;
        PacketUtils.kickPlayer( type, Protocol.GAME, userConnection.getCh(), version );
        markDisconnected = true;
        LOGGER.log( Level.INFO, "(BF) [" + name + "|" + ip + "] check failed: " + kickMessage );
        if ( type != PacketUtils.KickType.BIG_PACKET )
        {
            FailedUtils.addIpToQueue( ip, type );
        }
    }

    public void sendMessage(int index)
    {
        PacketUtils.messages[index].write( getChannel(), getVersion() );
    }


    @Override
    public String toString()
    {
        return "[" + name + "|" + ip + "] <-> BFPlus";
    }
}
