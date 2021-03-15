import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.util.Scanner; // Import the Scanner class to read text files

public class Temp {
  public static void main(String[] args) {
    int i = 0;
        try {
            File myObj = new File("../../../../../transactions/insert.txt");
            Scanner myReader = new Scanner(myObj);
            while (myReader.hasNextLine()) {
                if (i == 10) break;
                i++;
                String[] data = myReader.nextLine().split(", ");
                
                    System.out.print(data[0]);
                    System.out.print("    hi"+ data[1]);
                //System.out.println();
            }
            myReader.close();
        } catch (FileNotFoundException e) {
            System.out.println("An error occurred.");
            e.printStackTrace();
        }
  }
}