package me.staartvin.statz.datamanager.player.specification;

import java.util.UUID;

public class TimePlayedSpecification extends PlayerStatSpecification {

    public TimePlayedSpecification(UUID uuid, int value, String worldName) {
        super();

        this.putInData(Keys.UUID.toString(), uuid);
        this.putInData(Keys.VALUE.toString(), value);
        this.putInData(Keys.WORLD.toString(), worldName);
    }

    @Override
    public boolean hasWorldSupport() {
        return true;
    }

    public enum Keys {UUID, VALUE, WORLD}
}
