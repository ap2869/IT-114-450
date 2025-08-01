package Project.Sandbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder ;
 

public class PlayViewSandbox {
    // CardUI - Power-up cards
    public static class CardUI extends JButton {
        public CardUI(Card card, Consumer<Card> onSelect) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(new EmptyBorder(8, 8, 8, 8));
            setToolTipText(card.getDescription());

            setText(String.format(
                    "<html><div style='width:100%%; white-space:normal;'><ul style='margin:0;padding:0;list-style:none;'>"
                            + "<li><b>Name:</b> %s</li>"
                            + "<li><b>Type:</b> %s</li>"
                            + "<li><b>Effect:</b> %s</li>"
                            + "<li><b>Uses:</b> Single</li>"
                            + "</ul></div></html>",
                    card.getValue(),
                    card.getType().name(),
                    card.getDescription()));
            addActionListener(_ -> {
                if (onSelect != null) {
                    onSelect.accept(card);
                }
            });
            int cardWidth = 140;
            int cardHeight = 100;
            setPreferredSize(new Dimension(cardWidth, cardHeight));
            setMaximumSize(new Dimension(cardWidth, cardHeight));
            setMinimumSize(new Dimension(cardWidth, cardHeight));
            setAlignmentY(TOP_ALIGNMENT);
        }

