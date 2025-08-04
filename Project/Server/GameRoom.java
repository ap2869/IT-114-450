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

    // Rock Paper Scissors Lizard Spock Implementation
    private final Map<Long, Choice> playerChoice = new HashMap<>();
    
    // NEW: Cooldown system - track last choice per player
    private final Map<Long, Choice> playerLastChoice = new HashMap<>();
    private boolean cooldownEnabled = true; // Default: cooldowns enabled

    public enum Choice {
        ROCK,
        PAPER,
        SCISSORS,
        LIZARD,
        SPOCK
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
        playerChoice.remove(removedClient);
        playerLastChoice.remove(removedClient); // Clean up cooldown data

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
        roundTimer = new TimedEvent(15, () -> onRoundEnd()); 
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
        playerChoice.clear(); 
        playerLastChoice.clear(); // Clear cooldown data on new game
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

        String cooldownStatus = cooldownEnabled ? " (No repeats allowed!)" : "";
        sendGameEvent(String.format("Round %d - Make your choice: ROCK, PAPER, SCISSORS, LIZARD, or SPOCK!%s", round, cooldownStatus));
        startRoundTimer(); // Start timer for players to make choices

        // For RPSLS, we don't need individual turns - all players choose simultaneously
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
        if (round >= 5) { // Best of 5 for RPSLS
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
        playerChoice.clear(); // Clear RPSLS choices
        playerLastChoice.clear(); // Clear cooldown data
        resetReadyStatus();
        resetTurnStatus();

        // Announce final scores
        FinalScores();

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

    private void FinalScores() {
        StringBuilder scoreMessage = new StringBuilder("** FINAL SCORES ** \n");

        List<ServerThread> sortedPlayers = clientsInRoom.values().stream()
                .sorted((p1, p2) -> Integer.compare(p2.getPoints(), p1.getPoints()))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedPlayers.size(); i++) {
            ServerThread player = sortedPlayers.get(i);
            String medal = i == 0 ? "" : i == 1 ? "" : i == 2 ? "" : "  ";
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

    private void checkIsHost(ServerThread player) throws Exception {
        // Simple host check - first player in room or you can implement more sophisticated logic
        if (turnOrder.isEmpty() || !turnOrder.get(0).equals(player)) {
            throw new Exception("Only the host can perform this action");
        }
    }
    // end check methods

    // receive data from ServerThread (GameRoom specific)

    /**
     * Handles the RPSLS choice from the client.
     * 
     * @param currentUser The player making the choice
     * @param choice      The RPSLS choice (ROCK, PAPER, SCISSORS, LIZARD, SPOCK)
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

            // Process RPSLS choice with cooldown check
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

    /**
     * NEW: Host can toggle cooldown feature
     */
    protected void handleCooldownToggle(ServerThread host, boolean enableCooldown) {
        try {
            checkIsHost(host); // Verify this player is the host
            cooldownEnabled = enableCooldown;
            
            String status = enableCooldown ? "enabled" : "disabled";
            sendGameEvent(String.format("🔄 Choice cooldowns %s by %s", status, host.getDisplayName()));
            
        } catch (Exception e) {
            host.sendMessage(Constants.DEFAULT_CLIENT_ID, "Only the host can toggle cooldowns");
        }
    }


    /**
     * NEW: Handle spectator join requests
     */
    protected void handleSpectatorJoin(ServerThread client) {
        client.setSpectator(true);
        client.setReady(false); 
        

        client.sendMessage(Constants.DEFAULT_CLIENT_ID, 
            " You are now spectating this game. You can watch but cannot participate in game.");
    }

    // Rock Paper Scissors Lizard Spock Methods

    private void processRPSChoice(ServerThread player, String textChoice) {
        try {
            Choice choice;
            try {
                // Handle legacy and new choices
                String upperChoice = textChoice.toUpperCase();
                if (upperChoice.equals("SCISSOR")) {
                    choice = Choice.SCISSORS;
                } else {
                    choice = Choice.valueOf(upperChoice);
                }
            } catch (IllegalArgumentException e) {
                player.sendMessage(Constants.DEFAULT_CLIENT_ID, "Please choose ROCK, PAPER, SCISSORS, LIZARD, or SPOCK");
                return;
            }

            // prevent same choice twice in a row
            if (cooldownEnabled) {
                Choice lastChoice = playerLastChoice.get(player.getClientId());
                if (lastChoice != null && lastChoice == choice) {
                    player.sendMessage(Constants.DEFAULT_CLIENT_ID, 
                        String.format(" You already chose %s last round! Please choose another option.", choice));
                    return; // Block the choice
                }
            }

            // Choice is valid - proceed normally
            playerChoice.put(player.getClientId(), choice);
            player.sendMessage(Constants.DEFAULT_CLIENT_ID, "You chose: " + choice);
            player.setTookTurn(true);
            sendTurnStatus(player, true);

            if (allPlayersChose()) {
                processRPSRound();
            } else {
            
                int playersReady = (int) clientsInRoom.values().stream().filter(ServerThread::isReady).count();
                int playersChosen = playerChoice.size();
                sendGameEvent(String.format("PENDING: Waiting for choices... (%d/%d players have chosen)",
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
                sendGameEvent("Not enough players for Rock Paper Scissors Lizard Spock");
                clearChoicesAndEndRound();
                return;
            }

            List<Long> playerIds = new ArrayList<>(playerChoice.keySet());

            if (playerIds.size() == 2) {
    
                processMultiPlayerRPS(playerIds);
            }

            clearChoicesAndEndRound();

        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("processRPSRound exception", e);
            clearChoicesAndEndRound();
        }
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
        for (Choice choice : Choice.values()) {
            if (choiceGroups.containsKey(choice)) {
                int count = choiceGroups.get(choice).size();
                choiceAnnouncement.append(String.format("%s(%d) ", choice, count));
            }
        }
        sendGameEvent(choiceAnnouncement.toString());
    
        // Show individual player choices too
        StringBuilder playerChoices = new StringBuilder("Players: ");
        for (Long playerId : playerIds) {
            String playerName = clientsInRoom.get(playerId).getDisplayName();
            Choice choice = playerChoice.get(playerId);
            playerChoices.append(String.format("%s=%s ", playerName, choice));
        }
        sendGameEvent(playerChoices.toString());

        // Determine winners for multiplayer
        List<Choice> presentChoices = new ArrayList<>(choiceGroups.keySet());
    
        if (presentChoices.size() == 1) {
            Choice unanimousChoice = presentChoices.get(0);
            sendGameEvent(String.format(" Everyone chose %s - it's a tie!", unanimousChoice));
        } else if (presentChoices.size() >= 3) {
            // With 5 choices, having 3+ different choices usually results in ties
            sendGameEvent(" Too many different choices - it's a tie!");
        } else if (presentChoices.size() == 2) {
            // Two choices present - determine winner
            Choice choice1 = presentChoices.get(0);
            Choice choice2 = presentChoices.get(1);
    
            Choice winningChoice = isWinningChoice(choice1, choice2) ? choice1 : choice2;
            Choice losingChoice = winningChoice == choice1 ? choice2 : choice1;
            
            List<Long> winners = choiceGroups.get(winningChoice);
            List<Long> losers = choiceGroups.get(losingChoice);
    
            // Award points to winners
            for (Long winnerId : winners) {
                ServerThread winner = clientsInRoom.get(winnerId);
                winner.changePoints(1);
                sendPlayerPoints(winner);
            }

            String action = getWinningAction(winningChoice, losingChoice);
            String winnerNames = winners.stream()
                    .map(id -> clientsInRoom.get(id).getDisplayName())
                    .collect(Collectors.joining(", "));
            
            String loserNames = losers.stream()
                    .map(id -> clientsInRoom.get(id).getDisplayName())
                    .collect(Collectors.joining(", "));

            sendGameEvent(String.format(" %s %s %s!", winningChoice, action, losingChoice));
            sendGameEvent(String.format("Winners (%d): %s", winners.size(), winnerNames));
            sendGameEvent(String.format("Losers (%d): %s", losers.size(), loserNames));
        }
    }

    private void clearChoicesAndEndRound() {
        // NEW: Store current choices as "last choices" for next round cooldown
        for (Map.Entry<Long, Choice> entry : playerChoice.entrySet()) {
            playerLastChoice.put(entry.getKey(), entry.getValue());
        }
        
        playerChoice.clear(); // Clear current round choices
        onRoundEnd();
    }

    private boolean isWinningChoice(Choice choice1, Choice choice2) {
        return (choice1 == Choice.ROCK && choice2 == Choice.SCISSORS) ||
                (choice1 == Choice.ROCK && choice2 == Choice.LIZARD) ||
                (choice1 == Choice.PAPER && choice2 == Choice.ROCK) ||
                (choice1 == Choice.PAPER && choice2 == Choice.SPOCK) ||
                (choice1 == Choice.SCISSORS && choice2 == Choice.PAPER) ||
                (choice1 == Choice.SCISSORS && choice2 == Choice.LIZARD) ||
                (choice1 == Choice.LIZARD && choice2 == Choice.PAPER) ||
                (choice1 == Choice.LIZARD && choice2 == Choice.SPOCK) ||
                (choice1 == Choice.SPOCK && choice2 == Choice.SCISSORS) ||
                (choice1 == Choice.SPOCK && choice2 == Choice.ROCK);
    }

    private String getWinningAction(Choice winner, Choice loser) {
        switch (winner) {
            case ROCK:
                if (loser == Choice.SCISSORS) return "crushes";
                if (loser == Choice.LIZARD) return "crushes";
                break;
            case PAPER:
                if (loser == Choice.ROCK) return "covers";
                if (loser == Choice.SPOCK) return "disproves";
                break;
            case SCISSORS:
                if (loser == Choice.PAPER) return "cuts";
                if (loser == Choice.LIZARD) return "decapitates";
                break;
            case LIZARD:
                if (loser == Choice.PAPER) return "eats";
                if (loser == Choice.SPOCK) return "poisons";
                break;
            case SPOCK:
                if (loser == Choice.SCISSORS) return "smashes";
                if (loser == Choice.ROCK) return "vaporizes";
                break;
        }
        return "beats"; // fallback
    }

    // NEW: Get player's blocked choice for UI feedback
    public Choice getPlayerBlockedChoice(long clientId) {
        return playerLastChoice.get(clientId);
    }

    // NEW: Check if cooldowns are enabled
    public boolean isCooldownEnabled() {
        return cooldownEnabled;
    }
}