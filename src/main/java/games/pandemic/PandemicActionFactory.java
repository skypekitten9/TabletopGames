package games.pandemic;

import core.actions.*;
import core.components.BoardNode;
import core.components.Card;
import core.components.Counter;
import core.components.Deck;
import core.properties.*;
import games.pandemic.actions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static core.CoreConstants.*;
import static games.pandemic.PandemicConstants.*;
import static games.pandemic.PandemicConstants.infectionHash;
import static utilities.Utils.generatePermutations;
import static utilities.Utils.indexOf;

class PandemicActionFactory {

    /**
     * Calculates regular player actions.
     * @return - ArrayList, various action types (unique).
     */
    static List<AbstractAction> getPlayerActions(PandemicGameState pgs) {
        PandemicParameters pp = (PandemicParameters) pgs.getGameParameters();

        // get player's hand, role card, role string, player location name and player location BoardNode
        Deck<Card> playerHand = ((Deck<Card>) pgs.getComponentActingPlayer(playerHandHash));
        String roleString = pgs.getPlayerRoleActingPlayer();
        PropertyString playerLocationName = (PropertyString) pgs.getComponentActingPlayer(playerCardHash)
                .getProperty(playerLocationHash);
        BoardNode playerLocationNode = pgs.world.getNodeByProperty(nameHash, playerLocationName);
        int activePlayer = pgs.getTurnOrder().getCurrentPlayer(pgs);

        // Create a list for possible actions, including first move actions
        Set<AbstractAction> actions = new HashSet<>(getMoveActions(pgs, activePlayer, playerHand));

        // Build research station, discard card corresponding to current player location to build one, if not already there.
        if (!((PropertyBoolean) playerLocationNode.getProperty(researchStationHash)).value
                && ! roleString.equals("Operations Expert")) {
            int card_in_hand = -1;
            for (int idx = 0; idx < playerHand.getSize(); idx++) {
                Card card = playerHand.getComponents().get(idx);
                Property cardName = card.getProperty(nameHash);
                if (cardName.equals(playerLocationName)) {
                    card_in_hand = idx;
                    break;
                }
            }
            if (card_in_hand != -1) {
                actions.addAll(getResearchStationActions(pgs, playerLocationName.value, playerHand.getComponents().get(card_in_hand), card_in_hand));
            }
        }

        // Treat disease
        PropertyIntArray cityInfections = (PropertyIntArray)playerLocationNode.getProperty(infectionHash);
        for (int i = 0; i < cityInfections.getValues().length; i++){
            if (cityInfections.getValues()[i] > 0){
                boolean treatAll = false;
                if (roleString.equals("Medic")) treatAll = true;

                actions.add(new TreatDisease(pp.n_initial_disease_cubes, colors[i], playerLocationName.value, treatAll));
            }
        }

        // Share knowledge, give or take card, player can only have 7 cards
        // Both players have to be at the same city
        List<Integer> players = ((PropertyIntArrayList)playerLocationNode.getProperty(playersHash)).getValues();
        for (int i : players) {
            if (i != activePlayer) {
                Deck<Card> otherDeck = (Deck<Card>) pgs.getComponent(playerHandHash, i);
                String otherRoleString = pgs.getPlayerRole(i);

                // Give card
                for (int j = 0; j < playerHand.getSize(); j++) {
                    Card card = playerHand.getComponents().get(j);
                    // Researcher can give any card, others only the card that matches the city name
                    if (roleString.equals("Researcher") || (card.getProperty(nameHash)).equals(playerLocationName)) {
                        actions.add(new DrawCard(playerHand.getComponentID(), otherDeck.getComponentID(), j));
                    }
                }

                // Take card
                // Can take any card from the researcher or the card that matches the city if the player is in that city
                for (int j = 0; j < otherDeck.getSize(); j++) {
                    Card card = otherDeck.getComponents().get(j);
                    if (otherRoleString.equals("Researcher") || (card.getProperty(nameHash)).equals(playerLocationName)) {
                        actions.add(new DrawCard(otherDeck.getComponentID(), playerHand.getComponentID(), j));
                    }
                }
            }
        }

        // Discover a cure, cards of the same colour at a research station
        ArrayList<Integer>[] colorCounter = new ArrayList[colors.length];
        for (Card card: playerHand.getComponents()){
            Property p  = card.getProperty(colorHash);
            if (p != null){
                // Only city cards have colours, events don't
                String color = ((PropertyColor)p).valueStr;
                int idx = indexOf(colors, color);
                if (colorCounter[idx] == null)
                    colorCounter[idx] = new ArrayList<>();
                colorCounter[idx].add(card.getComponentID());
            }
        }
        for (int i = 0 ; i < colorCounter.length; i++){
            if (colorCounter[i] != null){
                if (roleString.equals("Scientist") && colorCounter[i].size() >= pp.n_cards_for_cure_reduced){
                    actions.add(new CureDisease(colors[i], colorCounter[i]));
                } else if (colorCounter[i].size() >= pp.n_cards_for_cure){
                    actions.add(new CureDisease(colors[i], colorCounter[i]));
                }
            }
        }

        // Special role actions
        actions.addAll(getSpecialRoleActions(pgs, roleString, playerHand, playerLocationName.value));

        // Event actions
        actions.addAll(getEventActions(pgs));
        actions.remove(new DoNothing());  // Players can't just do nothing in main game phase

        // Done!
        return new ArrayList<>(actions);
    }

