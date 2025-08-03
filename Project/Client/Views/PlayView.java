package Project.Client.Views;

import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JPanel;

import Project.Client.Client;
import Project.Common.Phase;

public class PlayView extends JPanel {
    private final JPanel buttonPanel = new JPanel();

    public PlayView(String name){
        this.setName(name);

        // Create Rock, Paper, Scissors buttons using a cleaner approach
        String[] choices = {"Rock", "Paper", "Scissors", "Lizard", "Spock"};
        
        for (String choice : choices) {
            JButton button = new JButton(choice);
            button.addActionListener(_ -> {
                try {
                    Client.INSTANCE.sendDoTurn(choice.toLowerCase());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            buttonPanel.add(button);
        }
        
        this.add(buttonPanel);
    }
    
    public void changePhase(Phase phase){
        if (phase == Phase.READY) {
            buttonPanel.setVisible(false);
        } else if (phase == Phase.IN_PROGRESS) {
            buttonPanel.setVisible(true);
        }
    }
}