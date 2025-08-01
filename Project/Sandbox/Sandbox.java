package Project.Sandbox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class Sandbox {
    public static void main(String[] args) {
        // Create a tournament with multiple rounds
        Tournament tournament = new Tournament(5); // 5 rounds
        
        // Create deck of power-up cards
        Deck deck = new Deck();
        List<Card> playerHand = new ArrayList<>();
        
        // Draw 3 cards for the player
        for (int i = 0; i < 3; i++) {
            Card card = deck.drawCard();
            if (card != null) {
                playerHand.add(card);
                System.out.println("Drew card: " + card);
            }
        }
        
        Scanner scanner = new Scanner(System.in);
        
        // Play the tournament
        while (!tournament.isFinished()) {
            System.out.println("\n=== Round " + (tournament.getCurrentRound() + 1) + " ===");
            System.out.println("Your hand: " + playerHand);
            
            // Player chooses move
            Move playerMove = getPlayerMove(scanner);
            
            // Ask if player wants to use a card
            Card usedCard = null;
            if (!playerHand.isEmpty()) {
                System.out.println("Do you want to use a card? (y/n)");
                if (scanner.nextLine().toLowerCase().startsWith("y")) {
                    usedCard = chooseCard(playerHand, scanner);
                }
            }
            
            // AI opponent chooses move
            Move aiMove = Move.values()[(int) (Math.random() * Move.values().length)];
            
            // Play the round
            RoundResult result = tournament.playRound(playerMove, aiMove, usedCard);
            
            System.out.println("\nYou played: " + playerMove);
            System.out.println("AI played: " + aiMove);
            if (usedCard != null) {
                System.out.println("You used card: " + usedCard);
                playerHand.remove(usedCard);
            }
            System.out.println("Result: " + result);
            System.out.println("Current score - You: " + tournament.getPlayerScore() + 
                             ", AI: " + tournament.getAiScore());
        }
        
        // Tournament finished
        System.out.println("\n=== Tournament Finished ===");
        System.out.println("Final Score - You: " + tournament.getPlayerScore() + 
                         ", AI: " + tournament.getAiScore());
        
        if (tournament.getPlayerScore() > tournament.getAiScore()) {
            System.out.println("You won the tournament!");
        } else if (tournament.getAiScore() > tournament.getPlayerScore()) {
            System.out.println("AI won the tournament!");
        } else {
            System.out.println("Tournament ended in a tie!");
        }
        
        scanner.close();
    }
    
    private static Move getPlayerMove(Scanner scanner) {
        while (true) {
            System.out.println("Choose your move: (R)ock, (P)aper, (S)cissors");
            String input = scanner.nextLine().toUpperCase();
            
            switch (input) {
                case "R":
                case "ROCK":
                    return Move.ROCK;
                case "P":
                case "PAPER":
                    return Move.PAPER;
                case "S":
                case "SCISSORS":
                    return Move.SCISSORS;
                default:
                    System.out.println("Invalid input. Please try again.");
            }
        }
    }
    
    private static Card chooseCard(List<Card> hand, Scanner scanner) {
        System.out.println("Choose a card to use:");
        for (int i = 0; i < hand.size(); i++) {
            System.out.println((i + 1) + ". " + hand.get(i));
        }
        
        while (true) {
            try {
                int choice = Integer.parseInt(scanner.nextLine()) - 1;
                if (choice >= 0 && choice < hand.size()) {
                    return hand.get(choice);
                } else {
                    System.out.println("Invalid choice. Please try again.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }
}

class Tournament {
    private int totalRounds;
    private int currentRound;
    private int playerScore;
    private int aiScore;
    private List<RoundResult> history;
    
    public Tournament(int totalRounds) {
        this.totalRounds = totalRounds;
        this.currentRound = 0;
        this.playerScore = 0;
        this.aiScore = 0;
        this.history = new ArrayList<>();
    }
    
    public RoundResult playRound(Move playerMove, Move aiMove, Card powerUpCard) {
        if (isFinished()) {
            throw new IllegalStateException("Tournament is already finished");
        }
        
        RoundResult result = determineWinner(playerMove, aiMove, powerUpCard);
        history.add(result);
        
        switch (result) {
            case PLAYER_WIN:
                playerScore++;
                break;
            case AI_WIN:
                aiScore++;
                break;
            case TIE:
                // No score change
                break;
        }
        
        currentRound++;
        return result;
    }
    
    private RoundResult determineWinner(Move playerMove, Move aiMove, Card powerUpCard) {
        // Apply card effects
        Move effectivePlayerMove = playerMove;
        Move effectiveAiMove = aiMove;
        
        if (powerUpCard != null) {
            switch (powerUpCard.getType()) {
                case FORCE_WIN:
                    return RoundResult.PLAYER_WIN;
                case FORCE_TIE:
                    return RoundResult.TIE;
                case COUNTER_MOVE:
                    // Player's move becomes the counter to AI's move
                    effectivePlayerMove = getCounterMove(aiMove);
                    break;
                case DOUBLE_DAMAGE:
                    // If player would win, they get 2 points instead of 1
                    RoundResult normalResult = compareMovesOnly(playerMove, aiMove);
                    if (normalResult == RoundResult.PLAYER_WIN) {
                        playerScore++; // Extra point
                    }
                    return normalResult;
                case REVERSE_RULES:
                    // Reverse the normal RPS rules
                    return compareMovesReversed(playerMove, aiMove);
            }
        }
        
        return compareMovesOnly(effectivePlayerMove, effectiveAiMove);
    }
    
    private RoundResult compareMovesOnly(Move playerMove, Move aiMove) {
        if (playerMove == aiMove) {
            return RoundResult.TIE;
        }
        
        switch (playerMove) {
            case ROCK:
                return (aiMove == Move.SCISSORS) ? RoundResult.PLAYER_WIN : RoundResult.AI_WIN;
            case PAPER:
                return (aiMove == Move.ROCK) ? RoundResult.PLAYER_WIN : RoundResult.AI_WIN;
            case SCISSORS:
                return (aiMove == Move.PAPER) ? RoundResult.PLAYER_WIN : RoundResult.AI_WIN;
            default:
                return RoundResult.TIE;
        }
    }
    
    private RoundResult compareMovesReversed(Move playerMove, Move aiMove) {
        if (playerMove == aiMove) {
            return RoundResult.TIE;
        }
        
        // Reversed rules: Rock loses to Paper, Paper loses to Scissors, Scissors loses to Rock
        switch (playerMove) {
            case ROCK:
                return (aiMove == Move.PAPER) ? RoundResult.PLAYER_WIN : RoundResult.AI_WIN;
            case PAPER:
                return (aiMove == Move.SCISSORS) ? RoundResult.PLAYER_WIN : RoundResult.AI_WIN;
            case SCISSORS:
                return (aiMove == Move.ROCK) ? RoundResult.PLAYER_WIN : RoundResult.AI_WIN;
            default:
                return RoundResult.TIE;
        }
    }
    
    private Move getCounterMove(Move opponentMove) {
        switch (opponentMove) {
            case ROCK:
                return Move.PAPER;
            case PAPER:
                return Move.SCISSORS;
            case SCISSORS:
                return Move.ROCK;
            default:
                return Move.ROCK;
        }
    }
    
    public boolean isFinished() {
        return currentRound >= totalRounds;
    }
    
    public int getCurrentRound() {
        return currentRound;
    }
    
    public int getPlayerScore() {
        return playerScore;
    }
    
    public int getAiScore() {
        return aiScore;
    }
    
    public List<RoundResult> getHistory() {
        return new ArrayList<>(history);
    }
}

enum Move {
    ROCK("Rock"),
    PAPER("Paper"),
    SCISSORS("Scissors");
    
    private final String displayName;
    
    Move(String displayName) {
        this.displayName = displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

enum RoundResult {
    PLAYER_WIN("You Win!"),
    AI_WIN("AI Wins!"),
    TIE("It's a Tie!");
    
    private final String displayName;
    
    RoundResult(String displayName) {
        this.displayName = displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}

class Deck {
    private List<Card> cards;

    public Deck() {
        this.cards = new ArrayList<>();
        
        // Create power-up cards programmatically
        // Force Win cards
        for (int i = 0; i < 2; i++) {
            cards.add(new Card("AUTO_WIN_" + (i+1), CardType.FORCE_WIN, 1.0f, 
                "Automatically win this round"));
        }
        
        // Force Tie cards
        for (int i = 0; i < 3; i++) {
            cards.add(new Card("FORCE_TIE_" + (i+1), CardType.FORCE_TIE, 1.0f, 
                "Force this round to be a tie"));
        }
        
        // Counter Move cards
        for (int i = 0; i < 4; i++) {
            cards.add(new Card("COUNTER_" + (i+1), CardType.COUNTER_MOVE, 1.0f, 
                "Your move becomes the counter to opponent's move"));
        }
        
        // Double Damage cards
        for (int i = 0; i < 2; i++) {
            cards.add(new Card("DOUBLE_" + (i+1), CardType.DOUBLE_DAMAGE, 2.0f, 
                "If you win, gain 2 points instead of 1"));
        }
        
        // Reverse Rules cards
        for (int i = 0; i < 1; i++) {
            cards.add(new Card("REVERSE_" + (i+1), CardType.REVERSE_RULES, 1.0f, 
                "Reverse the normal Rock Paper Scissors rules"));
        }

        Collections.shuffle(cards);
    }

    public Card drawCard() {
        if (cards.isEmpty()) {
            return null;
        }
        return cards.remove(cards.size() - 1); // Draw from top
    }

    public int getTotalCards() {
        return cards.size();
    }

    public boolean isEmpty() {
        return cards.isEmpty();
    }

    public void shuffle() {
        Collections.shuffle(cards);
    }
}

class Card {
    private String id;
    private CardType type;
    private float value;
    private String description;

    public Card(String id, CardType type, float value, String description) {
        this.id = id;
        this.type = type;
        this.value = value;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public CardType getType() {
        return type;
    }

    public float getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return String.format("%s: %s", id, description);
    }
}

enum CardType {
    FORCE_WIN,        // Automatically win the round
    FORCE_TIE,        // Force the round to be a tie
    COUNTER_MOVE,     // Your move becomes the counter to opponent's move
    DOUBLE_DAMAGE,    // If you win, gain 2 points instead of 1
    REVERSE_RULES     // Reverse the normal RPS rules for this round
}