    /**
     * Calculate all special actions that can be performed by different player roles. Not included those that can
     * execute the same actions as other players but with different parameters.
     * @param role - role of player
     * @param playerHand - cards in hand for the player
     * @param playerLocation - current location of player
     * @return - list of actions for the player role.
     */
    static List<AbstractAction> getSpecialRoleActions(PandemicGameState pgs, String role, Deck<Card> playerHand, String playerLocation) {
        ArrayList<AbstractAction> actions = new ArrayList<>();
        int playerIdx = pgs.getTurnOrder().getCurrentPlayer(pgs);

        switch (role) {
            // Operations expert special actions
            case "Operations Expert":
                if (!(pgs.researchStationLocations.contains(playerLocation))) {
                    actions.addAll(getResearchStationActions(pgs, playerLocation, null, -1));
                } else {
                    // List all the other nodes with combination of all the city cards in hand
                    for (BoardNode bn : pgs.world.getBoardNodes()) {
                        for (int c = 0; c < playerHand.getSize(); c++) {
                            if (playerHand.getComponents().get(c).getProperty(colorHash) != null) {
                                actions.add(new MovePlayerWithCard(playerIdx, ((PropertyString) bn.getProperty(nameHash)).value, c));
                            }
                        }
                    }
                }
                break;
            // Dispatcher special actions
            case "Dispatcher":
                // Move any pawn, if its owner agrees, to any city containing another pawn.
                String[] locations = new String[pgs.getNPlayers()];
                for (int i = 0; i < pgs.getNPlayers(); i++) {
                    locations[i] = ((PropertyString) pgs.getComponent(playerCardHash, i)
                            .getProperty(playerLocationHash)).value;
                }
                for (int j = 0; j < pgs.getNPlayers(); j++) {
                    for (int i = 0; i < pgs.getNPlayers(); i++) {
                        if (i != j) {
                            actions.add(new MovePlayer(i, locations[j]));
                        }
                    }
                }

                // Move another player’s pawn, if its owner agrees, as if it were his own.
                for (int i = 0; i < pgs.getNPlayers(); i++) {
                    if (i != playerIdx) {
                        actions.addAll(getMoveActions(pgs, i, playerHand));
                    }
                }
                break;
            // Contingency Planner special actions
            case "Contingency Planner":
                Deck<Card> plannerDeck = (Deck<Card>) pgs.getComponent(plannerDeckHash);
                if (plannerDeck.getSize() == 0) {
                    // then can pick up an event card
                    Deck<Card> infectionDiscardDeck = (Deck<Card>) pgs.getComponent(infectionDiscardHash);
                    List<Card> infDiscard = infectionDiscardDeck.getComponents();
                    for (int i = 0; i < infDiscard.size(); i++) {
                        Card card = infDiscard.get(i);
                        if (card.getProperty(colorHash) != null) {
                            actions.add(new DrawCard(infectionDiscardDeck.getComponentID(), plannerDeck.getComponentID(), i));
                        }
                    }
                }
                break;
        }
        return actions;
    }

