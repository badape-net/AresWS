package net.badape.aresws.services;

public class SQL {

    public final static String SQL_CREATE_PLAYER = "INSERT INTO ares.players(credits) VALUES (?)";

    public final static String SQL_CREATE_DEV_PLAYER = "INSERT INTO ares.dev_players(dev_id, player_id) VALUES (?, ?)";

    public final static String SQL_GET_DEVID = "SELECT dev_players.dev_id, dev_players.player_id, players.credits FROM " +
            "ares.dev_players LEFT JOIN ares.players ON dev_players.player_id=players.player_id WHERE dev_players.dev_id = ?";

    public final static String SQL_ROSTER = "SELECT roster.hero_id, roster FROM players " +
            "JOIN roster ON roster.player_id = players.player_id " +
            "WHERE players.steam_id64 = ?";

    public final static String SQL_HERO_CREDITS = "SELECT credits, hero_id FROM heroes WHERE game_id = ?";

    public final static String SQL_BUY_HERO = "UPDATE ares.players SET credits = credits - ? WHERE steam_id64 = ?";

    public final static String SQL_HERO_DATA = "SELECT * FROM ares.heroes WHERE game_id = ?";

    public final static String SQL_ADD_ROSTER = "INSERT INTO ares.roster (player_id, hero_id) " +
            "VALUES ((SELECT player_id FROM ares.players WHERE steam_id64 = ?), ? );";

    public static final String SQL_HEROES = "SELECT * FROM ares.heroes;";

    public static final String SQL_UPSERT_HERO =
            "INSERT INTO ares.heroes(hero_id, game_id, credits, description) VALUES (?, ?, ?, ?)" +
            "ON CONFLICT ON CONSTRAINT heroes_pkey DO " +
            "UPDATE SET game_id=?, credits=?, description=? WHERE heroes.hero_id=?";
}
