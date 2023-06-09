package com.genife.adventbombs.Commands;

import com.genife.adventbombs.AdventBombs;
import com.genife.adventbombs.Managers.PasswordManager;
import com.genife.adventbombs.Rockets.NuclearLogic;
import com.genife.adventbombs.Rockets.SculkLogic;
import com.genife.adventbombs.Runnables.RocketRunnable;
import com.genife.adventbombs.SoundUtils.PlaySound;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

import static com.genife.adventbombs.Managers.ConfigManager.*;

public class PassConversation extends StringPrompt {
    private final Player rocketSender;
    private final String rocketType;
    private final int cordsX;
    private final int cordsZ;
    private final int explosionPower;
    private final AdventBombs instance = AdventBombs.getInstance();
    private final PasswordManager passwordManager;

    public PassConversation(CommandSender sender, String rocketType, int cordsX, int cordsZ, int explosionPower, PasswordManager passwordManager) {
        this.rocketSender = (Player) sender;
        this.rocketType = rocketType;
        this.cordsX = cordsX;
        this.cordsZ = cordsZ;
        this.explosionPower = explosionPower;
        this.passwordManager = passwordManager;
    }

    // функция для перехвата кодового слова
    @Override
    public String getPromptText(ConversationContext context) {
        rocketSender.playSound(rocketSender.getLocation(), ROCKET_CODE_TYPING_SOUND, SoundCategory.MASTER, 1.0f, 1.0f);
        return MESSAGE_PREFIX + PASS_ENTER_MESSAGE;
    }

    @Override
    public Prompt acceptInput(ConversationContext context, String input) {
        if (passwordManager.isPasswordValid(rocketSender, input, rocketType)) {
            // запускаем ракету, ибо пароль верный
            context.getForWhom().sendRawMessage(MESSAGE_PREFIX + PASS_ENTERED_MESSAGE);
            launchNuclearRocket();
            broadcastAlarm();
            // проигрываем личное оповещение игроку "вы инициировали запуск ракет, в случае.."
            Bukkit.getScheduler().runTaskLater(instance,
                    () -> rocketSender.playSound(rocketSender.getLocation(), ROCKET_INITIALIZED_SOUND, SoundCategory.MASTER, 1.0f, 1.0f), 40);
        } else {
            // Неправильный пароль
            context.getForWhom().sendRawMessage(MESSAGE_PREFIX + INCORRECT_PASS_MESSAGE);
            playInvalidCode();
        }

        return null;
    }

    // функция для запуска ракеты
    private void launchNuclearRocket() {
        // запускаем ракету
        World senderWorld = rocketSender.getWorld();
        Location targetLocation = senderWorld.getHighestBlockAt(cordsX, cordsZ).getLocation();

        RocketRunnable task;

        if (rocketType.equals("nuclear")) {
            task = new RocketRunnable(new NuclearLogic(rocketSender, targetLocation, explosionPower));
        } else {
            task = new RocketRunnable(new SculkLogic(rocketSender, targetLocation, explosionPower));
        }

        task.runTaskTimer(instance, 0, 1);
    }

    // отправляем сообщения после пуска ракеты, начинаем проигрывать звук воздушной тревоги на их локациях
    private void broadcastAlarm() {

        Bukkit.broadcast(Component.text(MESSAGE_PREFIX + ROCKET_START_DETECTED_MESSAGE));

        BukkitRunnable sirenTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (alarmChecker()) return;
                List<Location> allAlarms = instance.getAlarmManager().getAlarmsLocations();
                for (Location location : allAlarms) {
                    new PlaySound(ALARM_SOUND, 200, location);
                }
            }
        };

        if (instance.getAlarmManager().isSirenTasksEmpty()) {
            Bukkit.broadcast(Component.text(MESSAGE_PREFIX + ALARM_START_BROADCAST_MESSAGE));
            instance.getAlarmManager().addSirenTask(sirenTask);
            // запускаем задачу с интервалом из конфига
            sirenTask.runTaskTimer(instance, 0, 20 * ALARM_SOUND_PLAY_INTERVAL);
        }
    }

    // если пароль не верный, то выводим публичное оповещение о несанкционированной попытке доступа
    private void playInvalidCode() {
        String playerName = rocketSender.getName();

        Bukkit.broadcast(Component.text(MESSAGE_PREFIX + NON_SANCTIONED_ACCESS_MESSAGE.replace("{player}", playerName)));

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), INVALID_PASS_BROADCAST_SOUND, 0.6f, 1.0f);
        }
    }

    // эта функция отправляет оповещение, если тревога кончилась
    private boolean alarmChecker() {
        if (RocketRunnable.isListEmpty()) {
            Bukkit.broadcast(Component.text(MESSAGE_PREFIX + ALARM_STOP_BROADCAST_MESSAGE));
            instance.getAlarmManager().stopSirenTasks();
            return true;
        }
        return false;
    }
}