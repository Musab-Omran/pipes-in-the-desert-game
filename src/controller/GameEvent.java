package controller;

/**
 * Represents a generic game event emitted by the model layer.
 * <p>
 * The class is intentionally lightweight and can be used by the GUI
 * to carry additional state if needed in later extensions.
 */
public class GameEvent {

    private final String type;
    private final String message;

    /**
     * Creates a new game event.
     *
     * @param type event type
     * @param message event message
     */
    public GameEvent(String type, String message) {
        this.type = type;
        this.message = message;
    }

    /**
     * Returns the event type.
     *
     * @return event type
     */
    public String getType() {
        return type;
    }

    /**
     * Returns the event message.
     *
     * @return event message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "GameEvent{" +
                "type='" + type + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
