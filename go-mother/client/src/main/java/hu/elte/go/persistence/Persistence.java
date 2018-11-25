package hu.elte.go.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import hu.elte.go.BoardResponse;
import hu.elte.go.dtos.*;
import hu.elte.go.events.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.stereotype.Component;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.sockjs.frame.Jackson2SockJsMessageCodec;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

@Component
public class Persistence implements IPersistence {

    private final ApplicationEventPublisher publisher;
    private MyStompSessionHandler stompSessionHandler;
    private String clientUuid;
    Logger logger = Logger.getLogger(Persistence.class.getName());
    private StompSession.Subscription creationSubscription;
    private String userName;

    @Autowired
    public Persistence(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
        this.clientUuid = UUID.randomUUID().toString();
        stompSessionHandler = new MyStompSessionHandler();
    }

    @Override
    public void connect(String IPAddress) {
        try {
            connectToServer(IPAddress).get();
            subscribeToEvents();
        } catch (ExecutionException ex) {
            publisher.publishEvent(new ConnectToServer(this, false));
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "Connection interrupted", ex);
        }
    }

    public void subscribeToEvents() {
        subscribeTo("/roomsResponse",
                getRoomListCallback(), new TypeReference<BoardResponse<RoomListDTO>>(){});
        creationSubscription = subscribeTo("/createPlayerResponse/" + clientUuid,
                getCreatePlayerCallback(), new TypeReference<BoardResponse<Void>>(){});
//        subscribeTo("/newGameResponse", this, new TypeReference<BoardResponse<NewGameStartedDTO>>() {});
//        subscribeTo("/gameStepped", this, new TypeReference<BoardResponse<GameSteppedDTO>>() {});
//        subscribeTo("/switchPlayerResponse", this, new TypeReference<BoardResponse<PlayerSwitchedDTO>>() {});
//        subscribeTo("/messages", this, new TypeReference<BoardResponse<MessageDTO>>() {});
//        subscribeTo("/playerUpdates", this, new TypeReference<BoardResponse<PlayerUpdateDTO>>() {});
//        subscribeTo("/buyEvents", this, new TypeReference<BoardResponse<BuyDTO>>() {});
//        subscribeTo("/buyItemsResponse", this, new TypeReference<BoardResponse<PurchasedListDTO>>() {});
//        subscribeTo("/gameOver", this, new TypeReference<BoardResponse<GameOverDTO>>() {});
    }

    private void uuidFinalizedSubscribes(){
        subscribeTo("/createRoomResponse/" + clientUuid,
                getCreateRoomCallback(), new TypeReference<BoardResponse<RoomCreationDTO>>(){});
        subscribeTo("/joinRooResponse/" + clientUuid,
                getJoinAttemptCallback(), new TypeReference<BoardResponse<RoomDetailsDTO>>(){});
    }

    private ListenableFuture<StompSession> connectToServer(String IPAddress) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        Transport webSocketTransport = new WebSocketTransport(new StandardWebSocketClient());
        List<Transport> transports = Collections.singletonList(webSocketTransport);

        SockJsClient sockJsClient = new SockJsClient(transports);
        sockJsClient.setMessageCodec(new Jackson2SockJsMessageCodec());

        WebSocketStompClient stompClient = new WebSocketStompClient(sockJsClient);

        String url = "ws://{host}:{port}/go";
        return stompClient.connect(url, headers, stompSessionHandler, IPAddress, 8080);
    }

    @Override
    public void createPlayer(String name) {
        this.userName = name;
        String json = "{}";
        stompSessionHandler.getSession()
                .send("/app/createPlayer/" + clientUuid + "/" + name, json.getBytes());
        System.out.println("Sent");
    }

    @Override
    public void requestNewGame(int playerNumber) {
        StompSession stompSession = stompSessionHandler.getSession();
        NewGameRequestDTO ngr = new NewGameRequestDTO(playerNumber);
        ObjectMapper mapper = new ObjectMapper();
        String json;
        try {
            json = mapper.writeValueAsString(ngr);
            stompSession.send("/app/newGame", json.getBytes());
        } catch (JsonProcessingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void requestStep() {
        StompSession stompSession = stompSessionHandler.getSession();
        String json = "{}";
        stompSession.send("/app/step", json.getBytes());
    }

    @Override
    public void switchPlayer(int currentPlayerIndex) {
        StompSession stompSession = stompSessionHandler.getSession();
        String json = "" + currentPlayerIndex;
        stompSession.send("/app/switchPlayer", json.getBytes());
    }

    @Override
    public void buyItems(List<String> itemsToPurchase) {
        StompSession stompSession = stompSessionHandler.getSession();
        ItemListDTO dto = new ItemListDTO(itemsToPurchase);
        ObjectMapper mapper = new ObjectMapper();
        try {
            String json = mapper.writeValueAsString(dto);
            System.out.println(json);
            stompSession.send("/app/buyItems", json.getBytes());
        } catch (JsonProcessingException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void getRoomList() {
        StompSession stompSession = stompSessionHandler.getSession();
        String body = "{}";
        stompSession.send("/app/rooms", body.getBytes());
    }

    @Override
    public void createRoom(String roomName) {
        StompSession stompSession = stompSessionHandler.getSession();
        stompSession.send("/app/createRoom/" + clientUuid, roomName.getBytes());
    }

    @Override
    public void joinRoom(String roomUuid) {
        StompSession stompSession = stompSessionHandler.getSession();
        stompSession.send("/app/joinRoom/" + roomUuid + "/" + clientUuid, "{}".getBytes());
    }

    private class MyStompSessionHandler extends StompSessionHandlerAdapter {
        private StompSession session;
        public StompSession getSession() {
            return session;
        }

        @Override
        public void afterConnected(StompSession stompSession, StompHeaders stompHeaders) {
            System.out.println("Now connected");
            session = stompSession;
            publisher.publishEvent(new ConnectToServer(this, true));
        }
    }

    private <D> StompSession.Subscription subscribeTo(String path, Consumer<BoardResponse<D>> callback, TypeReference tr) {
        StompSession stompSession = stompSessionHandler.getSession();
        return stompSession.subscribe(path, new StompFrameHandler() {

            @Override
            public Type getPayloadType(StompHeaders stompHeaders) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders stompHeaders, Object o) {
                ObjectMapper mapper = new ObjectMapper();
                BoardResponse<D> response;
                try {
                    String json = new String((byte[]) o);
                    response = mapper.readValue(json, tr);
                    System.out.println("Handling frame after path: " + path);
                    callback.accept(response);
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }
        });
    }

    private Consumer<BoardResponse<Void>> getCreatePlayerCallback(){
        return (resp) -> {
            if(resp.isActionSuccessful()){
                System.out.println("Player created.");
                publisher.publishEvent(new PlayerCreatedEvent(this));
                uuidFinalizedSubscribes();
                return;
            }
            if (!resp.getErrorMessage().contains("UUID")) {
                System.out.println("Username already used.");
                publisher.publishEvent(new ErrorEvent(this, resp.getErrorMessage()));
                return;
            }

            //Probably never end up here.
            creationSubscription.unsubscribe();
            clientUuid = UUID.randomUUID().toString();
            creationSubscription = subscribeTo("/createPlayerResponse/" + clientUuid,
                    getCreatePlayerCallback(), new TypeReference<BoardResponse<Void>>(){});
            createPlayer(userName);
        };
    }

    private Consumer<BoardResponse<RoomListDTO>> getRoomListCallback() {
        return (resp) -> {
            if (resp.isActionSuccessful()) {
                publisher.publishEvent(new RoomsRefreshEvent(this, resp.getValue().rooms));
            } else {
                publisher.publishEvent(new ErrorEvent(this, resp.getErrorMessage()));
            }
        };
    }

    private Consumer<BoardResponse<RoomCreationDTO>> getCreateRoomCallback() {
        return (resp) -> {
            if (resp.isActionSuccessful()) {
                RoomCreationDTO r = resp.getValue();
                publisher.publishEvent(new RoomCreatedEvent(this, r.name, r.roomUuid));
            } else {
                publisher.publishEvent(new ErrorEvent(this, resp.getErrorMessage()));
            }
        };
    }

    private Consumer<BoardResponse<Void>> getJoinAttemptCallback() {
        return (resp) -> {
            if (resp.isActionSuccessful()) {
                publisher.publishEvent(new JoinedToRoomEvent(this));
            } else {
                publisher.publishEvent(new ErrorEvent(this, resp.getErrorMessage()));
            }
        };
    }
}
