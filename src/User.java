import java.util.Objects;

public class User {

    public static final String INIT = "init";
    public static final String OUTSIDE = "outside";
    public static final String INSIDE = "inside";

    private String nick;
    private String state;
    private String room;

    User(String nick) {
        this.nick = nick;
        this.state = INIT;
        this.room = "";
    }

    @Override
    public String toString() {
        return "User{" +
                "nick='" + nick + '\'' +
                ", state='" + state + '\'' +
                ", room='" + room + '\'' +
                '}';
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public String getState() {
        return state;
    }

    public void setStateInitial() {
        this.state = INIT;
    }

    public void setStateOutside() {
        this.state = OUTSIDE;
    }

    public void setStateInside() {
        this.state = INSIDE;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}
