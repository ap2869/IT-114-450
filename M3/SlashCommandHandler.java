package M3;

/*
Challenge 2: Simple Slash Command Handler
-----------------------------------------
- Accept user input as slash commands
  - "/greet <name>" → Prints "Hello, <name>!"
  - "/roll <num>d<sides>" → Roll <num> dice with <sides> and returns a single outcome as "Rolled <num>d<sides> and got <result>!"
  - "/echo <message>" → Prints the message back
  - "/quit" → Exits the program
- Commands are case-insensitive
- Print an error for unrecognized commands
- Print errors for invalid command formats (when applicable)
- Capture 3 variations of each command except "/quit"
*/

import java.util.Scanner;

public class SlashCommandHandler extends BaseClass {
    private static String ucid = "ap2869"; // <-- change to your UCID

    public static void main(String[] args) {
        printHeader(ucid, 2, "Objective: Implement a simple slash command parser.");

        Scanner scanner = new Scanner(System.in);

        // ap2869 june 16, 2025 

        // Can define any variables needed here

        while (true) {

            System.out.print("Enter command: ");
            String input = scanner.nextLine();

            if (input.isEmpty()) {
                System.out.println("Type a command /greet, /roll, /echo or /quit");
               
            }

            String command = input.toLowerCase();

           

            if (command.startsWith("/echo")){
               
                    System.out.println("Please enter message: ");
                    String message = scanner.nextLine();
                    
                    if (message.isEmpty()) {
                        System.out.println("Error: u have entered an empty message.");
                    } else {
                        System.out.println("------------------------------");
                        System.out.println("Your Message is: " + message);
                    }
                
            }

            if (command.startsWith("/greet")){
                
                    System.out.println("Please enter name: ");
                    String greet = scanner.nextLine();
                    if (greet.isEmpty()) {
                        System.out.print("Error: Empty name entered!! ");
                    } else { 
                        System.out.println("------------------------------");
                        System.out.print("Hello: " + greet + " Welcome !");
                    }


                
            }

            if (command.startsWith("/roll")) {

                System.out.println("Please enter how many dice and sides (like 2d6):");           

               }

            if (command.equals("/quit")){
                System.out.println ("Thank you for playing. :) ");
                System.out.println("Goodbye.");
                
            }
            
            if (1 == 1) {
                System.out.println(" ");
                break;
            }
        }

        printFooter(ucid, 2);
        scanner.close();
    }
}
