package com.yny.utils.team;

public final class VerifyTeamFeed {
    public static void main(String[] args) throws Exception {
        if (!"https://ping.antiutm.com/allies.json".equals(SteinCombat.REMOTE_URL))
            throw new AssertionError("Remote URL changed");
        Feed feed = Feed.fetch(SteinCombat.REMOTE_URL);
        if (feed == null) throw new AssertionError("No feed");
        System.out.println("Live feed: allies=" + feed.allies.size()
                + ", allyClans=" + feed.allyClans.size()
                + ", enemies=" + feed.enemies.size()
                + ", enemyClans=" + feed.enemyClans.size());
    }
}