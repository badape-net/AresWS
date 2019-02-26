package net.badape.aresws.db;

public final class SQL {

    public final static String SELECT_PLAYER = "SELECT * FROM players.player WHERE player_id = ?";

    public final static String CREATE_PLAYER = "INSERT INTO players.player DEFAULT VALUES";

    public final static String CREATE_DEV_PLAYER = "INSERT INTO players.dev_player(dev_id, player_id) VALUES (?, ?)";

    public final static String SELECT_DEVID = "SELECT dev_player.dev_id, dev_player.player_id FROM " +
            "players.dev_player LEFT JOIN players.player ON dev_player.player_id=player.player_id WHERE dev_player.dev_id = ?";

    public final static String SQL_ROSTER = "SELECT roster.hero_id, roster FROM players " +
            "JOIN roster ON roster.player_id = players.player_id " +
            "WHERE players.player_id = ?";

    public final static String SELECT_HERO_CREDITS = "SELECT credits, hero_id FROM hero WHERE game_id = ?";

    public final static String UPDATE_BUY_HERO = "UPDATE store.players SET credits = credits - ? WHERE player_id = ?";

    public final static String SQL_ADD_ROSTER = "INSERT INTO store.roster (player_id, hero_id) " +
            "VALUES ((SELECT player_id FROM store.players WHERE steam_id64 = ?), ? );";

    public static final String SQL_HEROES = "SELECT * FROM store.hero;";

    public static final String UPSERT_HERO =
            "INSERT INTO store.hero(hero_id, game_id, credits, description) VALUES (?, ?, ?, ?)" +
                    "ON CONFLICT ON CONSTRAINT hero_pkey DO " +
                    "UPDATE SET game_id=?, credits=?, description=? WHERE hero.hero_id=?";

    public static final String UPSERT_HERO_CONFIG =
            "INSERT INTO roster.hero_base(hero_id, health, mana, stamina, spawn_cost) VALUES (?, ?, ?, ?, ?, ?)" +
                    "ON CONFLICT ON CONSTRAINT hero_base_pkey DO " +
                    "UPDATE roster.hero_base SET health=?, mana=?, stamina=?, spawn_cost=? WHERE hero.hero_id=?";

    public static final String INSERT_NEW_ACCOUNT = "INSERT INTO store.account(player_id, credits) VALUES (?, ?)";

    public static final String INSERT_NEW_STATS_ACCOUNT = "INSERT INTO stats.account(player_id, stats_id) VALUES (?, ?)";

    public static final String INSERT_NEW_STATS = "INSERT INTO stats.stats DEFAULT VALUES";

    public static final String SELECT_STATS = "SELECT player_id, stats.experience, stats.kills, stats.deaths, stats.experience, stats.matches_won, stats.matches_lost, stats.resources " +
            "FROM stats.account " +
            "JOIN stats.stats " +
            "ON stats.stats_id = account.stats_id " +
            "WHERE player_id = ?";

    public static final String SELECT_CREDITS = "SELECT credits FROM store.account WHERE player_id = ?";
}
