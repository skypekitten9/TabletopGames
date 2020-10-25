package games.dominion.actions;

import core.AbstractGameState;
import core.actions.AbstractAction;
import games.dominion.DominionGameState;

public class Village extends DominionAction {

    /**
     * Executes this action, applying its effect to the given game state. Can access any component IDs stored
     * through the AbstractGameState.getComponentById(int id) method.
     *
     * @param state - game state which should be modified by this action.
     * @return - true if successfully executed, false otherwise.
     */
    @Override
    public boolean _execute(DominionGameState state) {
        state.changeActions(2);
        state.drawCard(state.getCurrentPlayer());
        return true;
    }

    /**
     * Create a copy of this action, with all of its variables.
     * NO REFERENCES TO COMPONENTS TO BE KEPT IN ACTIONS, PRIMITIVE TYPES ONLY.
     *
     * @return - new AbstractAction object with the same properties.
     */
    @Override
    public AbstractAction copy() {
        // no state ... or do I want to retain a history of the cards drawn?
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Village);
    }

    @Override
    public int hashCode() {
        return 376298;
    }

    @Override
    public String getString(AbstractGameState gameState) {
        return "VILLAGE";
    }
}