    /**
     * Calculates AddResearchStation* actions.
     * @param playerLocation - current location of player
     * @param card - card that is used to play this action, will be discarded. Ignored if null.
     * @param cardIdx - index of card used to play this action (from player hand).
     * @return - list of AddResearchStation* actions
     */
    static List<AbstractAction> getResearchStationActions(PandemicGameState pgs, String playerLocation, Card card, int cardIdx) {
        Set<AbstractAction> actions = new HashSet<>();
        Counter rStationCounter = (Counter) pgs.getComponent(researchStationHash);

        // Check if any research station tokens left
        if (rStationCounter.getValue() == 0) {
            // If all research stations are used, then take one from board
            for (String station : pgs.researchStationLocations) {
                if (card == null) actions.add(new AddResearchStationFrom(station, playerLocation));
                else actions.add(new AddResearchStationWithCardFrom(station, playerLocation, cardIdx));
            }
        } else {
            // Otherwise can just build here
            if (card == null) actions.add(new AddResearchStation(playerLocation));
            else actions.add(new AddResearchStationWithCard(playerLocation, cardIdx));
        }
        return new ArrayList<>(actions);
    }

    /**
     * Calculates all movement actions (drive/ferry, charter flight, direct flight, shuttle flight).
     * @param playerId - player to calculate movement for
     * @param playerHand - deck of cards to be used for movement
     * @return all movement actions
     */
    static List<AbstractAction> getMoveActions(PandemicGameState pgs, int playerId, Deck<Card> playerHand){
        Set<AbstractAction> actions = new HashSet<>();

        PropertyString playerLocationProperty = (PropertyString) pgs.getComponent(playerCardHash, playerId)
                .getProperty(playerLocationHash);
        String playerLocationName = playerLocationProperty.value;
        BoardNode playerLocationNode = pgs.world.getNodeByProperty(nameHash, playerLocationProperty);
        HashSet<BoardNode> neighbours = playerLocationNode.getNeighbours();

        // Drive / Ferry add actions for travelling to immediate cities
        for (BoardNode otherCity : neighbours){
            actions.add(new MovePlayer(playerId, ((PropertyString)otherCity.getProperty(nameHash)).value));
        }

        // Iterate over all the cities in the world
        for (BoardNode bn: pgs.world.getBoardNodes()) {
            String destination = ((PropertyString) bn.getProperty(nameHash)).value;

            if (!neighbours.contains(bn)) {  // Ignore neighbours, already covered in Drive/Ferry actions
                for (int c = 0; c < playerHand.getSize(); c++){
                    Card card = playerHand.getComponents().get(c);

                    //  Check if card has country to determine if it is city card or not
                    if ((card.getProperty(countryHash)) != null){
                        String cardCity = ((PropertyString)card.getProperty(nameHash)).value;
                        if (playerLocationName.equals(cardCity)){
                            // Charter flight, discard card that matches your city and travel to any city
                            // Only add the ones that are different from the current location
                            if (!destination.equals(playerLocationName)) {
                                actions.add(new MovePlayerWithCard(playerId, destination, c));
                            }
                        } else if (destination.equals(cardCity)) {
                            // Direct Flight, discard city card and travel to that city
                            actions.add(new MovePlayerWithCard(playerId, cardCity, c));
                        }
                    }
                }
            }
        }

        // Shuttle flight, move from city with research station to any other research station
        // If current city has research station, add every city that has research stations
        if (((PropertyBoolean)playerLocationNode.getProperty(researchStationHash)).value) {
            for (String station: pgs.researchStationLocations){
                actions.add(new MovePlayer(playerId, station));
            }
        }

        return new ArrayList<>(actions);
    }

