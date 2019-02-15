package net.badape.aresws.services;

public class SQL {

    public final static String SQL_CREATE_PLAYER = "INSERT INTO ares.players(credits) VALUES (?)";
    public final static String SQL_CREATE_DEV_PLAYER = "INSERT INTO ares.\"devPlayers\"(\"devId\", \"playerId\") VALUES (?, ?)";
    public final static String SQL_GET_DEVID = "SELECT \"devPlayers\".\"devId\", \"devPlayers\".\"playerId\", \"players\".\"credits\" FROM ares.\"devPlayers\" LEFT JOIN ares.players ON \"devPlayers\".\"playerId\"=\"players\".\"playerId\" WHERE \"devPlayers\".\"devId\" = ?";
    public final static String SQL_GET_PLAYER = "SELECT \"playerId\", credits, created FROM ares.players WHERE \"playerId\" = ?";
    public final static String SQL_ROSTER = "SELECT roster.heroId, roster FROM players " +
            "JOIN roster ON roster.playerId = players.playerId " +
            "WHERE players.steamId64 = ?";

    public final static String SQL_HERO_CREDITS = "SELECT credits, heroId FROM heroes WHERE gameId = ?";
    public final static String SQL_BUY_HERO = "UPDATE ares.players SET credits = credits - ? WHERE steamId64 = ?";
    public final static String SQL_HERO_DATA = "SELECT * FROM ares.heroes WHERE \"gameId\" = ?";

    public final static String SQL_ADD_ROSTER = "INSERT INTO ares.roster (playerId, heroId) " +
            "VALUES ((SELECT playerId FROM ares.players WHERE steamId64 = ?), ? );";
}
