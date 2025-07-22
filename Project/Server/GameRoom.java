package Project.Server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Exceptions.MissingCurrentPlayerException;
import Project.Exceptions.NotPlayersTurnException;
import Project.Exceptions.NotReadyException;
import Project.Exceptions.PhaseMismatchException;
import Project.Exceptions.PlayerNotFoundException;

public class GameRoom extends BaseGameRoom {

    // used for general rounds (usually phase-based turns)
    private TimedEvent roundTimer = null;

    // used for granular turn handling (usually turn-order turns)
    private TimedEvent turnTimer = null;
    private List<ServerThread> turnOrder = new ArrayList<>();
    private long currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
    private int round = 0;

    public GameRoom(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientAdded(ServerThread sp) {
        // sync GameRoom state to new client
        syncCurrentPhase(sp);
        syncReadyStatus(sp);
        syncTurnStatus(sp);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientRemoved(ServerThread sp) {
        // Stops the timers so room can clean up
        LoggerUtil.INSTANCE.info("Player Removed, remaining: " + clientsInRoom.size());
        long removedClient = sp.getClientId();
        turnOrder.removeIf(player -> player.getClientId() == sp.getClientId());
        if (clientsInRoom.isEmpty()) {
            resetReadyTimer();
            resetTurnTimer();
            resetRoundTimer();
            onSessionEnd();
        } else if (removedClient == currentTurnClientId) {
            onTurnStart();
        }
    }

    // timer handlers
    private void startRoundTimer() {
        roundTimer = new TimedEvent(20, () -> onRoundEnd());
        roundTimer.setTickCallback((time) -> System.out.println("Round Time: " + time));
    }

    private void resetRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void startTurnTimer() {
        turnTimer = new TimedEvent(20, () -> onTurnEnd());
        turnTimer.setTickCallback((time) -> System.out.println("Turn Time: " + time));
    }

    private void resetTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
        }
    }
    // end timer handlers

    // lifecycle methods

    /** {@inheritDoc} */
    @Override
    protected void onSessionStart() {
        LoggerUtil.INSTANCE.info("onSessionStart() start");
        changePhase(Phase.IN_PROGRESS);
        currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
        setTurnOrder();
        round = 0;
        LoggerUtil.INSTANCE.info("onSessionStart() end");
        onRoundStart();
    }

    /** {@inheritDoc} */
    @Override
    protected void onRoundStart() {
        LoggerUtil.INSTANCE.info("onRoundStart() start");
        resetRoundTimer();
        resetTurnStatus();
        round++;
        relay(null, String.format("Round %d has started", round));
        // startRoundTimer(); Round timers aren't needed for turns
        // if you do decide to use it, ensure it's reasonable and based on the number of
        // players
        LoggerUtil.INSTANCE.info("onRoundStart() end");
        onTurnStart();
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnStart() {
        LoggerUtil.INSTANCE.info("onTurnStart() start");
        resetTurnTimer();
        try {
            ServerThread currentPlayer = getNextPlayer();
            relay(null, String.format("It's %s's turn", currentPlayer.getDisplayName()));
        } catch (MissingCurrentPlayerException | PlayerNotFoundException e) {

            e.printStackTrace();
        }
        startTurnTimer();
        LoggerUtil.INSTANCE.info("onTurnStart() end"); 
    }

    // Note: logic between Turn Start and Turn End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onTurnEnd() {
        LoggerUtil.INSTANCE.info("onTurnEnd() start");
        resetTurnTimer(); // reset timer if turn ended without the time expiring
        try {
            // optionally can use checkAllTookTurn();
            if (isLastPlayer()) {
                // if the current player is the last player in the turn order, end the round
                onRoundEnd();
            } else {
                onTurnStart();
            }
        } catch (MissingCurrentPlayerException | PlayerNotFoundException e) {

            e.printStackTrace();
        }
        LoggerUtil.INSTANCE.info("onTurnEnd() end");
    }

    // Note: logic between Round Start and Round End is typically handled via timers
    // and user interaction
    /** {@inheritDoc} */
    @Override
    protected void onRoundEnd() {
        LoggerUtil.INSTANCE.info("onRoundEnd() start");
        resetRoundTimer(); // reset timer if round ended without the time expiring

        LoggerUtil.INSTANCE.info("onRoundEnd() end");
        if (round >= 3) {
            onSessionEnd();
        } else {
            onRoundStart();
        }
    }

    /** {@inheritDoc} */
    @Override
    protected void onSessionEnd() {
        LoggerUtil.INSTANCE.info("onSessionEnd() start");
        turnOrder.clear();
        currentTurnClientId = Constants.DEFAULT_CLIENT_ID;
        resetReadyStatus();
        resetTurnStatus();
        changePhase(Phase.READY);
        LoggerUtil.INSTANCE.info("onSessionEnd() end");
    }
    // end lifecycle methods
    // send/sync data to ServerThread(s)
    private void sendResetTurnStatus() {
        clientsInRoom.values().forEach(spInRoom -> {
            boolean failedToSend = !spInRoom.sendResetTurnStatus();
            if (failedToSend) {
                removeClient(spInRoom);
            }
        });
    }

    private void sendTurnStatus(ServerThread client, boolean tookTurn) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendTurnStatus(client.getClientId(), client.didTakeTurn());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

