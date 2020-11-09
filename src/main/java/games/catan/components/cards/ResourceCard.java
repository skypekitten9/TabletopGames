package games.catan.components.cards;

import core.components.Card;

public class ResourceCard extends Card {

	public enum ResourceCardType {
		Brick,
		Lumber,
		Ore,
		Grain,
		Wool,
	}
	public final ResourceCard.ResourceCardType type;

	public ResourceCard(games.catan.components.cards.ResourceCard.ResourceCardType type){
		super(type.toString());
		this.type = type;
	}

	@Override
	public Card copy() {return new games.catan.components.cards.ResourceCard(this.type);}

	@Override
	public String toString() {
		//TODO (oh) string conversion of card data
		return("todo");
	}
}
