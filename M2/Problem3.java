package M2;

public class Problem3 extends BaseClass {
    private static Integer[] array1 = {42, -17, 89, -256, 1024, -4096, 50000, -123456};
    private static Double[] array2 = {3.14159265358979, -2.718281828459, 1.61803398875, -0.5772156649, 0.0000001, -1000000.0};
    private static Float[] array3 = {1.1f, -2.2f, 3.3f, -4.4f, 5.5f, -6.6f, 7.7f, -8.8f};
    private static String[] array4 = {"123", "-456", "789.01", "-234.56", "0.00001", "-99999999"};
    private static Object[] array5 = {-1, 1, 2.0f, -2.0d, "3", "-3.0"};
    private static void bePositive(Object[] arr, int arrayNumber) {
        // Only make edits between the designated "Start" and "End" comments
        printArrayInfo(arr, arrayNumber);

        // Challenge 1: Make each value positive
        // Challenge 2: Convert the values back to their original data type and assign it to the proper slot of the `output` array
        // Step 1: sketch out plan using comments (include ucid and date)
        // Step 2: Add/commit your outline of comments (required for full credit)
        // Step 3: Add code to solve the problem (add/commit as needed)
        Object[] output = new Object[arr.length];
        // Start Solution Edits -----------------------------------------------------

         // ap2869 - june 9, 2025 

         // get the numbers from the arrays 
         // if its not a number get the string(word)
         // take whats inside and store it
         // go through each array (int, flost,double,string)
         // once u have all from the array stored and assign to its proper output. 

         for (int i = 0; i < arr.length; i++) {
            Object num = arr[i];
    
            if (num instanceof Integer) {
                int number = (Integer) num;
                output[i] = (number < 0) ? -number : number;
            } else if (num instanceof Double) {
                double number = (Double) num;
                output[i] = (number < 0) ? -number : number;
            } else if (num instanceof Float) {
                float number = (Float) num;
                output[i] = (number < 0) ? -number : number;
            } else if (num instanceof String) {
                String word = (String) num;
             
                try {
                    double number = Double.parseDouble(word);
                    num = (number < 0) ? -number : num;
                    output[i] = String.valueOf(num);
                } catch (NumberFormatException e) {
                    output[i] = word; 
                }
            } else {
                output[i] = num; 
            }
        }

     
        // End Solution Edits -----------------------------------------------------
        System.out.println("Output: ");
        printOutputWithType(output);
       
        System.out.println("");
        System.out.println("______________________________________");
    }

    public static void main(String[] args) {
        final String ucid = "ap2869"; // <-- change to your UCID
        // no edits below this line
        printHeader(ucid, 3);
        bePositive(array1, 1);
        bePositive(array2, 2);
        bePositive(array3, 3);
        bePositive(array4, 4);
        bePositive(array5, 5);
        printFooter(ucid, 3);

    }
}