    /**
     * Calculates discard card actions for current player.
     * @return - ArrayList, DrawCard actions (from their hand to the player discard deck).
     */
    static List<AbstractAction> getDiscardActions(PandemicGameState pgs) {
        Deck<Card> playerDeck = (Deck<Card>) pgs.getComponentActingPlayer(playerHandHash);
        Deck<Card> playerDiscardDeck = (Deck<Card>) pgs.getComponent(playerDeckDiscardHash);

        Set<AbstractAction> acts = new HashSet<>();  // Only discard card actions available
        for (int i = 0; i < playerDeck.getSize(); i++) {
            acts.add(new DrawCard(playerDeck.getComponentID(), playerDiscardDeck.getComponentID(), i));  // adding card i from player deck to player discard deck
        }
        return new ArrayList<>(acts);
    }

    /**
     * Calculates actions restricted to removing infection discarded cards (or do nothing) for current player.
     * @return - ArrayList, RemoveCardWithCard actions + DoNothing.
     */
    static List<AbstractAction> getRPactions(PandemicGameState pgs) {
        Set<AbstractAction> acts = new HashSet<>();
        acts.add(new DoNothing());

        Deck<Card> infectionDiscard = (Deck<Card>) pgs.getComponent(infectionDiscardHash);
        int nInfectDiscards = infectionDiscard.getSize();
        Deck<Card> ph = (Deck<Card>) pgs.getComponentActingPlayer(playerHandHash);
        Deck<Card> playerDiscard = (Deck<Card>) pgs.getComponent(playerDeckDiscardHash);
        int nCards = ph.getSize();
        for (int cp = 0; cp < nCards; cp++) {
            Card card = ph.getComponents().get(cp);
            if (((PropertyString)card.getProperty(nameHash)).value.equals("Resilient Population")) {
                for (int idx = 0; idx < nInfectDiscards; idx++) {
                    acts.add(new RemoveComponentFromDeck<Card>(ph.getComponentID(), playerDiscard.getComponentID(), cp, infectionDiscard.getComponentID(), idx));
                }
                break;
            }
        }
        return new ArrayList<>(acts);
    }

    /**
     * Calculates all event actions available for the given player.
     * @return - list of all actions available based on event cards owned by the player.
     */
    static List<AbstractAction> getEventActions(PandemicGameState pgs) {
        PandemicParameters pp = (PandemicParameters) pgs.getGameParameters();
        Deck<Card> playerHand = (Deck<Card>) pgs.getComponentActingPlayer(playerHandHash);
        Deck<Card> playerDiscard = (Deck<Card>) pgs.getComponent(playerDeckDiscardHash);
        int fromDeck = playerHand.getComponentID();
        int toDeck = playerDiscard.getComponentID();

        Set<AbstractAction> actions = new HashSet<>();
        actions.add(new DoNothing());  // Can always do nothing

        for (Card card: playerHand.getComponents()){
            Property p  = card.getProperty(colorHash);
            if (p == null){
                // Event cards don't have colour
                int cardIdx = playerHand.getComponents().indexOf(card);
                actions.addAll(actionsFromEventCard(pgs, card, pp, fromDeck, toDeck, cardIdx));
            }
        }

        // Contingency planner gets also special deck card
        Card playerCard = ((Card) pgs.getComponentActingPlayer(playerCardHash));
        String roleString = ((PropertyString)playerCard.getProperty(nameHash)).value;
        if (roleString.equals("Contingency Planner")){
            Deck<Card> plannerDeck = (Deck<Card>) pgs.getComponent(plannerDeckHash);
            if (plannerDeck.getSize() > 0){
                // then can pick up an event card
                Card card = plannerDeck.peek();
                int cardIdx = playerHand.getComponents().indexOf(card);
                actions.addAll(actionsFromEventCard(pgs, card, pp, fromDeck, toDeck, cardIdx));
            }
        }

        return new ArrayList<>(actions);
    }

