import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import java.io.*;
import java.net.*;
import java.util.*;

public class Player extends Application {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("What is your name?");
        Player player = new Player(scanner.nextLine());
        player.connectToServer();
        launch(args);
        scanner.close();
    }

    private static TextArea gameArea, chatArea; private static TextField gameField, chatField;
    private static volatile boolean chatTyped, gameTyped;
    private static String chatString, gameString;
    private static List<String> chatAreaStrings, gameAreaStrings;
    private static Stage stage;

    private static String name, actionPlayerName;
    private static Sender sender;
    private static Receiver receiver;
    private static Socket socket;
    private static Thread chatSenderThread, gameSenderThread, receiverThread, javaFXThread;

    public void start(Stage primaryStage) {
        Pane pane = new Pane();
        stage = primaryStage;
        javaFXThread = Thread.currentThread();
        System.out.println("JavaFX Thread: " + Thread.currentThread());

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
                chatSenderThread.stop();
                gameSenderThread.stop();
            } catch (IOException e) {
                e.printStackTrace();
            }
            stage.close();
        });

    }

    public Player() {
    }

    private static List<Card> hand, deck, discardPile;
    private static int handLimit, numActions, numBuys, handPurchasePower, amountSpentThisTurn, bonusPurchasePower;
    private static boolean gameOver, myTurn, gameAreaDisabled;
    private static Phase phase;

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
        phase = Phase.ACTION;
    }

    public static void gameOverStuff() {
        gameArea.setText("The game is over\nYou had: " + getTotalPoints() + " points");
        gameAreaDisabled = true;
        Thread.currentThread().stop();
    }
    public static void showGameAreaText() {
        StringBuilder s = new StringBuilder();
        while (gameAreaStrings.size() > 4) gameAreaStrings.remove(0);
        for (String string : gameAreaStrings) {
            s.append(string);
        }
        gameArea.setText(s.toString());
    }
    public static void resetGameAreaStrings() {
        gameAreaStrings = new ArrayList<>();
        System.out.println(gameAreaStrings + " - gameAreaStrings");
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

    public static class Sender {

        private ObjectOutputStream dataOut;
        private GameSender gameSender;
        private ChatSender chatSender;

        public Sender() {
            try {
                dataOut = new ObjectOutputStream(socket.getOutputStream());
                gameSender = new GameSender();
                chatSender = new ChatSender();
            } catch (IOException ex) {
                System.out.println("IO Exception from sender constructor");
            }
        }

        public class GameSender implements Runnable {
            public GameSender() {
                gameSenderThread = new Thread(this);
                gameSenderThread.start();
            }

            public void run() {
                System.out.println("gameSender thread: " + Thread.currentThread());
                try {
                    while (!gameAreaDisabled) {
                        if (myTurn) {
                            newTurn();
                            resetGameAreaStrings();
                            boolean cardWasPlayed = false;
                            while(getNumActions()>0){
                                gameAreaStrings.add("Your hand:\n" + getHand() + "\nYou have " + getNumActions() +
                                        " action(s) remaining this turn\nWould you like to play an action card?\n");
                                showGameAreaText();
                                checkGameField();
                                if (gameString.equals("yes")) {
                                    System.out.println(gameString + " " + Thread.currentThread());
                                    gameAreaStrings.add("What card do you want to play?\n");
                                    showGameAreaText();
                                    checkGameField();
                                    System.out.println(gameTyped + " " + Thread.currentThread());
                                    for (Card card : getHand()) {
                                        if (card.getCardName().equals(gameString)) {
                                            if (card.isActionCard()) {
                                                performAction((ActionCard) card);
                                                cardWasPlayed = true;
                                                gameAreaStrings.add("Action successfully performed\n");
                                                showGameAreaText();
                                            }
                                            break;
                                        }
                                    }
                                    if (!cardWasPlayed) {
                                        gameAreaStrings.add("No action performed\n");
                                        showGameAreaText();
                                    }
                                    cardWasPlayed = false;
                                } else {
                                    myTurn = false;
//                                    Platform.runLater(() -> resetGameAreaStrings());
                                    resetGameAreaStrings();
                                    gameAreaStrings.add("It is not your turn\n");
                                    showGameAreaText();
                                    break;
                                }
                            }
/*
                            boolean cardWasPurchased;
                            while (getNumBuys() > 0) {
                                cardWasPurchased = false;
                                System.out.println("You have " + getHandPurchasePower() + " coins to spend");
                                System.out.println("You have " + getNumBuys() + " buy(s) remaining this turn");
                                System.out.println("Would you like to buy a card?");
                                if (true) {
                                    System.out.println("What card do you want?");
//                            for (List<Card> list : cardSupply) {
//                                if (list.size()!=0 && list.get(0).getCardName().equals(scannerInput)) {
//                                    if(buyCard(list.get(0),list)){
//                                        cardWasPurchased = true;
//                                        System.out.println("Purchase successful\n");
//                                    }
//                                    break;
//                                }
//                            }
                                    if (!cardWasPurchased) {
                                        System.out.println("Purchase failed, try again\n");
                                    }
                                } else {
                                    break;
                                }
                            }*/
                            discardHand();
                        } else Thread.sleep(50);
                    }
                    gameArea.setText(null);
                } catch (InterruptedException | IOException ex) {
                    System.out.println("IOException at gameSender run()");
                }
            }
            public void checkGameField() throws InterruptedException, IOException {
                while (!gameTyped) {
                    if (gameOver && !gameAreaDisabled) {
                        gameOverStuff();
                    }
                    Thread.sleep(50);
                } gameTyped = false;
            }
        }

        public class ChatSender implements Runnable {

            public ChatSender() {
                chatSenderThread = new Thread(this);
                chatSenderThread.start();
            }

            public void run() {
                System.out.println("chatSender thread: " + Thread.currentThread());
                try {
                    sendNextInstruction(Instruction.DEALCARDS);
                    sendNextInstruction(Instruction.BEGINCHAT);
                    sendNextInstruction(Instruction.NAME);
                    sendMessage(name);

                    String s = "";
                    while (!s.equals("leave")) {
//
                        while (!chatTyped) {
                            Thread.sleep(50);
                        }
                        if (chatTyped) {
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
                    myTurn = receiveTurnStatus();
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
                    else if (nextInstruction == Instruction.GAMEMESSAGE /*|| nextInstruction == Instruction.GAMENAME*/) {
                        gameString = (receiveMessage());
//                        gameAreaStrings.add(gameString + "\n");
//                        String s = "";
//                        if (gameAreaStrings.size() > 7) gameAreaStrings.remove(0);
//                        for (String string : gameAreaStrings) {
//                            s += string;
//                        }
//                        gameArea.setText(s);
                    }
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

        public boolean receiveTurnStatus() throws IOException {
            return dataIn.readBoolean();
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

    public static void newTurn(){
        handLimit = 5;
        numActions = 1;
        numBuys = 1;
        handPurchasePower = 0;
        amountSpentThisTurn = 0;
        bonusPurchasePower = 0;
        drawHand();
    }
    public static int getNumBuys(){
        return numBuys;
    }
    public static int getNumActions() {
        return numActions;
    }
    public static boolean buyCard(Card card, List<Card> cardStack){
        if(getHandPurchasePower()>=card.getCost()){
            discardPile.add(card);
            cardStack.remove(0);
            numBuys--;
            amountSpentThisTurn+=card.getCost();
            return true;
        }
        else{
            System.out.println("You don't have enough coins");
            return false;
        }
    }
    public static void performAction(ActionCard actionCard){
        Scanner input = new Scanner(actionCard.getAction());
        String extractedString;
        int numAdds = 0;

        while(input.hasNext()){
            extractedString = input.next();
            if(extractedString.contains("+")){
                numAdds = Integer.parseInt(extractedString.substring(extractedString.indexOf("+")+1,extractedString.indexOf("+")+2));
            }
            else if(extractedString.contains("Card")){
                handLimit+=numAdds;
                for(int i=0;i<numAdds;i++){
                    drawCardFromDeck();
                }
            }
            else if(extractedString.contains("Action")){
                numActions+=numAdds;
            }
            else if(extractedString.contains("Buy")){
                numBuys+=numAdds;
            }
            else if(extractedString.contains("Coin")){
                bonusPurchasePower+=numAdds;
            }
        }
        discardPile.add(actionCard);
        hand.remove(actionCard);
        numActions--;
    }
    public static int getHandPurchasePower(){
        handPurchasePower = 0;
        for(Card card: hand){
            handPurchasePower+=card.getPurchasePower();
        }
        handPurchasePower-=amountSpentThisTurn;
        handPurchasePower+=bonusPurchasePower;
        return handPurchasePower;
    }
    public static List<Card> getHand() {
        return hand;
    }
    public static boolean drawCardFromDeck(){
        if(deck.size()==0){
            discardPileToDeck();
            shuffleDeck();
        }
        if(hand.size()>=handLimit){
            System.out.println("You already have a full hand");
            return false;
        }
        else if(deck.size()==0) {
            System.out.println("You have no more cards");
            return false;
        }
        else{
            hand.add(deck.get(0));
            deck.remove(0);
            return true;
        }
    }
    public static void discardPileToDeck(){
        while(discardPile.size()>0) {
            deck.add(discardPile.get(0));
            discardPile.remove(0);
        }
    }
    public static void drawHand(){
        for(int i=0;i<handLimit;i++){
            if(!drawCardFromDeck()) break;
        }
    }
    public static void shuffleDeck(){
        Collections.shuffle(deck);
    }
    public static void discardHand(){
        while(hand.size()>0) {
            discardPile.add(hand.get(0));
            hand.remove(0);
        }
    }
    public static int getTotalPoints() {
        int points = 0;
        for (Card card : discardPile) {
            points += card.getVictoryPoints();
        }
        for (Card card : deck) {
            points += card.getVictoryPoints();
        }
        for (Card card : hand) {
            points += card.getVictoryPoints();
        }
        return points;
    }

}