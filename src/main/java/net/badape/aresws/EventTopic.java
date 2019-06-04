package net.badape.aresws;

import net.badape.aresws.actors.AccountActor;
import net.badape.aresws.actors.HeroStoreActor;

public final class EventTopic {
    public static final String GET_DEV_ACCOUNT = AccountActor.class.getCanonicalName() + ".dev.get";
    public static final String GET_DEVICE_ACCOUNT = AccountActor.class.getCanonicalName() + ".device.get";

    public static final String NEW_ACCOUNT = HeroStoreActor.class.getCanonicalName() + ".new";
    public static final String GET_ROSTER =  HeroStoreActor.class.getCanonicalName() + ".roster";
    public static final String BUY_HERO =  HeroStoreActor.class.getCanonicalName() + ".hero";
    public static final String GET_TEAM =  HeroStoreActor.class.getCanonicalName() + ".team";

}
