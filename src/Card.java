import java.io.Serializable;

public class Card implements Serializable {

    private final String CARDNAME;
    private int cost;
    private int purchasePower;
    private int victoryPoints;

    public Card(String cardName, int cost, int purchasePower, int victoryPoints){
        CARDNAME = cardName;
        this.cost = cost;
        this.purchasePower = purchasePower;
        this.victoryPoints = victoryPoints;
    }

    public Card(Card card){
        CARDNAME = card.getCardName();
        cost = card.getCost();
        purchasePower = card.getPurchasePower();
        victoryPoints = card.getVictoryPoints();
    }

    public int getVictoryPoints(){
        return victoryPoints;
    }

    public int getPurchasePower(){
        return purchasePower;
    }

    public String getCardName(){
        return CARDNAME;
    }

    public int getCost(){
        return cost;
    }

    public String toString(){
        return "Card: " + CARDNAME + "  Cost: " + cost + "  Purchase Power: " + purchasePower + "  Victory Points: " + victoryPoints + "\n";
    }

    public boolean isActionCard(){
        return false;
    }

}
