package net.badape.aresws;

import net.badape.aresws.services.*;

public final class EventTopic {
    public static final String GET_EOS_ACCOUNT = AresAccount.class.getCanonicalName() + ".eos.get";

    public static final String NEW_ACCOUNT = AresStore.class.getCanonicalName() + ".new";
    public static final String GET_ROSTER =  AresStore.class.getCanonicalName() + ".roster";
    public static final String BUY_HERO =  AresStore.class.getCanonicalName() + ".hero";
    public static final String GET_TEAM =  AresStore.class.getCanonicalName() + ".team";
    public static final String HERO_REFRESH =  AresStore.class.getCanonicalName() + ".refresh";

    public static final String GET_FACTION_CONFIG = AresConfig.class.getCanonicalName() + ".faction";
    public static final String GET_CHARACTERS_CONFIG = AresConfig.class.getCanonicalName() + ".characters";

    public static final String PUT_EVENT = ElasticSearchClient.class.getCanonicalName() + ".put_event";
}
