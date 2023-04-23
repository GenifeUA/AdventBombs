package com.genife.adventbombs.Commands;

import com.genife.adventbombs.AdventBombs;
import com.genife.adventbombs.Managers.CooldownManager;
import com.genife.adventbombs.Managers.PasswordManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationFactory;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;

import static com.genife.adventbombs.Managers.ConfigManager.*;

public class RocketCommand implements CommandExecutor {
    private final HashMap<Player, Boolean> playersConversations = new HashMap<>();
    private final PasswordManager passwordManager = AdventBombs.getInstance().getPasswordManager();
    private final CooldownManager cooldownManager = new CooldownManager();

    public boolean onCommand(final CommandSender sender, final Command cmd, final String label, final String[] args) {
        if (args.length != 0) {
            if (args[0].equalsIgnoreCase("unblock")) {
                // проверяем, есть ли у игрока права оператора (OP)
                if (!sender.isOp()) {
                    sender.sendMessage(NO_PERMISSIONS);
                    return false;
                }

                if (args.length > 1) {
                    String playerName = args[1];

                    OfflinePlayer player = Bukkit.getOfflinePlayerIfCached(playerName);

                    if (player == null) {
                        sender.sendMessage(UNBLOCK_NOT_FOUND_MESSAGE.replace("{player}", playerName));
                        return true;
                    }

                    if (passwordManager.unblockPlayer(player.getUniqueId())) {
                        sender.sendMessage(UNBLOCK_SUCCESS_MESSAGE.replace("{player}", playerName));
                    } else {
                        sender.sendMessage(UNBLOCK_NOT_FOUND_MESSAGE.replace("{player}", playerName));
                    }
                    return true;
                }

                // если что-то введено не верно - информируем пользователя
                sender.sendMessage(INCORRECT_TYPING_MESSAGE);
                return false;
            }

            if (args[0].equalsIgnoreCase("nuclear") || args[0].equalsIgnoreCase("sculk")) {
                // проверка, кто отправил команду (игрок/другие источники)
                if (!(sender instanceof Player)) {
                    sender.sendMessage(ONLY_IN_GAME_COMMAND);
                    return false;
                }

                // если отправлены не все аргументы, то информируем игрока об этом
                if (args.length < 4) {
                    sender.sendMessage(INCORRECT_TYPING_MESSAGE);
                    return false;
                }

                try {
                    UUID senderUUID = ((Player) sender).getUniqueId();
                    // устанавливаем параметры нашей ракеты
                    String rocketType = args[0];
                    int cordsX = Integer.parseInt(args[1]);
                    int cordsZ = Integer.parseInt(args[2]);
                    int rocketPower = Integer.parseInt(args[3]);

                    // ограничиваем мощность ракеты
                    if (rocketPower < 0 || rocketPower > 100) {
                        sender.sendMessage(INCORRECT_TYPING_MESSAGE);
                        return false;
                    }

                    // если пользователь заблокирован из-за ввода неправильного пароля ранее, то не пропускаем.
                    if (passwordManager.isPLayerBlocked(senderUUID)) {
                        sender.sendMessage(PLAYER_BLOCKED_MESSAGE);
                        return false;
                    }

                    // проверяем, нет ли у игрока уже запущенного Conversation
                    if (playersConversations.containsKey((Player) sender)) {
                        sender.sendMessage(ALREADY_TYPING_PASS_MESSAGE);
                        return false;
                    }

                    Duration timeLeft = cooldownManager.getRemainingCooldown(senderUUID);

                    if (!timeLeft.isZero()) {
                        double secondsLeft = (double) timeLeft.getSeconds() + (double) timeLeft.getNano() / 1_000_000_000;
                        DecimalFormat df = new DecimalFormat("0.0");
                        String durationString = df.format(secondsLeft);
                        sender.sendMessage(COOLDOWN_COMMAND.replace("{duration}", durationString));
                        return false;
                    }

                    // создаём "перехватчик" ввода (в нашем случае, проверяем интерактивно пароль)
                    ConversationFactory cf = new ConversationFactory(AdventBombs.getInstance());
                    Conversation conv = cf
                            .withFirstPrompt(new PassConversation(sender, rocketType, cordsX, cordsZ, rocketPower, passwordManager))
                            .withLocalEcho(false)
                            .withTimeout(120)
                            .buildConversation((Player) sender);

                    // помечаем, что у игрока сейчас запущен PassConversation
                    playersConversations.put((Player) sender, true);

                    // Действия после завершения чата
                    conv.addConversationAbandonedListener(event -> {
                        if (!event.gracefulExit()) {
                            // Отправляем сообщение, если Conversation был прерван по таймауту
                            ((Player) event.getContext().getForWhom()).sendMessage(NO_PASS_TYPED_MESSAGE);
                        }
                        playersConversations.remove((Player) sender);
                    });

                    conv.begin();

                    cooldownManager.setCooldown(senderUUID, Duration.ofSeconds(CooldownManager.DEFAULT_COOLDOWN));

                    return true;

                } catch (NumberFormatException e) {
                    // если игрок ввёл не число, то выводим сообщение о неверном вводе.
                    sender.sendMessage(INCORRECT_TYPING_MESSAGE);
                    return false;
                }
            }
        }

        // если вообще нет аргументов, то тоже информируем человека
        sender.sendMessage(INCORRECT_TYPING_MESSAGE);
        return false;

    }
}