    private void syncTurnStatus(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverUser -> {
            if (serverUser.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendTurnStatus(serverUser.getClientId(),
                        serverUser.didTakeTurn(), true);
                if (failedToSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverUser.getDisplayName()));
                    disconnect(serverUser);
                }
            }
        });
    }

    // end send data to ServerThread(s)

    // misc methods
    private void resetTurnStatus() {
        clientsInRoom.values().forEach(sp -> {
            sp.setTookTurn(false);
        });
        sendResetTurnStatus();
    }

    /**
     * Sets `turnOrder` to a shuffled list of players who are ready.
     */
    private void setTurnOrder() {
        turnOrder.clear();
        turnOrder = clientsInRoom.values().stream().filter(ServerThread::isReady).collect(Collectors.toList());
        Collections.shuffle(turnOrder);
    }

    /**
     * Gets the current player based on the `currentTurnClientId`.
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private ServerThread getCurrentPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        // quick early exit
        if (currentTurnClientId == Constants.DEFAULT_CLIENT_ID) {
            throw new MissingCurrentPlayerException("Current Player not set");
        }
        return turnOrder.stream()
                .filter(sp -> sp.getClientId() == currentTurnClientId)
                .findFirst()
                // this shouldn't occur but is included as a "just in case"
                .orElseThrow(() -> new PlayerNotFoundException("Current player not found in turn order"));
    }

    /**
     * Gets the next player in the turn order.
     * If the current player is the last in the turn order, it wraps around
     * (round-robin).
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private ServerThread getNextPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        int index = 0;
        if (currentTurnClientId != Constants.DEFAULT_CLIENT_ID) {
            index = turnOrder.indexOf(getCurrentPlayer()) + 1;
            if (index >= turnOrder.size()) {
                index = 0;
            }
        }
        ServerThread nextPlayer = turnOrder.get(index);
        currentTurnClientId = nextPlayer.getClientId();
        return nextPlayer;
    }

    /**
     * Checks if the current player is the last player in the turn order.
     * 
     * @return
     * @throws MissingCurrentPlayerException
     * @throws PlayerNotFoundException
     */
    private boolean isLastPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        // check if the current player is the last player in the turn order
        return turnOrder.indexOf(getCurrentPlayer()) == (turnOrder.size() - 1);
    }

    private void checkAllTookTurn() {
        int numReady = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady())
                .toList().size();
        int numTookTurn = clientsInRoom.values().stream()
                // ensure to verify the isReady part since it's against the original list
                .filter(sp -> sp.isReady() && sp.didTakeTurn())
                .toList().size();
        if (numReady == numTookTurn) {
            relay(null,
                    String.format("All players have taken their turn (%d/%d) ending the round", numTookTurn, numReady));
            onRoundEnd();
        }
    }

    // start check methods
    private void checkCurrentPlayer(long clientId) throws NotPlayersTurnException {
        if (currentTurnClientId != clientId) {
            throw new NotPlayersTurnException("You are not the current player");
        }
    }

    // end check methods

    // receive data from ServerThread (GameRoom specific)

    /**
     * Handles the turn action from the client.
     * 
     * @param currentUser
     * @param exampleText (arbitrary text from the client, can be used for
     *                    additional actions or information)
     */
    protected void handleTurnAction(ServerThread currentUser, String exampleText) {
        try {
            checkPlayerInRoom(currentUser);
            checkCurrentPhase(currentUser, Phase.IN_PROGRESS);
            checkCurrentPlayer(currentUser.getClientId());
            checkIsReady(currentUser);
            if (currentUser.didTakeTurn()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "");
                return;
            }
            currentUser.setTookTurn(true);
            // TODO handle example text possibly or other turn related intention from client
            sendTurnStatus(currentUser, currentUser.didTakeTurn());
            ChoiceInput(currentUser, exampleText.trim());
        } catch (NotPlayersTurnException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "It's not your turn");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (NotReadyException e) {
            // The check method already informs the currentUser
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PlayerNotFoundException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to do the ready check");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PhaseMismatchException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID,
                    "You can only take a turn during the IN_PROGRESS phase");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        }
    }

        // ------------------------------------------------------------------------\/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ \/ 


    private final Map<Long, Choice> playerChoice = new HashMap<>();

    public enum Choice {

        ROCK,
        PAPER,
        SCISSOR

    }

    private void ChoiceInput(ServerThread player, String textChoice) {
        try {
            Choice choice;
            try {
                choice = Choice.valueOf(textChoice.toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Please Choose ROCK, PAPER, or SCISSOR");
                return;
            }
    
            playerChoice.put(player.getClientId(), choice);
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You Chose: " + choice);
    
            if (ChoicesEntered()) {
                gameRound();
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("ChoiceInput exception", e);
        }
    }

    private boolean ChoicesEntered() {
        long playerReady = clientsInRoom.values().stream().filter(ServerThread::isReady).count();
        return playerChoice.size() == playerReady;
    }

    private void gameRound() {
        List<Long> playerName = new ArrayList<>(playerChoice.keySet());
    
        Long player1 = playerName.get(0);
        Long player2 = playerName.get(1);
        Choice choice1 = playerChoice.get(player1);
        Choice choice2 = playerChoice.get(player2);
    
        String player1Name = clientsInRoom.get(player1).getDisplayName();
        String player2Name = clientsInRoom.get(player2).getDisplayName();
    
        String Winner;
        if (choice1 == choice2) {
            Winner = String.format("Draw! You Both Have Choosen %s.", choice1);
        } else if (
            (choice1 == Choice.ROCK && choice2 == Choice.SCISSOR) ||
            (choice1 == Choice.PAPER && choice2 == Choice.ROCK) ||
            (choice1 == Choice.SCISSOR && choice2 == Choice.PAPER)
        ) {
            Winner = String.format("WINNER!! %s ", player1Name);
        } else {
            Winner = String.format("WINNER!! %s ", player2Name);
        }
    
        relay(null, Winner);
        playerChoice.clear();
    }

     // ----------------------------------------------------------------------- /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ 
}