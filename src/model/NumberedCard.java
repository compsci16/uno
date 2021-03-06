package model;

import java.util.Objects;

/**
 * Represents a valid numbered (and hence colored) card of an UNO deck.
 */
public class NumberedCard extends ColoredCard {
    private final int value;
    public NumberedCard(Color color, int value) {
        super(color);
        this.value = value;
    }

    @Override
    public String toString(){
        return getColor().name().charAt(0) + ":" + value;
    }

    public int getNumber(){
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NumberedCard that = (NumberedCard) o;
        return value == that.value && getColor().equals(that.getColor());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, getColor());
    }

}
