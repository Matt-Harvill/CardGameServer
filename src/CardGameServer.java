import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class CardGameServer {

    public static void main(String[] args) {
        CardGameServer server = new CardGameServer();
        server.acceptConnections();
    }

    public void acceptConnections() {
        try {
            System.out.println("Accepting players...");
            while (numPlayers<3) {
                Socket s = serverSocket.accept();
                numPlayers++;
                System.out.println("There are now " + numPlayers + " player(s) connected");
                Player player = new Player(s);
                listOfPlayers.add(player);
            }
            System.out.println("No longer accepting players");
        } catch (IOException ex) {
            System.out.println("IOException from ChatServerConnections run()");
        }
    }

    private static List<List<Card>> cardSupply;
    private static List<Card> allCards;
    private static List<Card> cardsInGame;
    private static boolean gameOver;

    public CardGameServer() {
        System.out.println("-----Game Server-----");
        numPlayers = 0;
        listOfPlayers = new ArrayList<>();
        cardSupply = new ArrayList<>();
        allCards = new ArrayList<>();
        cardsInGame = new ArrayList<>();
        gameOver = false;

        try {
            serverSocket = new ServerSocket(52567);
            File cardDefinitions = new File("Card Definitions.txt");
            File cardsInThisGame = new File("Cards in this Game.txt");

            readInCardDefinitions(cardDefinitions, allCards);
            readInCardsInThisGame(cardsInThisGame, allCards, cardsInGame, cardSupply);

        } catch (IOException ex) {
            System.out.println("IOException from CardGameServer Constructor");
        }
    }

    public void checkStacks(List<List<Card>> cardSupply) {
        boolean colony = false;
        boolean province = false;
        boolean lessThan3Gone = false;
        ListIterator<List<Card>> iterator = cardSupply.listIterator();
        while(iterator.hasNext()) {
            List<Card> list = iterator.next();
            if(list.size()==0) {
                iterator.remove();
            }
            else if (list.get(0).getCardName().equals("Colony")) colony = true;
            else if (list.get(0).getCardName().equals("Province")) province = true;
        }
        if ((cardsInGame.size() - 2) < cardSupply.size()) lessThan3Gone = true;
        gameOver = !(colony && province && lessThan3Gone);
//        System.out.println(cardSupply);
    }
    public static void readInCardsInThisGame(File file, List<Card> allCards, List<Card> cardsInGame, List<List<Card>> cardSupply) throws FileNotFoundException {
        Scanner lineReader = new Scanner(file);
        Scanner inputFinder;

        while (lineReader.hasNextLine()) {
            String currentLine = lineReader.nextLine();
            inputFinder = new Scanner(currentLine);

            String name = inputFinder.next();
            int num = inputFinder.nextInt();

            for (Card card : allCards) {
                if (card.getCardName().equals(name)) {
                    if (num > 0) {
                        List<Card> list = new ArrayList<>();
                        populateCards(cardsInGame, card, 1);
                        cardSupply.add(list);
                        populateCards(list, card, num);
                    }
                    break;
                }
            }
            inputFinder.close();
        }
        lineReader.close();
    }
    public static void readInCardDefinitions(File file, List<Card> allCards) throws FileNotFoundException {
        Scanner lineReader = new Scanner(file);
        Scanner inputFinder;
        boolean actionCards = false;

        while (lineReader.hasNextLine()) {
            String currentLine = lineReader.nextLine();

            if (currentLine.contains("Name, Cost, PurchasePower, VictoryPoints, Action") || currentLine.equals(""))
                continue;
            if (currentLine.equals("Action Cards")) {
                actionCards = true;
                continue;
            }

            inputFinder = new Scanner(currentLine);
            String name = inputFinder.next();
            int cost = inputFinder.nextInt();
            int purchasePower = inputFinder.nextInt();
            int victoryPoints = inputFinder.nextInt();

            if (actionCards) {
                StringBuilder action = new StringBuilder();
                while (inputFinder.hasNext()) {
                    action.append(inputFinder.next()).append(" ");
                }
                allCards.add(new ActionCard(name, cost, purchasePower, victoryPoints, action.toString()));
            } else {
                allCards.add(new Card(name, cost, purchasePower, victoryPoints));
            }

            inputFinder.close();
        }
        lineReader.close();
    }
    public static void initialDeal(List<Card> deck, List<List<Card>> cardSupply) {
        for(List<Card> list: cardSupply) {
            if (list.get(0).getCardName().equals("Copper")) {
                for (int j = 0; j < 7; j++) {
                    if (list.size() != 0) {
                        deck.add(list.get(0));
                        list.remove(0);
                    } else {
                        System.out.println("You forgot to put enough Coppers in the supply!");
                        break;
                    }
                }
            } else if (list.get(0).getCardName().equals("Estate")) {
                for (int j = 0; j < 3; j++) {
                    if (list.size() != 0) {
                        deck.add(list.get(0));
                        list.remove(0);
                    } else {
                        System.out.println("You forgot to put enough Estates in the supply!");
                        break;
                    }
                }
            }
        }
    }
    public static void populateCards(List<Card> cardSupply, Card card, int numCards) {
        for (int i = 0; i < numCards; i++) {
            cardSupply.add(card);
        }
    }

    private static ServerSocket serverSocket;
    private static List<Player> listOfPlayers;
    private static int numPlayers;
    private static String messageSent;
    private static Instruction nextInstruction;
    private static String currentPlayerName;
    private static String playerCompletingTurn;
    private static ReentrantLock lock = new ReentrantLock();

    private class Player implements Runnable {

        private Socket socket;
        private boolean displayJoinedGame;
        private String playerName;
        private Player player;
        private ObjectOutputStream dataOut;
        private ObjectInputStream dataIn;
        private final int playerOrder;
        private Card sentCard;
        private List<Card> initialDeck;

        public Player(Socket s) {
            displayJoinedGame = false;
            player = this;
            socket = s;
            playerOrder = numPlayers;
            initialDeck = new ArrayList<>();

            try {
                dataIn = new ObjectInputStream(socket.getInputStream());
                dataOut = new ObjectOutputStream(socket.getOutputStream());
                playerName = "Anonymous #" + numPlayers;
            } catch (IOException ex) {
                System.out.println("IOException from Player Constructor");
            }

            Thread t = new Thread(this);
            t.start();
        }

        public void sendData() {
            try {
                sendNextInstruction(nextInstruction);
                sendPlayerName();
                sendGameStatus();
                if(nextInstruction==Instruction.BEGINCHAT) {
                } else if(nextInstruction==Instruction.DEALCARDS) {
                    sendDeck();
                } else {
                    sendMessage(messageSent);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void sendGameStatus() throws IOException {
            dataOut.writeBoolean(gameOver);
            dataOut.flush();
        }
        public void sendDeck() throws IOException {
            dataOut.writeObject(initialDeck);
            dataOut.flush();
        }
        public void sendPlayerName() throws IOException {
            dataOut.writeUTF(currentPlayerName);
            dataOut.flush();
        }
        public void sendMessage(String s) throws IOException {
            dataOut.writeUTF(s);
            dataOut.flush();
        }
        public void sendNextInstruction(Instruction instruction) throws IOException {
            dataOut.writeObject(instruction);
            dataOut.flush();
        }

        public void run() {
            try {
                if(playerOrder==1) playerCompletingTurn = playerName;

                while (nextInstruction != Instruction.LEAVE) {
                    Instruction tempInstruction = getNextInstruction();
                    try {
                        lock.lock();
                        nextInstruction = tempInstruction;
                        currentPlayerName = playerName;
                        if (nextInstruction == Instruction.MESSAGE /*|| nextInstruction==Instruction.GAMEMESSAGE*/) {
                            String s = receiveMessage();
                            System.out.println(playerName + ": " + s);
                            messageSent = (playerName + ": " + s);
                        } else if (nextInstruction == Instruction.NAME /*|| nextInstruction==Instruction.GAMENAME*/) {
                            if(!displayJoinedGame) {
                                playerName = receiveMessage();
                                System.out.println(playerName + " has joined the game");
                                messageSent = (playerName + " has joined the game");
                                displayJoinedGame = true;
                            } else {
                                String temp = receiveMessage();
                                System.out.println(playerName + " has changed their name to " + temp);
                                messageSent = (playerName + " has changed their name to " + temp);
                                playerName = temp;
                            }
                        } else if (nextInstruction == Instruction.BEGINCHAT) {
                            sendData();
                            continue;
                        } else if (nextInstruction == Instruction.LEAVE) {
                            messageSent = (playerName + " has left the game");
                        } else if (nextInstruction==Instruction.DEALCARDS){
                            initialDeal(initialDeck,cardSupply);
                            sendData();
                            continue;
                        }

                        checkStacks(cardSupply);
                        for (int i=0; i< listOfPlayers.size(); i++) {
                            listOfPlayers.get(i).sendData();
                        }
//                        Thread.sleep(3500);

                        if (nextInstruction == Instruction.LEAVE) listOfPlayers.remove(player);
                        displayJoinedGame = true;
                    } finally {
                        lock.unlock();
                    }
                }
                closeConnection();
            } catch (IOException | ClassNotFoundException ex) {
                System.out.println("IOException from Player run()");
            }
        }

        public String receiveMessage() throws IOException {
            return dataIn.readUTF();
        }
        public Instruction getNextInstruction() throws IOException, ClassNotFoundException {
            return (Instruction) dataIn.readObject();
        }
        public void closeConnection() throws IOException {
            numPlayers--;
            socket.close();
            System.out.println(playerName + " closed their SSC");
        }
    }
}