package Project.Server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Map;

import Project.Common.Constants;
import Project.Common.LoggerUtil;
import Project.Common.Phase;
import Project.Common.TimedEvent;
import Project.Common.TimerType;
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

    // Rock Paper Scissors Implementation
    private final Map<Long, Choice> playerChoice = new HashMap<>();

    public enum Choice {
        ROCK,
        PAPER,
        SCISSORS
    }

    public GameRoom(String name) {
        super(name);
    }

    /** {@inheritDoc} */
    @Override
    protected void onClientAdded(ServerThread sp) {
        // sync GameRoom state to new client

        syncCurrentPhase(sp);
        // sync only what's necessary for the specific phase
        // if you blindly sync everything, you'll get visual artifacts/discrepancies
        syncReadyStatus(sp);
        if (currentPhase != Phase.READY) {
            syncTurnStatus(sp); // turn/ready use the same visual process so ensure turn status is only called
                                // outside of ready phase
            syncPlayerPoints(sp);
        }

    }

    /** {@inheritDoc} */
    @Override
    protected void onClientRemoved(ServerThread sp) {
        // Stops the timers so room can clean up
        LoggerUtil.INSTANCE.info("Player Removed, remaining: " + clientsInRoom.size());
        long removedClient = sp.getClientId();
        turnOrder.removeIf(player -> player.getClientId() == sp.getClientId());
        playerChoice.remove(removedClient); // Remove from RPS choices

        if (clientsInRoom.isEmpty()) {
            resetReadyTimer();
            resetTurnTimer();
            resetRoundTimer();
            onSessionEnd();
        } else if (removedClient == currentTurnClientId) {
            onTurnStart();
        } else if (playerChoice.size() > 0 && allPlayersChose()) {
            // If remaining players have all chosen, process the round
            processRPSRound();
        }
    }

    // timer handlers
    private void startRoundTimer() {
        roundTimer = new TimedEvent(15, () -> onRoundEnd()); // Increased time for RPS
        roundTimer.setTickCallback((time) -> {
            System.out.println("Round Time: " + time);
            sendCurrentTime(TimerType.ROUND, time);
        });
    }

    private void resetRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
            sendCurrentTime(TimerType.ROUND, -1);
        }
    }

    private void startTurnTimer() {
        turnTimer = new TimedEvent(10, () -> onTurnEnd());
        turnTimer.setTickCallback((time) -> {
            System.out.println("Turn Time: " + time);
            sendCurrentTime(TimerType.TURN, time);
        });
    }

    private void resetTurnTimer() {
        if (turnTimer != null) {
            turnTimer.cancel();
            turnTimer = null;
            sendCurrentTime(TimerType.TURN, -1);
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
        playerChoice.clear(); // Clear any existing choices
        LoggerUtil.INSTANCE.info("onSessionStart() end");
        onRoundStart();
    }

    /** {@inheritDoc} */
    @Override
    protected void onRoundStart() {
        LoggerUtil.INSTANCE.info("onRoundStart() start");
        resetRoundTimer();
        resetTurnStatus();
        playerChoice.clear(); // Clear choices from previous round
        round++;

        sendGameEvent(String.format("Round %d - Make your choice: ROCK, PAPER, or SCISSORS!", round));
        startRoundTimer(); // Start timer for players to make choices

        // For RPS, we don't need individual turns - all players choose simultaneously
        LoggerUtil.INSTANCE.info("onRoundStart() end");
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnStart() {
        LoggerUtil.INSTANCE.info("onTurnStart() start");
        resetTurnTimer();
        try {
            ServerThread currentPlayer = getNextPlayer();
            sendGameEvent(String.format("It's %s's turn", currentPlayer.getDisplayName()));
        } catch (MissingCurrentPlayerException | PlayerNotFoundException e) {
            e.printStackTrace();
        }
        startTurnTimer();
        LoggerUtil.INSTANCE.info("onTurnStart() end");
    }

    /** {@inheritDoc} */
    @Override
    protected void onTurnEnd() {
        LoggerUtil.INSTANCE.info("onTurnEnd() start");
        resetTurnTimer(); // reset timer if turn ended without the time expiring
        try {
            if (isLastPlayer()) {
                onRoundEnd();
            } else {
                onTurnStart();
            }
        } catch (MissingCurrentPlayerException | PlayerNotFoundException e) {
            e.printStackTrace();
        }
        LoggerUtil.INSTANCE.info("onTurnEnd() end");
    }

    /** {@inheritDoc} */
    @Override
    protected void onRoundEnd() {
        LoggerUtil.INSTANCE.info("onRoundEnd() start");
        resetRoundTimer(); // reset timer if round ended without the time expiring

        LoggerUtil.INSTANCE.info("onRoundEnd() end");
        if (round >= 5) { // Best of 5 for RPS
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
        playerChoice.clear(); // Clear RPS choices
        resetReadyStatus();
        resetTurnStatus();

        // Announce final scores
        announceFinalScores();

        changePhase(Phase.READY);
        LoggerUtil.INSTANCE.info("onSessionEnd() end");
    }
    // end lifecycle methods

    // send/sync data to ServerThread(s)
    private void syncPlayerPoints(ServerThread incomingClient) {
        clientsInRoom.values().forEach(serverUser -> {
            if (serverUser.getClientId() != incomingClient.getClientId()) {
                boolean failedToSync = !incomingClient.sendPlayerPoints(serverUser.getClientId(),
                        serverUser.getPoints());
                if (failedToSync) {
                    LoggerUtil.INSTANCE.warning(
                            String.format("Removing disconnected %s from list", serverUser.getDisplayName()));
                    disconnect(serverUser);
                }
            }
        });
    }

    private void sendPlayerPoints(ServerThread sp) {
        clientsInRoom.values().removeIf(spInRoom -> {
            boolean failedToSend = !spInRoom.sendPlayerPoints(sp.getClientId(), sp.getPoints());
            if (failedToSend) {
                removeClient(spInRoom);
            }
            return failedToSend;
        });
    }

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

    private void setTurnOrder() {
        turnOrder.clear();
        turnOrder = clientsInRoom.values().stream().filter(ServerThread::isReady).collect(Collectors.toList());
        Collections.shuffle(turnOrder);
    }

    private void announceFinalScores() {
        StringBuilder scoreMessage = new StringBuilder("🏆 FINAL SCORES 🏆\n");

        List<ServerThread> sortedPlayers = clientsInRoom.values().stream()
                .sorted((p1, p2) -> Integer.compare(p2.getPoints(), p1.getPoints()))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedPlayers.size(); i++) {
            ServerThread player = sortedPlayers.get(i);
            String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : i == 2 ? "🥉" : "  ";
            scoreMessage.append(String.format("%s %s: %d points\n",
                    medal, player.getDisplayName(), player.getPoints()));
        }

        sendGameEvent(scoreMessage.toString());
    }

    private ServerThread getCurrentPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        if (currentTurnClientId == Constants.DEFAULT_CLIENT_ID) {
            throw new MissingCurrentPlayerException("Current Player not set");
        }
        return turnOrder.stream()
                .filter(sp -> sp.getClientId() == currentTurnClientId)
                .findFirst()
                .orElseThrow(() -> new PlayerNotFoundException("Current player not found in turn order"));
    }

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

    private boolean isLastPlayer() throws MissingCurrentPlayerException, PlayerNotFoundException {
        return turnOrder.indexOf(getCurrentPlayer()) == (turnOrder.size() - 1);
    }

    private void checkAllTookTurn() {
        int numReady = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady())
                .toList().size();
        int numTookTurn = clientsInRoom.values().stream()
                .filter(sp -> sp.isReady() && sp.didTakeTurn())
                .toList().size();
        if (numReady == numTookTurn) {
            sendGameEvent(
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
     * Handles the RPS choice from the client.
     * 
     * @param currentUser The player making the choice
     * @param choice      The RPS choice (ROCK, PAPER, SCISSORS)
     */
    protected void handleTurnAction(ServerThread currentUser, String choice) {
        try {
            checkPlayerInRoom(currentUser);
            checkCurrentPhase(currentUser, Phase.IN_PROGRESS);
            checkIsReady(currentUser);

            if (currentUser.didTakeTurn()) {
                currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You have already made your choice this round");
                return;
            }

            // Process RPS choice
            processRPSChoice(currentUser, choice);

        } catch (NotReadyException e) {
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PlayerNotFoundException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID, "You must be in a GameRoom to make a choice");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (PhaseMismatchException e) {
            currentUser.sendMessage(Constants.DEFAULT_CLIENT_ID,
                    "You can only make choices during the IN_PROGRESS phase");
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("handleTurnAction exception", e);
        }
    }

    // Rock Paper Scissors Methods

    private void processRPSChoice(ServerThread player, String textChoice) {
        try {
            Choice choice;
            try {
                // Handle both SCISSOR and SCISSORS for compatibility
                if (textChoice.toUpperCase().equals("SCISSOR")) {
                    choice = Choice.SCISSORS;
                } else {
                    choice = Choice.valueOf(textChoice.toUpperCase());
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Please choose ROCK, PAPER, or SCISSORS");
                return;
            }

            playerChoice.put(player.getClientId(), choice);
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You chose: " + choice);
            player.setTookTurn(true);
            sendTurnStatus(player, true);

            // Check if all players have made their choice
            if (allPlayersChose()) {
                processRPSRound();
            } else {
                // Let everyone know how many players still need to choose
                int playersReady = (int) clientsInRoom.values().stream().filter(ServerThread::isReady).count();
                int playersChosen = playerChoice.size();
                sendGameEvent(String.format("Waiting for choices... (%d/%d players have chosen)",
                        playersChosen, playersReady));
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("processRPSChoice exception", e);
        }
    }

    private boolean allPlayersChose() {
        long playersReady = clientsInRoom.values().stream().filter(ServerThread::isReady).count();
        return playerChoice.size() == playersReady;
    }

    private void processRPSRound() {
        try {
            if (playerChoice.size() < 2) {
                sendGameEvent("Not enough players for Rock Paper Scissors");
                clearChoicesAndEndRound();
                return;
            }

            List<Long> playerIds = new ArrayList<>(playerChoice.keySet());

            if (playerIds.size() == 2) {
                // Two player game
                processTwoPlayerRPS(playerIds.get(0), playerIds.get(1));
            } else {
                // Multi-player game
                processMultiPlayerRPS(playerIds);
            }

            clearChoicesAndEndRound();

        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("processRPSRound exception", e);
            clearChoicesAndEndRound();
        }
    }

    private void processTwoPlayerRPS(Long player1Id, Long player2Id) {
        Choice choice1 = playerChoice.get(player1Id);
        Choice choice2 = playerChoice.get(player2Id);

        ServerThread player1 = clientsInRoom.get(player1Id);
        ServerThread player2 = clientsInRoom.get(player2Id);

        String player1Name = player1.getDisplayName();
        String player2Name = player2.getDisplayName();

        // Announce the choices
        sendGameEvent(String.format("⚔️ %s chose %s vs %s chose %s",
                player1Name, choice1, player2Name, choice2));

        String result;
        if (choice1 == choice2) {
            result = String.format("🤝 It's a tie! Both chose %s", choice1);
        } else if (isWinningChoice(choice1, choice2)) {
            result = String.format("🏆 %s wins with %s!", player1Name, choice1);
            player1.changePoints(1);
            sendPlayerPoints(player1);
        } else {
            result = String.format("🏆 %s wins with %s!", player2Name, choice2);
            player2.changePoints(1);
            sendPlayerPoints(player2);
        }

        sendGameEvent(result);
    }

    private void processMultiPlayerRPS(List<Long> playerIds) {
        // Group players by their choice
        Map<Choice, List<Long>> choiceGroups = new HashMap<>();
        for (Long playerId : playerIds) {
            Choice choice = playerChoice.get(playerId);
            choiceGroups.computeIfAbsent(choice, k -> new ArrayList<>()).add(playerId);
        }

        // Announce all choices
        StringBuilder choiceAnnouncement = new StringBuilder("⚔️ Choices: ");
        for (Long playerId : playerIds) {
            String playerName = clientsInRoom.get(playerId).getDisplayName();
            Choice choice = playerChoice.get(playerId);
            choiceAnnouncement.append(String.format("%s=%s ", playerName, choice));
        }
        sendGameEvent(choiceAnnouncement.toString());

        // Determine winners
        List<Choice> presentChoices = new ArrayList<>(choiceGroups.keySet());

        if (presentChoices.size() == 1) {
            sendGameEvent("🤝 Everyone chose the same thing - it's a tie!");
        } else if (presentChoices.size() == 3) {
            sendGameEvent("🤝 All three choices present - it's a tie!");
        } else {
            // Two choices present - determine winner
            Choice choice1 = presentChoices.get(0);
            Choice choice2 = presentChoices.get(1);

            Choice winningChoice = isWinningChoice(choice1, choice2) ? choice1 : choice2;
            List<Long> winners = choiceGroups.get(winningChoice);

            // Award points to winners
            for (Long winnerId : winners) {
                ServerThread winner = clientsInRoom.get(winnerId);
                winner.changePoints(1);
                sendPlayerPoints(winner);
            }

            String winnerNames = winners.stream()
                    .map(id -> clientsInRoom.get(id).getDisplayName())
                    .collect(Collectors.joining(", "));

            sendGameEvent(String.format("🏆 Winners: %s with %s!", winnerNames, winningChoice));
        }
    }

    private boolean isWinningChoice(Choice choice1, Choice choice2) {
        return (choice1 == Choice.ROCK && choice2 == Choice.SCISSORS) ||
                (choice1 == Choice.PAPER && choice2 == Choice.ROCK) ||
                (choice1 == Choice.SCISSORS && choice2 == Choice.PAPER);
    }

    private void clearChoicesAndEndRound() {
        playerChoice.clear();
        onRoundEnd();
    }

     // ----------------------------------------------------------------------- /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ /\ 
}