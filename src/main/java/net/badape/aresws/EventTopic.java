package net.badape.aresws;

import net.badape.aresws.services.AresAccount;
import net.badape.aresws.services.AresContent;
import net.badape.aresws.services.AresStore;

public final class EventTopic {
    public static final String GET_DEV_ACCOUNT = AresAccount.class.getCanonicalName() + ".dev.get";
    public static final String GET_DEVICE_ACCOUNT = AresAccount.class.getCanonicalName() + ".device.get";

    public static final String NEW_ACCOUNT = AresStore.class.getCanonicalName() + ".new";
    public static final String GET_ROSTER =  AresStore.class.getCanonicalName() + ".roster";
    public static final String BUY_HERO =  AresStore.class.getCanonicalName() + ".hero";
    public static final String GET_TEAM =  AresStore.class.getCanonicalName() + ".team";
    public static final String STORE_REFRESH =  AresStore.class.getCanonicalName() + ".refresh";
    public static final String GET_GAME_NEWS = AresContent.class.getCanonicalName() + ".news";
}
