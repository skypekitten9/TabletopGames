package games.catan;

import core.AbstractGameParameters;
import core.AbstractGameState;
import core.components.Area;
import core.components.Component;
import core.components.GraphBoard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CatanGameState extends AbstractGameState {
    private CatanData data;
    public GraphBoard board;

    // Collection of areas, mapped to player ID. -1 is the general game area containing the board, counters and several decks.
    HashMap<Integer, Area> areas;

    // todo get turnorder right
    public CatanGameState(AbstractGameParameters pp, int nPlayers) {
        super(pp, new CatanTurnOrder(nPlayers, ((CatanParameters)pp).n_actions_per_turn));

        data = new CatanData((CatanParameters)pp);
        data.load(((CatanParameters)gameParameters).getDataPath());
    }

    private GraphBoard setupBoard(){
        board = new GraphBoard();
        // setup all the hexes in the game
        return board;
    }

    @Override
    protected List<Component> _getAllComponents() {
        List<Component> components = new ArrayList<>(); // areas.values()
//        components.add(tempDeck);
//        components.add(world);
        return components;
    }

    // Getters & setters
    public Component getComponent(int componentId, int playerId) {
        return areas.get(playerId).getComponent(componentId);
    }
    public Component getComponentActingPlayer(int componentId) {
        return areas.get(turnOrder.getCurrentPlayer(this)).getComponent(componentId);
    }
    public Component getComponent(int componentId) {
        return getComponent(componentId, -1);
    }
    Area getArea(int playerId) {
        return areas.get(playerId);
    }

    public CatanData getData(){
        return data;
    }

    @Override
    protected void _reset() {
        // todo set everything to null
        this.areas = null;
    }


    // todo implement methods below

    @Override
    protected AbstractGameState _copy(int playerId) {
        return this;
    }

    @Override
    protected double _getScore(int playerId) {
        return 0;
    }
}