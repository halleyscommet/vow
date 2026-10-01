package us.dingl.VowSMPPlugin;

public enum ConfigKey {

    LEADERBOARD_ENABLED("leaderboard-enabled"),
    LEADERBOARD_X("leaderboard-position.x"),
    LEADERBOARD_Y("leaderboard-position.y"),
    LEADERBOARD_Z("leaderboard-position.z"),
    LOGGED_PLAYERS("logged-players"),
    KILLED_PLAYERS("killed-players"),
    PLAYER_LIVES("player-lives"),
    LIVES_SHOWN("lives-shown"),
    PLAYER_VOWS("player-vows"),
    INVITES("invites"),
    FORGIVEN_INVITES("forgiven-invites"),
    UPDATE_REPO("update-repo"),
    RESOURCE_PACK_URL("resource-pack.url"),
    RESOURCE_PACK_HASH("resource-pack.hash");

    private final String path;

    ConfigKey(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}