    /**
     * Calculates action variations based on event card type.
     * @param card - event card to be played
     * @param pp - game parameters
     * @return list of actions corresponding to the event card.
     */
    static List<AbstractAction> actionsFromEventCard(PandemicGameState pgs,
                                                      Card card, PandemicParameters pp, int deckFrom, int deckTo, int cardIdx){
        Set<AbstractAction> actions = new HashSet<>();
        String cardString = ((PropertyString)card.getProperty(nameHash)).value;

        switch (cardString) {
            case "Airlift":
//                System.out.println("Airlift");
//            System.out.println("Move any 1 pawn to any city. Get permission before moving another player's pawn.");
                for (BoardNode bn: pgs.world.getBoardNodes()) {
                    String cityName = ((PropertyString) bn.getProperty(nameHash)).value;
                    for (int i = 0; i < pgs.getNPlayers(); i++) {
                        // Check if player is already there
                        String pLocation = ((PropertyString) pgs.getComponent(playerCardHash, i).getProperty(playerLocationHash)).value;
                        if (pLocation.equals(cityName)) continue;
                        actions.add(new MovePlayerWithCard(i, cityName, cardIdx));
                    }
                }

                Deck<Card> infDeck = (Deck<Card>) pgs.getComponent(infectionDiscardHash);
                Deck<Card> discardDeck = (Deck<Card>) pgs.getComponent(playerDeckDiscardHash);

                for (int i = 0; i < infDeck.getSize(); i++){
                    actions.add(new DrawCard(infDeck.getComponentID(), discardDeck.getComponentID(), i));
                }
                break;
            case "Government Grant":
                // "Add 1 research station to any city (no City card needed)."
                for (BoardNode bn: pgs.world.getBoardNodes()) {
                    if (!((PropertyBoolean) bn.getProperty(researchStationHash)).value) {
                        String cityName = ((PropertyString) bn.getProperty(nameHash)).value;
                        actions.addAll(getResearchStationActions(pgs, cityName, card, cardIdx));
                    }
                }
                break;
            case "One quiet night":
//                System.out.println("One quiet night");
//            System.out.println("Skip the next Infect Cities step (do not flip over any Infection cards).");
                actions.add(new QuietNight(deckFrom, deckTo, cardIdx));
                break;
            case "Forecast":
//                System.out.println("Forecast");
                actions.add(new Forecast(deckFrom, deckTo, cardIdx));
                break;
        }

        return new ArrayList<>(actions);
    }

    static List<AbstractAction> getForecastActions(PandemicGameState pgs) {
//            System.out.println("Draw, look at, and rearrange the top 6 cards of the Infection Deck. Put them back on top.");
        // Generate all permutations. Each one is a potential action.
        PandemicParameters pp = (PandemicParameters) pgs.getGameParameters();
        Deck<Card> playerHand = (Deck<Card>) pgs.getComponentActingPlayer(playerHandHash);
        Deck<Card> playerDiscard = (Deck<Card>) pgs.getComponent(playerDeckDiscardHash);
        int deckFrom = playerHand.getComponentID();
        int deckTo = playerDiscard.getComponentID();

        Set<AbstractAction> actions = new HashSet<>();
        Deck<Card> infectionDeck = (Deck<Card>) pgs.getComponent(infectionHash);
        int nInfectCards = infectionDeck.getSize();
        int n = Math.min(nInfectCards, pp.n_forecast_cards);
        ArrayList<int[]> permutations = new ArrayList<>();
        int[] order = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        generatePermutations(n, order, permutations);
        for (int[] perm: permutations) {
            actions.add(new RearrangeDeckOfCards(infectionDeck.getComponentID(), perm));
        }
        return new ArrayList<>(actions);
    }
}
