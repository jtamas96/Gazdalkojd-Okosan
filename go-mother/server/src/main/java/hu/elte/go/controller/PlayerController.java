package hu.elte.go.controller;

import hu.elte.go.BoardResponse;
import hu.elte.go.data.Player;
import hu.elte.go.model.PlayersModel;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class PlayerController {

    // Currently we support one player per client.
    private PlayersModel playersModel;

    @Autowired
    public PlayerController(PlayersModel playersModel) {
        this.playersModel = playersModel;
    }

    @MessageMapping("/createPlayer/{uuid}/{name}")
    @SendTo("/createPlayerResponse/{uuid}")
    public BoardResponse<Void> createPlayer(@DestinationVariable String uuid, @DestinationVariable String name) {
        System.out.println("Player creation request with id: " + uuid + " and name: " + name);
        BoardResponse<Void> response;
        Player p = playersModel.getPlayer(uuid);
        if (p != null) {
            response = new BoardResponse<>("Ez a játékos UUID már létezik.", false, null);
        } else {
            ConcurrentMap<String, Player> playersMap = playersModel.getPlayersMap();
            long playersWithSameName = playersMap.values().stream().filter(player -> player.getName().equals(name)).count();
            if (playersWithSameName != 0) {
                response = new BoardResponse<>("Ez a név már létezik.", false, null);
            } else {
                playersModel.createPlayer(uuid, name);
                response = new BoardResponse<>("", true, null);
            }
        }
        return response;
    }
}
