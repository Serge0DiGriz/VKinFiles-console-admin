import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Exception {
        MessageHandler handler = new MessageHandler();
        System.out.println(handler.getUsersInfo() + "\n");

        Scanner scan = new Scanner(System.in);
        System.out.print("Введите id: ");
        int id = scan.nextInt();

        if (handler.selectUser(id)) {
            System.out.println("Successfully authorization!");
            handler.downloadAll();
        } else System.out.println("Wrong id!");
    }
}
