package us.dingl.VowSMPPlugin;

public enum ConfigKey {

    LEADERBOARD_ENABLED("leaderboard-enabled"),
    LEADERBOARD_X("leaderboard-position.x"),
    LEADERBOARD_Y("leaderboard-position.y"),
    LEADERBOARD_Z("leaderboard-position.z"),
    KILLS_LEADERBOARD_ENABLED("kills-leaderboard-enabled"),
    KILLS_LEADERBOARD_X("kills-leaderboard-position.x"),
    KILLS_LEADERBOARD_Y("kills-leaderboard-position.y"),
    KILLS_LEADERBOARD_Z("kills-leaderboard-position.z"),
    PLAYER_KILLS("player-kills"),
    LOGGED_PLAYERS("logged-players"),
    KILLED_PLAYERS("killed-players"),
    PLAYER_LIVES("player-lives"),
    LIVES_SHOWN("lives-shown"),
    PLAYER_VOWS("player-vows"),
    EXTRA_VOW_LIVES("extra-vow-lives"),
    GURU_TRUSTED("guru-trusted"),
    MYTHICS("mythics"),
    MYTHIC_BROADCAST("mythic-transfer.broadcast"),
    MYTHIC_SOUND("mythic-transfer.sound"),
    UPDATE_REPO("update-repo"),
    RESOURCE_PACK_SOURCE("resource-pack.source"),
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