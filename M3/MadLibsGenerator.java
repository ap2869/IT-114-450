package M3;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/*
Challenge 3: Mad Libs Generator (Randomized Stories)
-----------------------------------------------------
- Load a **random** story from the "stories" folder
- Extract **each storyLine** into a collection (i.e., ArrayList)
- Prompts user for each placeholder (i.e., <adjective>) 
    - Any word the user types is acceptable, no need to verify if it matches the placeholder type
    - Any placeholder with underscores should display with spaces instead
- Replace placeholders with user input (assign back to original slot in collection)
*/

public class MadLibsGenerator extends BaseClass {
    private static final String STORIES_FOLDER = "M3/stories";
    private static String ucid = "ap2869"; // <-- change to your ucid

    public static void main(String[] args) {
        printHeader(ucid, 3,
                "Objective: Implement a Mad Libs generator that replaces placeholders dynamically.");

        Scanner scanner = new Scanner(System.in);
        File folder = new File(STORIES_FOLDER);

        if (!folder.exists() || !folder.isDirectory() || folder.listFiles().length == 0) {
            System.out.println("Error: No stories found in the 'stories' folder.");
            printFooter(ucid, 3);
            scanner.close();
            return;
        }
        List<String> lines = new ArrayList<>();
        
        // ap2869 june 16, 2025 
     
        File[] stories = folder.listFiles();
        
        int randomStory = (int)(Math.random() * stories.length);
        File story1 = stories[randomStory];

       
        System.out.println("Story Picked: " + story1.getName());
        
        try {

            Scanner reader = new Scanner(story1);        
          
            while (reader.hasNextLine()) {
                String storyLine = reader.nextLine();
                lines.add(storyLine);
                }

                reader.close();
                } catch (Exception e) {
                    System.out.println("Story not found:");
                    }

                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);

                        int start = line.indexOf('<');
                        int end = line.indexOf('>');
                        
                        while (start != -1 && end != -1 && end > start) {

                            String placeholder = line.substring(start + 1, end);                    
                            
                            String prompt = placeholder.replace("_", " ");                    
                      
                            System.out.println("Please write a " + prompt + ": ");
                            String word = scanner.nextLine();                    
                           
                            String replacement = line.substring(start, end + 1);                    
                           
                            line = line.replaceFirst(replacement, word);                    
                          
                            start = line.indexOf('<');
                            end = line.indexOf('>');
                        }              
                    
                        lines.set(i, line);
                    }
                                    
        System.out.println("\nYour Completed Mad Libs Story:\n");
        StringBuilder finalStory = new StringBuilder();
        for (String storyLine : lines) {
            finalStory.append(storyLine).append("\n");
        }
        System.out.println(finalStory.toString());

        printFooter(ucid, 3);
        scanner.close();
    }
}
