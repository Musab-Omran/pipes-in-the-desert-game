package model.elements;

/**
 * Base class for every object placed on the game board.
 */
public abstract class FieldElement {

    protected String id;
    protected int positionX;
    protected int positionY;

    public FieldElement(String id) {
        this.id = id;
        this.positionX = 0;
        this.positionY = 0;
    }

    public FieldElement(String id, int positionX, int positionY) {
        this.id = id;
        this.positionX = positionX;
        this.positionY = positionY;
    }

    /**
     * Each element handles its own water movement logic.
     */
    public abstract void moveWater();

    public String getId() {
        return id;
    }

    public int getPositionX() {
        return positionX;
    }

    public int getPositionY() {
        return positionY;
    }

    public void setPositionX(int x) {
        this.positionX = x;
    }

    public void setPositionY(int y) {
        this.positionY = y;
    }

    @Override
    public String toString() {
        return id;
    }
}
