import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Player extends Application {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("What is your name?");
        Player player = new Player(scanner.nextLine());
        player.connectToServer();
        launch(args);
        scanner.close();
    }

    private static TextArea gameArea;
    private static TextField gameField;
    private static TextArea chatArea;
    private static TextField chatField;
    private static volatile boolean chatTyped;
    private static String chatString;
    private static List<String> chatAreaStrings;
    private static volatile boolean gameTyped;
    private static String gameString;
    private static List<String> gameAreaStrings;
    private static Stage stage;

    private static String name;
    private static Sender sender;
    private static Receiver receiver;
    private static String actionPlayerName;
    private static Socket socket;
    private static Thread senderThread;
    private static Thread receiverThread;

    public void start(Stage primaryStage) {
        Pane pane = new Pane();
        stage = primaryStage;

        gameArea.setPrefWidth(280);
        gameArea.setPrefHeight(185);
        gameArea.setLayoutX(10);
        gameArea.setLayoutY(10);

        gameField.setLayoutX(10);
        gameField.setLayoutY(200);
        gameField.setPrefWidth(280);

        gameField.setOnAction(actionEvent -> {
            gameString = gameField.getText();
            gameTyped = true;
            gameField.setText(null);
        });

        chatArea.setPrefWidth(190);
        chatArea.setPrefHeight(125);
        chatArea.setLayoutX(300);
        chatArea.setLayoutY(10);

        chatField.setLayoutX(300);
        chatField.setLayoutY(140);
        chatField.setPrefWidth(190);

        chatField.setOnAction(actionEvent -> {
            chatString = chatField.getText();
            chatTyped = true;
            chatField.setText(null);
            if(chatString.equals("leave")) stage.close();
        });

        pane.getChildren().addAll(gameArea, gameField, chatArea, chatField);

        Scene scene = new Scene(pane, 500, 230);
        stage.setScene(scene);
        stage.setResizable(false);
        stage.setTitle("Dominion - " + name);

        stage.show();
        stage.setOnCloseRequest((event) -> {
            try {
                sender.sendNextInstruction(Instruction.LEAVE);
                sender.closeConnection();
                senderThread.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
            stage.close();
        });

    }

    public Player() {
    }

    private static List<Card> hand;
    private static List<Card> deck;
    private static List<Card> discardPile;
    private static int handLimit;
    private static int numActions;
    private static int numBuys;
    private static int handPurchasePower;
    private static int amountSpentThisTurn;
    private static int bonusPurchasePower;
    private static boolean gameOver;
    private static boolean myTurn;
    private static boolean gameAreaDisabled;

    public Player(String name) {
        gameArea = new TextArea();
        gameField = new TextField();
        chatArea = new TextArea();
        chatField = new TextField();
        chatTyped = false;
        chatString = "";
        chatAreaStrings = new ArrayList<>();
        gameTyped = false;
        gameString = "";
        gameAreaStrings = new ArrayList<>();

        Player.name = name;
        actionPlayerName = "*@#$%";

        hand = new ArrayList<>();
        deck = new ArrayList<>();
        discardPile = new ArrayList<>();
        handLimit = 5;
        numActions = 1;
        numBuys = 1;
        handPurchasePower = 0;
        amountSpentThisTurn = 0;
        bonusPurchasePower = 0;
        gameOver = false;
        myTurn = false;
        gameAreaDisabled = false;
    }

    public static void gameOverStuff() {
        gameArea.setText("The game is over\nYou had: " + 0 + " points");
        gameAreaDisabled = true;
    }

    public void connectToServer() {
        try {
            socket = new Socket("localhost", 52567);
            sender = new Sender();
            receiver = new Receiver();
        } catch (IOException ex) {
            System.out.println("exception @ connectToServer");
        }
    }

    public static class Sender implements Runnable {

        private ObjectOutputStream dataOut;

        public Sender() {
            try {
                dataOut = new ObjectOutputStream(socket.getOutputStream());
                senderThread = new Thread(this);
                senderThread.start();

            } catch (IOException ex) {
                System.out.println("IO Exception from sender constructor");
            }
        }

        public void run() {
            try {
                sendNextInstruction(Instruction.DEALCARDS);
                sendNextInstruction(Instruction.BEGINCHAT);
                sendNextInstruction(Instruction.NAME);
                sendMessage(name);

                String s = "";
                while (!s.equals("leave")) {
                    while (!gameTyped && !chatTyped) {
                        if (gameOver && !gameAreaDisabled) {
                            gameOverStuff();
                        }
                        Thread.sleep(50);
                    }
                    if (gameTyped) {
                        gameTyped = false;
//                        s = gameString;
//                        if (s.contains("CN -")) {
//                            sendNextInstruction(Instruction.GAMENAME);
//                            s = s.substring(5);
//                            sendMessage(s);
//                        } else {
//                            sendNextInstruction(Instruction.GAMEMESSAGE);
//                            sendMessage(s);
//                        }
                    } else if (chatTyped) {
                        chatTyped = false;
                        s = chatString;
                        if (s.contains("CN -")) {
                            sendNextInstruction(Instruction.NAME);
                            s = s.substring(5);
                            sendMessage(s);
                        } else if (s.equals("leave")) {
                            sendNextInstruction(Instruction.LEAVE);
                            break;
                        } else {
                            sendNextInstruction(Instruction.MESSAGE);
                            sendMessage(s);
                        }
                    }
                }
                closeConnection();
            } catch (IOException | InterruptedException ex) {
                System.out.println("IOException at sender run()");
            }
        }

        public void sendMessage(String s) throws IOException {
            dataOut.writeUTF(s);
            dataOut.flush();
        }
        public void sendNextInstruction(Instruction instruction) throws IOException {
            dataOut.writeObject(instruction);
            dataOut.flush();
        }
        public void closeConnection() {
            System.out.println("Chat connection closed in Sender");
        }
    }

    public static class Receiver implements Runnable {

        private ObjectInputStream dataIn;

        public Receiver() {
            try {
                dataIn = new ObjectInputStream(socket.getInputStream());
                receiverThread = new Thread(this);
                receiverThread.start();

            } catch (IOException ex) {
                System.out.println("IO Exception from receiver constructor");
            }
        }

        public void run() {
            try {

                while (true) {
                    Instruction nextInstruction = getNextInstruction();
                    actionPlayerName = receivePlayerName();
                    gameOver = receiveGameStatus();
                    if (nextInstruction == Instruction.MESSAGE || nextInstruction == Instruction.NAME) {
                        chatString = (receiveMessage());
                        chatAreaStrings.add(chatString + "\n");
                        StringBuilder s = new StringBuilder();
                        if (chatAreaStrings.size() > 4) chatAreaStrings.remove(0);
                        for (String string : chatAreaStrings) {
                            s.append(string);
                        }
                        chatArea.setText(s.toString());
                    }
//                    else if (nextInstruction == Instruction.GAMEMESSAGE || nextInstruction == Instruction.GAMENAME) {
//                        gameString = (receiveMessage());
//                        gameAreaStrings.add(gameString + "\n");
//                        String s = "";
//                        if (gameAreaStrings.size() > 7) gameAreaStrings.remove(0);
//                        for (String string : gameAreaStrings) {
//                            s += string;
//                        }
//                        gameArea.setText(s);
//                    }
                    else if (nextInstruction == Instruction.BEGINCHAT){
                        chatAreaStrings.add("-----This is the Chat-----\ntype \"leave\" to exit the chat\ntype \"CN - \" followed by the your new name to change it\n");
                        StringBuilder s = new StringBuilder();
                        if (chatAreaStrings.size() > 4) chatAreaStrings.remove(0);
                        for (String string : chatAreaStrings) {
                            s.append(string);
                        }
                        chatArea.setText(s.toString());
                    } else if (nextInstruction == Instruction.DEALCARDS) {
                        deck = receiveDeck();
                        System.out.println(deck);
                    }

                    if(!name.equals(actionPlayerName)) {
                        if (nextInstruction==Instruction.LEAVE){
                            chatString = (receiveMessage());
                            chatAreaStrings.add(chatString + "\n");
                            StringBuilder s = new StringBuilder();
                            if (chatAreaStrings.size() > 4) chatAreaStrings.remove(0);
                            for (String string : chatAreaStrings) {
                                s.append(string);
                            }
                            chatArea.setText(s.toString());
                        }
                    }
                    if(name.equals(actionPlayerName) && nextInstruction==Instruction.LEAVE) {
                        receiveMessage();
                        break;
                    }
                }
                closeConnection();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        public boolean receiveGameStatus() throws IOException {
            return dataIn.readBoolean();
        }
        public List<Card> receiveDeck() throws IOException, ClassNotFoundException {
            return (List<Card>) dataIn.readObject();
        }
        public String receivePlayerName() throws IOException {
            return dataIn.readUTF();
        }
        public String receiveMessage() throws IOException {
            return dataIn.readUTF();
        }
        public Instruction getNextInstruction() throws IOException, ClassNotFoundException {
            return (Instruction) dataIn.readObject();
        }

        public void closeConnection() {
            try {
                socket.close();
                System.out.println("receiver socket closed");
            } catch (IOException ex) {
                System.out.println("IOException at receiver closeConnection()");
            }
        }
    }

}