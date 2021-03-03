public class ActionCard extends Card {

    private final String ACTION;

    public ActionCard(String cardName, int cost, int purchasePower, int victoryPoints, String action){
        super(cardName,cost,purchasePower,victoryPoints);
        ACTION = action;
    }

    public ActionCard(ActionCard card) {
        super(card);
        ACTION = card.getAction();
    }

    public String getAction(){
        return ACTION;
    }

    public boolean isActionCard(){
        return true;
    }

    public String toString(){
        return (super.toString() + "Action: " + ACTION + "\n");
    }

}