        public void removeListeners() {
            for (ActionListener al : getActionListeners()) {
                removeActionListener(al);
            }
        }
    }

    // MoveUI - Replaces CellUI for Rock/Paper/Scissors selection
    public static class MoveUI extends JPanel {
        private Move move;
        private boolean isSelected = false;
        private JLabel moveLabel;
        private JLabel statusLabel;

        public MoveUI(Move move, Consumer<Move> onClick) {
            this.move = move;
            setBackground(Color.LIGHT_GRAY);
            setBorder(new LineBorder(Color.DARK_GRAY, 2));
            setLayout(new BorderLayout());

            moveLabel = new JLabel();
            statusLabel = new JLabel(" "); // Empty space initially
            
            add(moveLabel, BorderLayout.CENTER);
            add(statusLabel, BorderLayout.SOUTH);
            
            refresh();
            
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onClick != null) {
                        onClick.accept(move);
                    }
                }
            });
        }

        public void setSelected(boolean selected) {
            this.isSelected = selected;
            refresh();
        }

        public void setStatus(String status) {
            statusLabel.setText("<html><center><small>" + status + "</small></center></html>");
            repaint();
        }

        private void refresh() {
            String emoji = getEmoji(move);
            String text = String.format("<html><center><div style='font-size:20px;'>%s</div><div><b>%s</b></div></center></html>", 
                                       emoji, move.name());
            moveLabel.setText(text);

            if (isSelected) {
                setBackground(Color.GREEN);
            } else {
                setBackground(Color.LIGHT_GRAY);
            }
            
            repaint();
        }

        private String getEmoji(Move move) {
            switch (move) {
                case ROCK: return "🗿";
                case PAPER: return "📄";
                case SCISSORS: return "✂️";
                default: return "❓";
            }
        }
    }

    // GameAreaUI - Replaces GridUI for move selection
    public static class GameAreaUI extends JPanel {
        private MoveUI[] moveButtons;
        private final Consumer<Move> onMoveClick;
        private JPanel container = new JPanel();
        private JLabel gameStatusLabel;
        private JLabel scoreLabel;
        private int playerScore = 0;
        private int aiScore = 0;
        private int roundNumber = 1;

        public GameAreaUI(Consumer<Move> onMoveClick) {
            this.onMoveClick = onMoveClick;
            this.setLayout(new BorderLayout());
            
            // Status panel at top
            JPanel statusPanel = new JPanel(new GridLayout(2, 1));
            gameStatusLabel = new JLabel("<html><center><h2>Round 1 - Choose Your Move!</h2></center></html>");
            scoreLabel = new JLabel("<html><center><b>Score: You 0 - AI 0</b></center></html>");
            statusPanel.add(gameStatusLabel);
            statusPanel.add(scoreLabel);
            this.add(statusPanel, BorderLayout.NORTH);
            
            this.add(container, BorderLayout.CENTER);
            generateMoveGrid();
        }

        private void generateMoveGrid() {
            container.removeAll();
            moveButtons = new MoveUI[3];
            container.setLayout(new GridLayout(1, 3, 10, 10));
            container.setBorder(new EmptyBorder(20, 20, 20, 20));
            
            Dimension preferredSize = new Dimension(120, 100);
            Move[] moves = {Move.ROCK, Move.PAPER, Move.SCISSORS};
            
            for (int i = 0; i < 3; i++) {
                MoveUI moveUI = new MoveUI(moves[i], this::handleMoveClick);
                moveUI.setPreferredSize(preferredSize);
                moveButtons[i] = moveUI;
                container.add(moveUI);
            }
            
            container.revalidate();
            container.repaint();
            this.revalidate();
            this.repaint();
        }

        private void handleMoveClick(Move selectedMove) {
            // Clear previous selections
            for (MoveUI moveUI : moveButtons) {
                moveUI.setSelected(false);
                moveUI.setStatus(" ");
            }
            
            // Highlight selected move
            for (MoveUI moveUI : moveButtons) {
                if (moveUI.move == selectedMove) {
                    moveUI.setSelected(true);
                    moveUI.setStatus("SELECTED");
                    break;
                }
            }
            
            if (onMoveClick != null) {
                onMoveClick.accept(selectedMove);
            }
        }

        public void showRoundResult(Move playerMove, Move aiMove, String result) {
            // Show what AI chose
            for (MoveUI moveUI : moveButtons) {
                if (moveUI.move == aiMove) {
                    moveUI.setStatus("AI CHOSE");
                }
            }

            // Update game status
            gameStatusLabel.setText(String.format("<html><center><h2>%s</h2><p>You: %s | AI: %s</p></center></html>", 
                                                  result, playerMove.name(), aiMove.name()));

            // Update scores
            if (result.contains("Win")) {
                playerScore++;
            } else if (result.contains("Lose")) {
                aiScore++;
            }
            updateScoreDisplay();
        }

        public void nextRound() {
            roundNumber++;
            gameStatusLabel.setText(String.format("<html><center><h2>Round %d - Choose Your Move!</h2></center></html>", roundNumber));
            
            // Clear selections and statuses
            for (MoveUI moveUI : moveButtons) {
                moveUI.setSelected(false);
                moveUI.setStatus(" ");
            }
        }

        private void updateScoreDisplay() {
            scoreLabel.setText(String.format("<html><center><b>Score: You %d - AI %d</b></center></html>", 
                                            playerScore, aiScore));
        }

        public int getPlayerScore() {
            return playerScore;
        }

        public int getAiScore() {
            return aiScore;
        }

        public boolean isGameOver() {
            return playerScore >= 3 || aiScore >= 3; // Best of 5
        }
    }

    // HandUI - Power-up cards (unchanged structure)
    public static class HandUI extends JPanel {
        private final HashMap<String, CardUI> cards;
        private final Consumer<Card> onCardSelect;
        private final JPanel cardPanel;

        public HandUI(List<Card> cardList, Consumer<Card> onCardSelect) {
            super(new BorderLayout());
            this.onCardSelect = onCardSelect;
            cards = new HashMap<>();

            JLabel title = new JLabel("<html><center><b>Power-Up Cards</b></center></html>");
            add(title, BorderLayout.NORTH);

            cardPanel = new JPanel(new GridBagLayout());
            cardPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

            JScrollPane scrollPane = new JScrollPane(cardPanel,
                    JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                    JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

            scrollPane.setBorder(null);
            add(scrollPane, BorderLayout.CENTER);

            updateCards(cardList);
        }

        public void updateCards(List<Card> cardList) {
            cards.keySet().removeIf(id -> {
                boolean willRemove = cardList.stream().noneMatch(c -> c.getId().equals(id));
                if (willRemove) {
                    CardUI cardView = cards.get(id);
                    if (cardView != null)
                        cardView.removeListeners();
                }
                return willRemove;
            });
            cardPanel.removeAll();

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.VERTICAL;
            gbc.weighty = 1.0;

            int hGap = 8;
            for (int i = 0; i < cardList.size(); i++) {
                Card card = cardList.get(i);
                CardUI cardView = cards.get(card.getId());
                if (cardView == null) {
                    cardView = new CardUI(card, this::handleCardSelection);
                    cards.put(card.getId(), cardView);
                }
                gbc.gridx = i;
                gbc.insets = new Insets(0, i == 0 ? 0 : hGap, 0, 0);
                cardPanel.add(cardView, gbc);
            }

            cardPanel.revalidate();
            cardPanel.repaint();
        }

        private void handleCardSelection(Card card) {
            System.out.println("Selected power-up card: " + card.getId());
            if (onCardSelect != null)
                onCardSelect.accept(card);
        }
    }

    // PlayView (Main Panel)
    public static class PlayView extends JPanel {
        private final JPanel buttonPanel = new JPanel();
        private final GameAreaUI gameArea;
        private final HandUI handUI;
        private Move selectedMove;
        private Card selectedCard;
        private Random random = new Random();

        public PlayView(List<Card> mockCards) {
            this.setLayout(new BorderLayout());
            buttonPanel.setLayout(new BorderLayout());
            JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
            splitPane.setResizeWeight(0.7);
            splitPane.setDividerLocation(0.7);
            splitPane.setOneTouchExpandable(false);
            splitPane.setEnabled(false);

            gameArea = new GameAreaUI(this::handleMoveSelection);
            splitPane.setTopComponent(gameArea);

            handUI = new HandUI(mockCards, this::handleCardSelection);
            splitPane.setBottomComponent(handUI);
            buttonPanel.add(splitPane, BorderLayout.CENTER);

            this.add(buttonPanel, BorderLayout.CENTER);
        }

        private void handleCardSelection(Card card) {
            selectedCard = card;
            String message = String.format("Use power-up '%s'?\n\nEffect: %s", 
                                          card.getValue(), card.getDescription());
            if (confirmSelection(message)) {
                System.out.printf("[RPS] Used power-up: %s\n", card.getValue());
                // Apply card effect (in real game, this would modify game state)
                resetSelections();
            }
        }

        private void handleMoveSelection(Move move) {
            selectedMove = move;
            processRound();
        }

        private void processRound() {
            if (selectedMove != null) {
                // Generate AI move
                Move aiMove = Move.values()[random.nextInt(Move.values().length)];
                
                // Determine winner
                String result = determineWinner(selectedMove, aiMove);
                
                // Show result in game area
                gameArea.showRoundResult(selectedMove, aiMove, result);
                
                // Show result dialog
                String message = String.format("Round Result:\n\nYou played: %s\nAI played: %s\n\n%s", 
                                              selectedMove.name(), aiMove.name(), result);
                JOptionPane.showMessageDialog(this, message, "Round Result", JOptionPane.INFORMATION_MESSAGE);

                // Check for game end
                if (gameArea.isGameOver()) {
                    endGame();
                } else {
                    gameArea.nextRound();
                }

                resetSelections();
            }
        }

        private String determineWinner(Move playerMove, Move aiMove) {
            if (playerMove == aiMove) {
                return "It's a Tie!";
            }

            switch (playerMove) {
                case ROCK:
                    return (aiMove == Move.SCISSORS) ? "You Win!" : "You Lose!";
                case PAPER:
                    return (aiMove == Move.ROCK) ? "You Win!" : "You Lose!";
                case SCISSORS:
                    return (aiMove == Move.PAPER) ? "You Win!" : "You Lose!";
                default:
                    return "Error!";
            }
        }

        private void endGame() {
            int playerScore = gameArea.getPlayerScore();
            int aiScore = gameArea.getAiScore();
            
            String message;
            if (playerScore > aiScore) {
                message = String.format("🎉 Congratulations! You won!\n\nFinal Score: You %d - AI %d", 
                                       playerScore, aiScore);
            } else {
                message = String.format("😞 Game Over! AI won!\n\nFinal Score: You %d - AI %d", 
                                       playerScore, aiScore);
            }
            
            JOptionPane.showMessageDialog(this, message, "Game Over", JOptionPane.INFORMATION_MESSAGE);
        }

        private boolean confirmSelection(String message) {
            return JOptionPane.showConfirmDialog(
                    this,
                    message,
                    "Confirm Action",
                    JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
        }

        private void resetSelections() {
            selectedMove = null;
            selectedCard = null;
        }
    }

    // MAIN
    public static void main(String[] args) {
        // Mock power-up cards for RPS
        List<Card> mockCards = Arrays.asList(
                new Card("auto_win", CardType.FORCE_WIN, 1.0f, "Automatically win the next round"),
                new Card("counter_play", CardType.COUNTER_MOVE, 1.0f, "Your move becomes the counter to opponent's"),
                new Card("force_tie", CardType.FORCE_TIE, 1.0f, "Force the next round to be a tie"),
                new Card("double_win", CardType.DOUBLE_DAMAGE, 2.0f, "Next win counts as 2 points"),
                new Card("reverse", CardType.REVERSE_RULES, 1.0f, "Reverse RPS rules for one round"));

        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Rock Paper Scissors Tournament");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 500);
            PlayView playView = new PlayView(mockCards);
            frame.setContentPane(playView);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}



