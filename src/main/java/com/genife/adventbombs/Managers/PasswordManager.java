package com.genife.adventbombs.Managers;

import com.genife.adventbombs.Formatters.BlockedPlayerElement;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.genife.adventbombs.Managers.ConfigManager.*;

public class PasswordManager {
    private final List<BlockedPlayerElement> blockedPlayers = new ArrayList<>();

    // проверяет правильность введённого пароля
    public boolean isPasswordValid(Player player, String password, String type) {
        // если пароль в зависимости от типа бомбы верный, то отдаём true
        if ((Objects.equals(type, "nuclear") && password.equals(NUCLEAR_START_PASS)) || (Objects.equals(type, "sculk") && password.equals(SCULK_START_PASS))) {
            return true;
        } else {
            // ставим время конца блокировки на 12 часов вперёд (проверяем через canUseCommand()), возвращаем false
            blockedPlayers.add(new BlockedPlayerElement(player.getUniqueId(), player.getName(), System.currentTimeMillis() + (BLOCKING_DURATION * 1000)));
            return false;
        }
    }

    public boolean isPLayerBlocked(UUID playerUUID) {
        // Если игрок есть в списке, значит он заблокирован.
        // Разблокировку проводит ActualizeData класс.
        return blockedPlayers.stream().anyMatch(blocked -> blocked.playerUUID().equals(playerUUID));
    }

    // удаляет заблокированных из списка по запросу, возвращает, удалило ли
    public boolean unblockPlayer(UUID playerUUID) {
        return blockedPlayers.removeIf(blocked -> blocked.playerUUID().equals(playerUUID));
    }

    // возвращает список заблокированных
    public List<BlockedPlayerElement> getBlockedPlayers() {
        return new ArrayList<>(blockedPlayers);
    }
}
