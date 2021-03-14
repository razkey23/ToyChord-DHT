import java.io.File;  // Import the File class
import java.io.FileNotFoundException;  // Import this class to handle errors
import java.util.Scanner; // Import the Scanner class to read text files

public class Temp {
  public static void main(String[] args) {
    try {
      File myObj = new File("../../../../transactions/insert.txt");
      Scanner myReader = new Scanner(myObj);
      System.out.println("OK");
      myReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
  }
}