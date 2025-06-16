package M3;

/*
Challenge 1: Command-Line Calculator
------------------------------------
- Accept two number1s and an operator as command-line arguments
- Supports addition (+) and subtraction (-)
- Allow integer and floating-point number1s
- Ensures correct decimal places in output based on input (e.g., 0.1 + 0.2 → 1 decimal place)
- Display an error for invalid inputs or unsupported operators
- Capture 5 variations of tests
*/

public class CommandLineCalculator extends BaseClass {
    private static String ucid = "ap2869"; // <-- change to your ucid

    public static void main(String[] args) {
        printHeader(ucid, 1, "Objective: Implement a calculator using command-line arguments.");

        if (args.length != 3) {
            System.out.println("Usage: java M3.CommandLineCalculator <num1> <operator> <num2>");
            printFooter(ucid, 1);
            return;
        }

        try {
            System.out.println("Calculating result...");


            // ap2869 june 16, 2025

              
            String number1 = args[0];  
            String operator = args[1];       
            String number2 = args[2];   
            double num1 = Double.parseDouble(number1);     
            double num2 = Double.parseDouble(number2);
            double result = 0;

  
            if (!operator.equals("+")) {
                if (!operator.equals("-")){
                    
                    System.out.println("Error: Please use only + or -");
                    }
                    
                }
                
            if (operator.equals("+")) {
                result = num1 + num2;}
                else if (operator.equals("-")) {
                    result = num1 - num2;
                        }

            if (number1.contains(".")) {
                num1 = number1.length() - number1.indexOf('.') - 1;
                }

            if (number2.contains(".")) {
                num2 = number2.length() - number2.indexOf('.') - 1;
                }
                System.out.println("result: " + (result));

        } catch (Exception e) {
            System.out.println("Invalid input. Please ensure correct format and valid numbers.");
        }

        printFooter(ucid, 1);
    }
}
