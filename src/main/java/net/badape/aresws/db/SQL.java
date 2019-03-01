package net.badape.aresws.db;

public final class SQL {

    public final static String SELECT_PLAYER = "SELECT * FROM players.player WHERE player_id = ?";

    public final static String CREATE_PLAYER = "INSERT INTO players.player DEFAULT VALUES";

    public final static String CREATE_DEV_PLAYER = "INSERT INTO players.dev_player(dev_id, player_id) VALUES (?, ?)";

    public final static String SELECT_DEVID = "SELECT dev_player.dev_id, dev_player.player_id FROM " +
            "players.dev_player LEFT JOIN players.player ON dev_player.player_id=player.player_id WHERE dev_player.dev_id = ?";

    public final static String SELECT_ROSTER =
            "SELECT * FROM stats.roster JOIN stats.account ON roster.player_id = account.player_id " +
            "JOIN stats.hero_base ON roster.hero_id = hero_base.hero_id " +
            "WHERE account.player_id = ?";

    public final static String SELECT_HERO_CREDITS = "SELECT credits FROM store.hero WHERE hero_id = ?";

    public final static String UPDATE_BUY_HERO =
            "UPDATE store.account SET credits = credits - (SELECT credits FROM store.hero WHERE hero_id = ?) WHERE player_id = ?";

    public final static String SQL_ADD_ROSTER =
            "INSERT INTO stats.roster (player_id, hero_id) VALUES (?, ?)";

    public static final String SQL_HEROES = "SELECT * FROM store.hero;";

    public static final String UPSERT_HERO =
            "INSERT INTO store.hero(hero_id, game_id, credits, description) VALUES (?, ?, ?, ?)" +
            "ON CONFLICT ON CONSTRAINT hero_pkey DO " +
            "UPDATE SET game_id=?, credits=?, description=? " +
            "WHERE hero.hero_id=?";

    public static final String UPSERT_HERO_CONFIG =
            "INSERT INTO stats.hero_base(hero_id, game_id, health, mana, stamina, spawn_cost) VALUES (?, ?, ?, ?, ?, ?) " +
            "ON CONFLICT ON CONSTRAINT hero_base_pkey DO " +
            "UPDATE SET game_id=?, health=?, mana=?, stamina=?, spawn_cost=? " +
            "WHERE hero_base.hero_id=?";

    public static final String INSERT_NEW_ACCOUNT = "INSERT INTO store.account(player_id, credits) VALUES (?, ?)";

    public static final String INSERT_NEW_STATS_ACCOUNT = "INSERT INTO stats.account(player_id) VALUES (?)";

    public static final String SELECT_STATS = "SELECT * FROM stats.account WHERE player_id = ?";

    public static final String SELECT_CREDITS = "SELECT credits FROM store.account WHERE player_id = ?";
}
