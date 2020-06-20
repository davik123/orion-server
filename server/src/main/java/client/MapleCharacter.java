/*
This file is part of the OdinMS Maple Story Server
Copyright (C) 2008 ~ 2010 Patrick Huy <patrick.huy@frz.cc> 
Matthias Butz <matze@odinms.de>
Jan Christian Meyer <vimes@odinms.de>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License version 3
as published by the Free Software Foundation. You may not use, modify
or distribute this program under any other version of the
GNU Affero General Public License.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package client;

import client.anticheat.CheatTracker;
import client.anticheat.ReportType;
import client.inventory.*;
import constants.*;
import constants.skills.BladeLord;
import constants.skills.Rogue;
import database.DatabaseConnection;
import database.DatabaseException;
import handling.channel.ChannelServer;
import handling.channel.handler.utils.PartyHandlerUtils.PartyOperation;
import handling.world.*;
import handling.world.buddy.BuddyListEntry;
import handling.world.buddy.MapleBuddyList;
import handling.world.guild.MapleGuild;
import handling.world.guild.MapleGuildCharacter;
import handling.world.party.MapleParty;
import handling.world.party.MaplePartyCharacter;
import scripting.EventInstanceManager;
import scripting.NPCScriptManager;
import scripting.v1.event.EventInstance;
import server.*;
import server.Timer.BuffTimer;
import server.Timer.EtcTimer;
import server.Timer.MapTimer;
import server.cashShop.CashShop;
import server.life.MapleMonster;
import server.life.MobSkill;
import server.maps.*;
import server.quest.MapleQuest;
import server.shops.IMaplePlayerShop;
import server.state.MapleVar;
import server.state.SimpleMapleVar;
import tools.*;
import tools.packet.*;

import java.awt.*;
import java.io.Serializable;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MapleCharacter extends AbstractAnimatedMapleMapObject implements Serializable {

  private static final long serialVersionUID = 845748950829L;
  private String name, chalktext, BlessOfFairy_Origin;
  private long lastCombo, lastfametime, keydown_skill, loginTime, lastRecoveryTime, lastDragonBloodTime,
          lastBerserkTime, lastHPTime, lastMPTime, lastFairyTime;
  private byte dojoRecord, gmLevel, gender, initialSpawnPoint, skinColor, guildrank = 5, allianceRank = 5, world,
          fairyExp = 10, subcategory, mobKilledNo, portalCount = 0, morphId = 0;
  private short level, mulung_energy, combo, availableCP, totalCP, fame, hpApUsed, job;
  private int accountid, id, meso, exp, hair, face, mapid, bookCover, dojo, guildid = 0, fallcounter = 0, maplepoints,
          nxcredit, chair, itemEffect, points, rank = 1, rankMove = 0, jobRank = 1, jobRankMove = 0,
          marriageId, marriageItemId = 0, coconutteam = 0, followid = 0, battleshipHP = 0, remainingAp, remainingSp;
  private Point old = new Point(0, 0);
  private boolean smega, hidden, hasSummon = false;
  private int[] wishlist, rocks, savedLocations, regrocks;
  private transient AtomicInteger inst;
  private List<Integer> lastmonthfameids;
  private List<MapleDoor> doors;
  private List<MaplePet> pets;
  private transient Set<MapleMonster> controlled;
  private transient Set<MapleMapObject> visibleMapObjects;
  private transient ReentrantReadWriteLock visibleMapObjectsLock;
  private Map<MapleQuest, MapleQuestStatus> quests;
  private Map<Integer, String> questinfo;
  private Map<Integer, Integer> linkMobs = new LinkedHashMap<>();
  private Map<ISkill, SkillEntry> skills = new LinkedHashMap<ISkill, SkillEntry>();
  private transient Map<MapleBuffStat, MapleBuffStatValueHolder> effects = new ConcurrentEnumMap<MapleBuffStat, MapleBuffStatValueHolder>(
          MapleBuffStat.class);
  private transient Map<Integer, MapleSummon> summons;
  private transient Map<Integer, MapleCoolDownValueHolder> coolDowns = new LinkedHashMap<Integer, MapleCoolDownValueHolder>();
  private transient Map<MapleDisease, MapleDiseaseValueHolder> diseases = new ConcurrentEnumMap<MapleDisease, MapleDiseaseValueHolder>(
          MapleDisease.class);
  private Map<ReportType, Integer> reports = new EnumMap<>(ReportType.class);
  private CashShop cs;
  private transient Deque<MapleCarnivalChallenge> pendingCarnivalRequests;
  private transient MapleCarnivalParty carnivalParty;
  private MapleBuddyList buddylist;
  private MonsterBook monsterbook;
  private transient CheatTracker anticheat;
  private MapleClient client;
  private PlayerStats stats;
  private transient PlayerRandomStream CRand;
  private transient MapleMap map;
  private transient MapleShop shop;
  private transient MapleDragon dragon;
  private transient RockPaperScissors rps;
  private transient SpeedQuiz sq;
  private MapleStorage storage;
  private transient MapleTrade trade;
  private MapleMount mount;
  private List<Integer> finishedAchievements = new ArrayList<Integer>();
  private MapleMessenger messenger;
  private byte[] petStore;
  private transient IMaplePlayerShop playerShop;
  private MapleParty party;
  private boolean invincible = false, canTalk = true, followinitiator = false, followon = false;
  private MapleGuildCharacter mgc;
  private transient EventInstanceManager eventInstance;
  private MapleInventory[] inventory;
  private SkillMacro[] skillMacros = new SkillMacro[5];
  private MapleKeyLayout keylayout;
  private transient ScheduledFuture<?> beholderHealingSchedule, beholderBuffSchedule, mapTimeLimitTask, fishing;
  private long nextConsume = 0, pqStartTime = 0;
  private transient Event_PyramidSubway pyramidSubway = null;
  private transient List<Integer> pendingExpiration = null, pendingSkills = null, pendingUnlock = null;
  private transient Map<Integer, Integer> movedMobs = new HashMap<Integer, Integer>();
  private String teleportname = "";
  private boolean changed_wishlist, changed_trocklocations, changed_regrocklocations, changed_skillmacros,
          changed_achievements, changed_savedlocations, changed_questinfo, changed_skills, changed_reports;
  private int watk;
  private EvanSkillPoints evanSP;
  private static boolean autoSkill = false;

  private HashMap<String, Object> temporaryData = new HashMap<>();

  private long travelTime;

  private EventInstance newEventInstance;

  private MapleCharacter(final boolean ChannelServer) {
    setStance(0);
    setPosition(new Point(0, 0));

    inventory = new MapleInventory[MapleInventoryType.values().length];
    for (MapleInventoryType type : MapleInventoryType.values()) {
      inventory[type.ordinal()] = new MapleInventory(type);
    }
    quests = new LinkedHashMap<MapleQuest, MapleQuestStatus>();
    stats = new PlayerStats(this);
    if (ChannelServer) {
      changed_reports = false;
      changed_skills = false;
      changed_achievements = false;
      changed_wishlist = false;
      changed_trocklocations = false;
      changed_regrocklocations = false;
      changed_skillmacros = false;
      changed_savedlocations = false;
      changed_questinfo = false;
      lastCombo = 0;
      mulung_energy = 0;
      combo = 0;
      keydown_skill = 0;
      loginTime = 0;
      lastRecoveryTime = 0;
      lastDragonBloodTime = 0;
      lastBerserkTime = 0;
      lastHPTime = 0;
      lastMPTime = 0;
      lastFairyTime = 0;
      smega = true;
      petStore = new byte[3];
      for (int i = 0; i < petStore.length; i++) {
        petStore[i] = (byte) -1;
      }
      wishlist = new int[10];
      rocks = new int[10];
      regrocks = new int[5];

      inst = new AtomicInteger();
      inst.set(0); // 1 = NPC/ Quest, 2 = Duey, 3 = Hired Merch store, 4 =
      // Storage
      keylayout = new MapleKeyLayout();
      doors = new ArrayList<>();
      controlled = new LinkedHashSet<>();
      summons = new LinkedHashMap<>();
      visibleMapObjects = new LinkedHashSet<>();
      visibleMapObjectsLock = new ReentrantReadWriteLock();
      pendingCarnivalRequests = new LinkedList<>();

      savedLocations = new int[SavedLocationType.values().length];
      for (int i = 0; i < SavedLocationType.values().length; i++) {
        savedLocations[i] = -1;
      }
      questinfo = new LinkedHashMap<>();
      anticheat = new CheatTracker(this);
      pets = new ArrayList<>();
    }
  }

  public static MapleCharacter getDefault(final MapleClient client, final int type) {
    MapleCharacter ret = new MapleCharacter(false);
    ret.client = client;
    ret.map = null;
    ret.exp = 0;
    ret.gmLevel = 0;
    ret.job = (short) (type == 1 ? 0 : (type == 0 ? 1000 : (type == 3 ? 2001 : (type == 4 ? 3000 : 2000))));
    ret.meso = 0;
    ret.level = 1;
    ret.remainingAp = 0;
    ret.fame = 0;
    ret.accountid = client.getAccID();
    ret.buddylist = new MapleBuddyList((byte) 20);
    ret.stats.str = 12;
    ret.stats.dex = 5;
    ret.stats.int_ = 4;
    ret.stats.luk = 4;
    ret.stats.maxhp = 50;
    ret.stats.hp = 50;
    ret.stats.maxmp = 50;
    ret.stats.mp = 50;

    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps;
      ps = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
      ps.setInt(1, ret.accountid);
      ResultSet rs = ps.executeQuery();

      if (rs.next()) {
        ret.client.setAccountName(rs.getString("name"));
        ret.nxcredit = rs.getInt("nxCredit");
        ret.maplepoints = rs.getInt("mPoints");
        ret.points = rs.getInt("points");

      }
      rs.close();
      ps.close();
    } catch (SQLException e) {
      System.err.println("Error getting character default" + e);
    }
    return ret;
  }

  public final static MapleCharacter ReconstructChr(final CharacterTransfer ct, final MapleClient client,
                                                    final boolean isChannel) {
    final MapleCharacter ret = new MapleCharacter(true); // Always true,
    // it's change
    // channel
    ret.client = client;
    if (!isChannel) {
      ret.client.setChannel(ct.channel);
    }
    ret.id = ct.characterid;
    ret.name = ct.name;
    ret.level = ct.level;
    ret.fame = ct.fame;

    ret.CRand = new PlayerRandomStream();

    ret.changed_skillmacros = false;
    ret.stats.str = ct.str;
    ret.stats.dex = ct.dex;
    ret.stats.int_ = ct.int_;
    ret.stats.luk = ct.luk;
    ret.stats.maxhp = ct.maxhp;
    ret.stats.maxmp = ct.maxmp;
    ret.stats.hp = ct.hp;
    ret.stats.mp = ct.mp;

    ret.chalktext = ct.chalkboard;
    ret.exp = ct.exp;
    ret.hpApUsed = ct.hpApUsed;
    ret.remainingAp = ct.remainingAp;
    ret.remainingSp = ct.remainingSp;
    ret.meso = ct.meso;
    ret.gmLevel = ct.gmLevel;
    ret.skinColor = ct.skinColor;
    ret.gender = ct.gender;
    ret.job = ct.job;
    ret.hair = ct.hair;
    ret.face = ct.face;
    ret.accountid = ct.accountid;
    ret.mapid = ct.mapid;
    ret.initialSpawnPoint = ct.initialSpawnPoint;
    ret.world = ct.world;
    ret.bookCover = ct.mBookCover;
    ret.dojo = ct.dojo;
    ret.dojoRecord = ct.dojoRecord;
    ret.guildid = ct.guildid;
    ret.guildrank = ct.guildrank;
    ret.allianceRank = ct.alliancerank;
    ret.points = ct.points;
    ret.fairyExp = ct.fairyExp;
    ret.marriageId = ct.marriageId;
    ret.evanSP = ct.evanSP;

    if (ret.guildid > 0) {
      ret.mgc = new MapleGuildCharacter(ret);
    }
    ret.buddylist = new MapleBuddyList(ct.buddysize);
    ret.subcategory = ct.subcategory;

    if (isChannel) {
      final MapleMapFactory mapFactory = ChannelServer.getInstance(client.getChannel()).getMapFactory();
      ret.map = mapFactory.getMap(ret.mapid);
      if (ret.map == null) { // char is on a map that doesn't exist warp
        // it to henesys
        ret.map = mapFactory.getMap(100000000);
      } else {
        if (ret.map.getForcedReturnId() != 999999999) {
          ret.map = ret.map.getForcedReturnMap();
        }
      }
      MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
      if (portal == null) {
        portal = ret.map.getPortal(0); // char is on a spawnpoint that
        // doesn't exist - select the
        // first spawnpoint instead
        ret.initialSpawnPoint = 0;
      }
      ret.setPosition(portal.getPosition());

      final int messengerid = ct.messengerid;
      if (messengerid > 0) {
        ret.messenger = World.Messenger.getMessenger(messengerid);
      }
    } else {

      ret.messenger = null;
    }
    int partyid = ct.partyid;
    if (partyid >= 0) {
      MapleParty party = World.Party.getParty(partyid);
      if (party != null && party.getMemberById(ret.id) != null) {
        ret.party = party;
      }
    }

    MapleQuestStatus queststatus;
    MapleQuestStatus queststatus_from;
    MapleQuest quest;
    for (final Map.Entry<Integer, Object> qs : ct.Quest.entrySet()) {
      quest = MapleQuest.getInstance(qs.getKey());
      queststatus_from = (MapleQuestStatus) qs.getValue();

      queststatus = new MapleQuestStatus(quest, queststatus_from.getStatus());
      queststatus.setForfeited(queststatus_from.getForfeited());
      queststatus.setCustomData(queststatus_from.getCustomData());
      queststatus.setCompletionTime(queststatus_from.getCompletionTime());

      if (queststatus_from.getMobKills() != null) {
        for (final Map.Entry<Integer, Integer> mobkills : queststatus_from.getMobKills().entrySet()) {
          queststatus.setMobKills(mobkills.getKey(), mobkills.getValue());
        }
      }
      ret.quests.put(quest, queststatus);
    }

    for (final Map.Entry<Integer, SkillEntry> qs : ct.Skills.entrySet()) {
      ret.skills.put(SkillFactory.getSkill(qs.getKey()), qs.getValue());
    }
    for (final Integer zz : ct.finishedAchievements) {
      ret.finishedAchievements.add(zz);
    }
    for (final Map.Entry<Byte, Integer> qs : ct.reports.entrySet()) {
      ret.reports.put(ReportType.getById(qs.getKey()), qs.getValue());
    }
    ret.monsterbook = new MonsterBook(ct.mbook);
    ret.inventory = (MapleInventory[]) ct.inventorys;
    ret.BlessOfFairy_Origin = ct.BlessOfFairy;
    ret.skillMacros = (SkillMacro[]) ct.skillmacro;
    ret.keylayout = new MapleKeyLayout(ct.keymap);
    ret.petStore = ct.petStore;
    ret.questinfo = ct.InfoQuest;
    ret.savedLocations = ct.savedlocation;
    ret.wishlist = ct.wishlist;
    ret.rocks = ct.rocks;
    ret.regrocks = ct.regrocks;
    ret.buddylist.loadFromTransfer(ct.buddies);
    // ret.lastfametime
    // ret.lastmonthfameids
    ret.keydown_skill = 0; // Keydown skill can't be brought over
    ret.lastfametime = ct.lastfametime;
    ret.loginTime = ct.loginTime;
    ret.lastRecoveryTime = ct.lastRecoveryTime;
    ret.lastDragonBloodTime = ct.lastDragonBloodTime;
    ret.lastBerserkTime = ct.lastBerserkTime;
    ret.lastHPTime = ct.lastHPTime;
    ret.lastMPTime = ct.lastMPTime;
    ret.lastFairyTime = ct.lastFairyTime;
    ret.lastmonthfameids = ct.famedcharacters;
    ret.morphId = ct.morphId;
    ret.storage = (MapleStorage) ct.storage;
    ret.cs = (CashShop) ct.cs;
    client.setAccountName(ct.accountname);
    ret.nxcredit = ct.nxCredit;
    ret.maplepoints = ct.MaplePoints;
    ret.mount = new MapleMount(ret, ct.mount_itemid, GameConstants.getSkillByJob(1004, ret.job), ct.mount_Fatigue,
            ct.mount_level, ct.mount_exp);
    ret.expirationTask(false, false);
    ret.stats.recalcLocalStats(true);

    return ret;
  }

  public static MapleCharacter loadCharFromDB(int charid, MapleClient client, boolean channelserver) {
    final MapleCharacter ret = new MapleCharacter(channelserver);
    ret.client = client;
    ret.id = charid;

    PreparedStatement ps = null;
    PreparedStatement pse = null;
    ResultSet rs = null;

    try {
      Connection con = DatabaseConnection.getConnection();
      ps = con.prepareStatement("SELECT * FROM characters WHERE id = ?");
      ps.setInt(1, charid);
      rs = ps.executeQuery();
      if (!rs.next()) {
        throw new RuntimeException("Loading the Char Failed (char not found)");
      }

      ret.name = rs.getString("name");
      ret.level = rs.getShort("level");
      ret.fame = rs.getShort("fame");

      ret.stats.str = rs.getShort("str");
      ret.stats.dex = rs.getShort("dex");
      ret.stats.int_ = rs.getShort("int");
      ret.stats.luk = rs.getShort("luk");
      ret.stats.maxhp = rs.getInt("maxhp");
      ret.stats.maxmp = rs.getInt("maxmp");
      ret.stats.hp = rs.getInt("hp");
      ret.stats.mp = rs.getInt("mp");

      ret.exp = rs.getInt("exp");
      ret.hpApUsed = rs.getShort("hpApUsed");
      ret.remainingAp = rs.getInt("ap");
      ret.remainingSp = rs.getInt("sp");
      ret.meso = rs.getInt("meso");
      ret.gmLevel = rs.getByte("gm");
      ret.skinColor = rs.getByte("skincolor");
      ret.gender = rs.getByte("gender");
      ret.job = rs.getShort("job");
      ret.hair = rs.getInt("hair");
      ret.face = rs.getInt("face");
      ret.accountid = rs.getInt("accountid");
      ret.mapid = rs.getInt("map");
      ret.initialSpawnPoint = rs.getByte("spawnpoint");
      ret.world = rs.getByte("world");
      ret.guildid = rs.getInt("guildid");
      ret.guildrank = rs.getByte("guildrank");
      ret.allianceRank = rs.getByte("allianceRank");
      if (ret.guildid > 0) {
        ret.mgc = new MapleGuildCharacter(ret);
      }
      ret.buddylist = new MapleBuddyList(rs.getByte("buddyCapacity"));
      ret.subcategory = rs.getByte("subcategory");
      ret.mount = new MapleMount(ret, 0, GameConstants.getSkillByJob(1004, ret.job), (byte) 0, (byte) 1, 0);
      ret.rank = rs.getInt("rank");
      ret.rankMove = rs.getInt("rankMove");
      ret.jobRank = rs.getInt("jobRank");
      ret.jobRankMove = rs.getInt("jobRankMove");
      ret.marriageId = rs.getInt("marriageId");

      // Evan stuff
      loadEvanSkills(ret);
      //

      // random new stuff
      World.randomWorldStuff.addToLoggedOnSinceLastRestart(ret);

      if (channelserver) {
        MapleMapFactory mapFactory = ChannelServer.getInstance(client.getChannel()).getMapFactory();
        ret.map = mapFactory.getMap(ret.mapid);
        if (ret.map == null) { // char is on a map that doesn't exist
          // warp it to henesys
          ret.map = mapFactory.getMap(100000000);
        }
        MaplePortal portal = ret.map.getPortal(ret.initialSpawnPoint);
        if (portal == null) {
          portal = ret.map.getPortal(0); // char is on a spawnpoint
          // that doesn't exist -
          // select the first
          // spawnpoint instead
          ret.initialSpawnPoint = 0;
        }
        ret.setPosition(portal.getPosition());

        int partyid = rs.getInt("party");
        if (partyid >= 0) {
          MapleParty party = World.Party.getParty(partyid);
          if (party != null && party.getMemberById(ret.id) != null) {
            ret.party = party;
          }
        }
        ret.bookCover = rs.getInt("monsterbookcover");
        ret.dojo = rs.getInt("dojo_pts");
        ret.dojoRecord = rs.getByte("dojoRecord");
        String field = rs.getString("pets");
        final String[] petsArr = field.split("\\;");
        if (!field.isEmpty()) {
          for (int i = 0; i < petsArr.length; i++) {
            String petInventoryId = petsArr[i];
            if (petInventoryId.isEmpty()) {
              ret.petStore[i] = -1;
            } else {
              ret.petStore[i] = Byte.parseByte(petsArr[i]);
            }
          }
        }
        rs.close();
        ps.close();
        ps = con.prepareStatement("SELECT * FROM achievements WHERE accountid = ?");
        ps.setInt(1, ret.accountid);
        rs = ps.executeQuery();
        while (rs.next()) {
          ret.finishedAchievements.add(rs.getInt("achievementid"));
        }

        ps = con.prepareStatement("SELECT * FROM reports WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        while (rs.next()) {
          if (ReportType.getById(rs.getByte("type")) != null) {
            ret.reports.put(ReportType.getById(rs.getByte("type")), rs.getInt("count"));
          }
        }
      }
      rs.close();
      ps.close();

      ps = con.prepareStatement("SELECT * FROM queststatus WHERE characterid = ?");
      ps.setInt(1, charid);
      rs = ps.executeQuery();
      pse = con.prepareStatement("SELECT * FROM queststatusmobs WHERE queststatusid = ?");
      while (rs.next()) {
        final int id = rs.getInt("quest");
        final MapleQuest q = MapleQuest.getInstance(id);
        final MapleQuestStatus status = new MapleQuestStatus(q, rs.getByte("status"));
        final long cTime = rs.getLong("time");
        if (cTime > -1) {
          status.setCompletionTime(cTime * 1000);
        }
        status.setForfeited(rs.getInt("forfeited"));
        status.setCustomData(rs.getString("customData"));
        ret.quests.put(q, status);
        pse.setInt(1, rs.getInt("queststatusid"));
        final ResultSet rsMobs = pse.executeQuery();

        while (rsMobs.next()) {
          status.setMobKills(rsMobs.getInt("mob"), rsMobs.getInt("count"));
        }
        rsMobs.close();
      }
      rs.close();
      ps.close();
      pse.close();

      if (channelserver) {
        ret.CRand = new PlayerRandomStream();
        ret.monsterbook = MonsterBook.loadCards(charid);

        ps = con.prepareStatement("SELECT * FROM inventoryslot where characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();

        if (!rs.next()) {
          throw new RuntimeException("No Inventory slot column found in SQL. [inventoryslot]");
        } else {
          ret.getInventory(MapleInventoryType.EQUIP).setSlotLimit(rs.getByte("equip"));
          ret.getInventory(MapleInventoryType.USE).setSlotLimit(rs.getByte("use"));
          ret.getInventory(MapleInventoryType.SETUP).setSlotLimit(rs.getByte("setup"));
          ret.getInventory(MapleInventoryType.ETC).setSlotLimit(rs.getByte("etc"));
          ret.getInventory(MapleInventoryType.CASH).setSlotLimit(rs.getByte("cash"));
        }
        ps.close();
        rs.close();

        for (Pair<IItem, MapleInventoryType> mit : ItemLoader.INVENTORY.loadItems(false, charid).values()) {
          ret.getInventory(mit.getRight()).addFromDB(mit.getLeft());
          if (mit.getLeft().getPet() != null) {
            ret.pets.add(mit.getLeft().getPet());
          }
        }

        ps = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
        ps.setInt(1, ret.accountid);
        rs = ps.executeQuery();
        if (rs.next()) {
          ret.getClient().setAccountName(rs.getString("name"));
          ret.nxcredit = rs.getInt("nxCredit");
          ret.maplepoints = rs.getInt("mPoints");
          ret.points = rs.getInt("points");

          if (rs.getTimestamp("lastlogon") != null) {
            final Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(rs.getTimestamp("lastlogon").getTime());
            if (cal.get(Calendar.DAY_OF_WEEK) + 1 == Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
              ret.nxcredit += 500;
            }
          }
          rs.close();
          ps.close();

          ps = con.prepareStatement("UPDATE accounts SET lastlogon = CURRENT_TIMESTAMP() WHERE id = ?");
          ps.setInt(1, ret.accountid);
          ps.executeUpdate();
        } else {
          rs.close();
        }
        ps.close();

        ps = con.prepareStatement("SELECT * FROM questinfo WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();

        while (rs.next()) {
          ret.questinfo.put(rs.getInt("quest"), rs.getString("customData"));
        }
        rs.close();
        ps.close();

        loadAutoSkills(ret);

        // All these skills are only begginer skills (mounts, etc)
        ps = con.prepareStatement(
                "SELECT skillid, skilllevel, masterlevel, expiration FROM skills WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        ISkill skil;
        while (rs.next()) {
          skil = SkillFactory.getSkill(rs.getInt("skillid"));
          if (ret.skills.containsKey(skil)) {
            continue;
          }
          ret.skills.put(skil, new SkillEntry(rs.getByte("skilllevel"), rs.getByte("masterlevel"),
                  rs.getLong("expiration")));

          if (ServerEnvironment.isSkillSavingEnabled()) {
            System.out.println("Loading skill: " + skil.getName() + " Level: " + rs.getByte("skilllevel"));
          }
        }
        rs.close();
        ps.close();

        ret.expirationTask(false, true);

        // Bless of Fairy handling
        ps = con.prepareStatement("SELECT * FROM characters WHERE accountid = ? ORDER BY level DESC");
        ps.setInt(1, ret.accountid);
        rs = ps.executeQuery();
        byte maxlevel_ = 0;
        while (rs.next()) {
          if (rs.getInt("id") != charid) { // Not this character
            byte maxlevel = (byte) (rs.getShort("level") / 10);

            if (maxlevel > 20) {
              maxlevel = 20;
            }
            if (maxlevel > maxlevel_) {
              maxlevel_ = maxlevel;
              ret.BlessOfFairy_Origin = rs.getString("name");
            }

          }
        }
        ret.skills.put(SkillFactory.getSkill(GameConstants.getBOF_ForJob(ret.job)),
                new SkillEntry(maxlevel_, (byte) 0, -1));
        ps.close();
        rs.close();
        // END

        ps = con.prepareStatement("SELECT * FROM skillmacros WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        int position;
        while (rs.next()) {
          position = rs.getInt("position");
          SkillMacro macro = new SkillMacro(rs.getInt("skill1"), rs.getInt("skill2"), rs.getInt("skill3"),
                  rs.getString("name"), rs.getInt("shout"), position);
          ret.skillMacros[position] = macro;
        }
        rs.close();
        ps.close();

        ps = con.prepareStatement("SELECT `key`,`type`,`action`,`fixed` FROM keymap WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();

        final Map<Integer, Triple<Byte, Integer, Byte>> keyb = ret.keylayout.Layout();
        if (ServerEnvironment.isDebugEnabled()) {
          System.out.println("Loading key map...");
        }
        while (rs.next()) {
          int skill = rs.getInt("action");
          int key = rs.getInt("key");
          byte fixed = rs.getByte("fixed");
          byte type = rs.getByte("type");
          if (ServerEnvironment.isDebugEnabled()) {
            System.out.println("K: " + key + " fixed: " + fixed + " type: " + type + " skill: " + skill);
          }
          keyb.put(key, new Triple<>(type, skill, fixed));

        }
        rs.close();
        ps.close();

        ps = con.prepareStatement("SELECT `locationtype`,`map` FROM savedlocations WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        while (rs.next()) {
          ret.savedLocations[rs.getInt("locationtype")] = rs.getInt("map");
        }
        rs.close();
        ps.close();

        ps = con.prepareStatement(
                "SELECT `characterid_to`,`when` FROM famelog WHERE characterid = ? AND DATEDIFF(NOW(),`when`) < 30");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        ret.lastfametime = 0;
        ret.lastmonthfameids = new ArrayList<Integer>(31);
        while (rs.next()) {
          ret.lastfametime = Math.max(ret.lastfametime, rs.getTimestamp("when").getTime());
          ret.lastmonthfameids.add(Integer.valueOf(rs.getInt("characterid_to")));
        }
        rs.close();
        ps.close();

        ret.buddylist.loadFromDb(charid);
        ret.storage = MapleStorage.loadStorage(ret.accountid);
        ret.cs = new CashShop(ret.accountid, charid, ret.getJob());

        ps = con.prepareStatement("SELECT sn FROM wishlist WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        int i = 0;
        while (rs.next()) {
          ret.wishlist[i] = rs.getInt("sn");
          i++;
        }
        while (i < 10) {
          ret.wishlist[i] = 0;
          i++;
        }
        rs.close();
        ps.close();

        ps = con.prepareStatement("SELECT mapid FROM trocklocations WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        int r = 0;
        while (rs.next()) {
          ret.rocks[r] = rs.getInt("mapid");
          r++;
        }
        while (r < 10) {
          ret.rocks[r] = 999999999;
          r++;
        }
        rs.close();
        ps.close();

        ps = con.prepareStatement("SELECT mapid FROM regrocklocations WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        r = 0;
        while (rs.next()) {
          ret.regrocks[r] = rs.getInt("mapid");
          r++;
        }
        while (r < 5) {
          ret.regrocks[r] = 999999999;
          r++;
        }
        rs.close();
        ps.close();

        ps = con.prepareStatement("SELECT * FROM mountdata WHERE characterid = ?");
        ps.setInt(1, charid);
        rs = ps.executeQuery();
        if (!rs.next()) {
          throw new RuntimeException("No mount data found on SQL column");
        }
        final IItem mount = ret.getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -18);
        ret.mount = new MapleMount(ret, mount != null ? mount.getItemId() : 0,
                GameConstants.getSkillByJob(1004, ret.job), rs.getByte("Fatigue"), rs.getByte("Level"),
                rs.getInt("Exp"));
        ps.close();
        rs.close();

        ret.stats.recalcLocalStats(true);
      } else { // Not channel server
        for (Pair<IItem, MapleInventoryType> mit : ItemLoader.INVENTORY.loadItems(true, charid).values()) {
          ret.getInventory(mit.getRight()).addFromDB(mit.getLeft());
        }
      }
    } catch (SQLException ess) {
      ess.printStackTrace();
      System.out.println("Failed to load character..");
    } finally {
      try {
        if (ps != null) {
          ps.close();
        }
        if (rs != null) {
          rs.close();
        }
      } catch (SQLException ignore) {
      }
    }
    return ret;
  }

  private static void loadAutoSkills(final MapleCharacter ret) {
    if (autoSkill == true) {// TODO: Remove aran auto skill
      ret.skills.putAll(JobConstants.getSkillsFromJob(ret.getJobValue()));
    }
    if (ret.isEvan() && autoSkill == true) {
      ret.skills.putAll(JobConstants.getEvanSkills());
    }

    if (ret.gmLevel > 0) {
      ret.skills.putAll(JobConstants.getGMSkills());
    }
  }

  public static void saveNewCharToDB(final MapleCharacter chr, final int type, final boolean db) {

    Connection con = null;
    PreparedStatement ps = null;
    PreparedStatement pse = null;
    ResultSet rs = null;
    try {
      con = DatabaseConnection.getConnection();
      con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
      con.setAutoCommit(false);
      ps = con.prepareStatement("INSERT INTO characters (level, fame, str, dex, luk, `int`, exp, hp, mp, maxhp, maxmp, ap, gm, skincolor, gender, job, hair, face, map, meso, hpApUsed, spawnpoint, party, buddyCapacity, monsterbookcover, dojo_pts, dojoRecord, pets, subcategory, marriageId, accountid, name, world) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", DatabaseConnection.RETURN_GENERATED_KEYS);
      ps.setInt(1, 1);
      ps.setShort(2, (short) 0); // Fame
      final PlayerStats stat = chr.stats;
      ps.setShort(3, stat.getStr()); // Str
      ps.setShort(4, stat.getDex()); // Dex
      ps.setShort(5, stat.getInt()); // Int
      ps.setShort(6, stat.getLuk()); // Luk
      ps.setInt(7, 0); // EXP
      ps.setInt(8, stat.getHp()); // HP
      ps.setInt(9, stat.getMp());
      ps.setInt(10, stat.getMaxHp()); // MP
      ps.setInt(11, stat.getMaxMp());
      ps.setShort(12, (short) 0); // Remaining AP
      ps.setByte(13, (byte) 0); // GM Level
      ps.setByte(14, chr.skinColor);
      ps.setByte(15, chr.gender);
      ps.setShort(16, chr.job);
      ps.setInt(17, chr.hair);
      ps.setInt(18, chr.face);
      ps.setInt(19, type == 1 ? 0 : (type == 0 ? 130030000 : (type == 3 ? 900090000 : 914000000)));
      ps.setInt(20, chr.meso); // Meso
      ps.setShort(21, (short) 0); // HP ap used
      ps.setByte(22, (byte) 0); // Spawnpoint
      ps.setInt(23, -1); // Party
      ps.setByte(24, chr.buddylist.getCapacity()); // Buddylist
      ps.setInt(25, 0); // Monster book cover
      ps.setInt(26, 0); // Dojo
      ps.setInt(27, 0); // Dojo record
      ps.setString(28, "-1;-1;-1");
      ps.setInt(29, db ? 1 : 0);
      ps.setInt(30, 0);
      ps.setInt(31, chr.getAccountID());
      ps.setString(32, chr.name);
      ps.setByte(33, chr.world);
      ps.executeUpdate();

      rs = ps.getGeneratedKeys();
      if (rs.next()) {
        chr.id = rs.getInt(1);
      } else {
        throw new DatabaseException("Inserting char failed.");
      }
      ps.close();
      rs.close();

      ps = con.prepareStatement(
              "INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `time`, `forfeited`, `customData`) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)",
              DatabaseConnection.RETURN_GENERATED_KEYS);
      pse = con.prepareStatement("INSERT INTO queststatusmobs VALUES (DEFAULT, ?, ?, ?)");
      ps.setInt(1, chr.id);
      for (final MapleQuestStatus q : chr.quests.values()) {
        ps.setInt(2, q.getQuest().getId());
        ps.setInt(3, q.getStatus());
        ps.setInt(4, (int) (q.getCompletionTime() / 1000));
        ps.setInt(5, q.getForfeited());
        ps.setString(6, q.getCustomData());
        ps.executeUpdate();
        rs = ps.getGeneratedKeys();
        rs.next();

        if (q.hasMobKills()) {
          for (int mob : q.getMobKills().keySet()) {
            pse.setInt(1, rs.getInt(1));
            pse.setInt(2, mob);
            pse.setInt(3, q.getMobKills(mob));
            pse.executeUpdate();
          }
        }
        rs.close();
      }
      ps.close();
      pse.close();

      ps = con.prepareStatement(
              "INSERT INTO inventoryslot (characterid, `equip`, `use`, `setup`, `etc`, `cash`) VALUES (?, ?, ?, ?, ?, ?)");
      ps.setInt(1, chr.id);
      ps.setByte(2, (byte) 60); // Eq
      ps.setByte(3, (byte) 60); // Use
      ps.setByte(4, (byte) 60); // Setup
      ps.setByte(5, (byte) 60); // ETC
      ps.setByte(6, (byte) 60); // Cash
      ps.execute();
      ps.close();

      ps = con.prepareStatement(
              "INSERT INTO mountdata (characterid, `Level`, `Exp`, `Fatigue`) VALUES (?, ?, ?, ?)");
      ps.setInt(1, chr.id);
      ps.setByte(2, (byte) 1);
      ps.setInt(3, 0);
      ps.setByte(4, (byte) 0);
      ps.execute();
      ps.close();

      List<Pair<IItem, MapleInventoryType>> listing = new ArrayList<Pair<IItem, MapleInventoryType>>();
      for (final MapleInventory iv : chr.inventory) {
        for (final IItem item : iv.list()) {
          listing.add(new Pair<IItem, MapleInventoryType>(item, iv.getType()));
        }
      }
      ItemLoader.INVENTORY.saveItems(listing, con, chr.id);

      final int[] key = {2, 3, 4, 5, 6, 7, 8, 16, 17, 18, 19, 20, 23, 24, 25, 26, 27, 29, 31, 33, 34, 35, 37, 38,
              39, 40, 41, 43, 44, 45, 46, 48, 50, 56, 57, 59, 60, 61, 62, 63, 64, 65};
      final int[] KeyType = {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
              5, 5, 4, 4, 4, 5, 5, 6, 6, 6, 6, 6, 6, 6};
      final int[] action = {10, 12, 13, 18, 24, 21, 29, 8, 5, 0, 4, 28, 1, 25, 19, 14, 15, 52, 2, 26, 17, 11, 3,
              20, 27, 16, 23, 9, 50, 51, 6, 22, 7, 53, 54, 100, 101, 102, 103, 104, 105, 106};

      ps = con.prepareStatement("INSERT INTO keymap (characterid, `key`, `type`, `action`) VALUES (?, ?, ?, ?)");
      ps.setInt(1, chr.id);
      for (int i = 0; i < key.length; i++) {
        if (ExcludedKeyMap.fromKeyValue(action[i]) != null) {
          continue;
        }
        ps.setInt(2, key[i]);
        ps.setInt(3, KeyType[i]);
        ps.setInt(4, action[i]);
        ps.execute();
      }
      ps.close();

      con.commit();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("[charsave] Error saving character data");
      try {
        con.rollback();
      } catch (SQLException ex) {
        e.printStackTrace();
        System.err.println("[charsave] Error Rolling Back");
      }
    } finally {
      try {
        if (pse != null) {
          pse.close();
        }
        if (ps != null) {
          ps.close();
        }
        if (rs != null) {
          rs.close();
        }
        con.setAutoCommit(true);
        con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      } catch (SQLException e) {
        e.printStackTrace();
        System.err.println("[charsave] Error going back to autocommit mode");
      }
    }
  }

  public void saveToDB(boolean dc, boolean fromcs) {
    Connection con = null;
    PreparedStatement ps = null;
    PreparedStatement pse = null;
    ResultSet rs = null;
    String petPosition = "";
    for (MaplePet pet : pets) {
      petPosition += pet.getInventoryPosition() + ";";
    }
    try {
      con = DatabaseConnection.getConnection();
      con.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
      con.setAutoCommit(false);
      ps = con.prepareStatement(
              "UPDATE characters SET level = ?, fame = ?, str = ?, dex = ?, luk = ?, `int` = ?, exp = ?, hp = ?, mp = ?, maxhp = ?, maxmp = ?, ap = ?, gm = ?, skincolor = ?, gender = ?, job = ?, hair = ?, face = ?, map = ?, meso = ?, hpApUsed = ?, spawnpoint = ?, party = ?, buddyCapacity = ?, monsterbookcover = ?, dojo_pts = ?, dojoRecord = ?, pets = ?, subcategory = ?, marriageId = ?, name = ?, sp = ? WHERE id = ?",
              DatabaseConnection.RETURN_GENERATED_KEYS);
      ps.setInt(1, level);
      ps.setShort(2, fame);
      ps.setShort(3, stats.getStr());
      ps.setShort(4, stats.getDex());
      ps.setShort(5, stats.getLuk());
      ps.setShort(6, stats.getInt());
      ps.setInt(7, exp < 0 ? 0 : exp);
      ps.setInt(8, stats.getHp() < 1 ? 50 : stats.getHp());
      ps.setInt(9, stats.getMp());
      ps.setInt(10, stats.getMaxHp());
      ps.setInt(11, stats.getMaxMp());
      ps.setInt(12, remainingAp);
      ps.setByte(13, gmLevel);
      ps.setByte(14, skinColor);
      ps.setByte(15, gender);
      ps.setShort(16, job);
      ps.setInt(17, hair);
      ps.setInt(18, face);
      if (!fromcs && map != null) {
        if (map.getForcedReturnId() != 999999999) {
          ps.setInt(19, map.getForcedReturnId());
        } else {
          ps.setInt(19, stats.getHp() < 1 ? map.getReturnMapId() : map.getId());
        }
      } else {
        ps.setInt(19, mapid);
      }
      ps.setInt(20, meso);
      ps.setShort(21, hpApUsed);
      if (map == null) {
        ps.setByte(22, (byte) 0);
      } else {
        final MaplePortal closest = map.findClosestSpawnpoint(getPosition());
        ps.setByte(22, (byte) (closest != null ? closest.getId() : 0));
      }
      ps.setInt(23, party != null ? party.getId() : -1);
      ps.setShort(24, buddylist.getCapacity());
      ps.setInt(25, bookCover);
      ps.setInt(26, dojo);
      ps.setInt(27, dojoRecord);
      // TODO: remove this from character table pet thing
      ps.setString(28, petPosition);
      ps.setByte(29, subcategory);
      ps.setInt(30, marriageId);
      ps.setString(31, name);
      ps.setInt(32, remainingSp);
      ps.setInt(33, id);
      if (ps.executeUpdate() < 1) {
        ps.close();
        throw new DatabaseException("Character not in database (" + id + ")");
      }
      ps.close();

      if (changed_skillmacros) {
        deleteWhereCharacterId(con, "DELETE FROM skillmacros WHERE characterid = ?");
        for (int i = 0; i < 5; i++) {
          final SkillMacro macro = skillMacros[i];
          if (macro != null) {
            ps = con.prepareStatement(
                    "INSERT INTO skillmacros (characterid, skill1, skill2, skill3, name, shout, position) VALUES (?, ?, ?, ?, ?, ?, ?)");
            ps.setInt(1, id);
            ps.setInt(2, macro.getSkill1());
            ps.setInt(3, macro.getSkill2());
            ps.setInt(4, macro.getSkill3());
            ps.setString(5, macro.getName());
            ps.setInt(6, macro.getShout());
            ps.setInt(7, i);
            ps.execute();
            ps.close();
          }
        }
      }

      deleteWhereCharacterId(con, "DELETE FROM inventoryslot WHERE characterid = ?");
      ps = con.prepareStatement(
              "INSERT INTO inventoryslot (characterid, `equip`, `use`, `setup`, `etc`, `cash`) VALUES (?, ?, ?, ?, ?, ?)");
      ps.setInt(1, id);
      ps.setByte(2, getInventory(MapleInventoryType.EQUIP).getSlotLimit());
      ps.setByte(3, getInventory(MapleInventoryType.USE).getSlotLimit());
      ps.setByte(4, getInventory(MapleInventoryType.SETUP).getSlotLimit());
      ps.setByte(5, getInventory(MapleInventoryType.ETC).getSlotLimit());
      ps.setByte(6, getInventory(MapleInventoryType.CASH).getSlotLimit());
      ps.execute();
      ps.close();

      saveInventory(con);

      if (changed_questinfo) {
        deleteWhereCharacterId(con, "DELETE FROM questinfo WHERE characterid = ?");
        ps = con.prepareStatement(
                "INSERT INTO questinfo (`characterid`, `quest`, `customData`) VALUES (?, ?, ?)");
        ps.setInt(1, id);
        for (final Entry<Integer, String> q : questinfo.entrySet()) {
          ps.setInt(2, q.getKey());
          ps.setString(3, q.getValue());
          ps.execute();
        }
        ps.close();
      }

      deleteWhereCharacterId(con, "DELETE FROM queststatus WHERE characterid = ?");
      ps = con.prepareStatement(
              "INSERT INTO queststatus (`queststatusid`, `characterid`, `quest`, `status`, `time`, `forfeited`, `customData`) VALUES (DEFAULT, ?, ?, ?, ?, ?, ?)",
              DatabaseConnection.RETURN_GENERATED_KEYS);
      pse = con.prepareStatement("INSERT INTO queststatusmobs VALUES (DEFAULT, ?, ?, ?)");
      ps.setInt(1, id);
      for (final MapleQuestStatus q : quests.values()) {
        ps.setInt(2, q.getQuest().getId());
        ps.setInt(3, q.getStatus());
        ps.setInt(4, (int) (q.getCompletionTime() / 1000));
        ps.setInt(5, q.getForfeited());
        ps.setString(6, q.getCustomData());
        ps.executeUpdate();
        rs = ps.getGeneratedKeys();
        rs.next();

        if (q.hasMobKills()) {
          for (int mob : q.getMobKills().keySet()) {
            pse.setInt(1, rs.getInt(1));
            pse.setInt(2, mob);
            pse.setInt(3, q.getMobKills(mob));
            pse.executeUpdate();
          }
        }
        rs.close();
      }
      ps.close();
      pse.close();

      if (changed_skills) {
        deleteWhereCharacterId(con, "DELETE FROM skills WHERE characterid = ?");
        ps = con.prepareStatement(
                "INSERT INTO skills (characterid, skillid, skilllevel, masterlevel, expiration) VALUES (?, ?, ?, ?, ?)");
        ps.setInt(1, id);

        for (final Entry<ISkill, SkillEntry> skill : skills.entrySet()) {
          if (ServerEnvironment.isSkillSavingEnabled()) {
            System.out.println(
                    "Saving skill: " + skill.getKey().getName() + " Level: " + skill.getValue().skillevel);
          }

          int skillId = skill.getKey().getId();
          ps.setInt(2, skillId);
          ps.setByte(3, skill.getValue().skillevel);
          ps.setByte(4, skill.getValue().masterlevel);
          ps.setLong(5, skill.getValue().expiration);
          ps.execute();
        }
        ps.close();
      }
      if (isEvan()) {
        deleteWhereCharacterId(con, "DELETE FROM evan_skillpoints where characterid = ?");
        ps = con.prepareStatement(this.evanSP.prepareSkillQuery(this.id));
        ps.executeUpdate();
        ps.close();
      }
      List<MapleCoolDownValueHolder> cd = getCooldowns();
      if (dc && cd.size() > 0) {
        ps = con.prepareStatement(
                "INSERT INTO skills_cooldowns (charid, SkillID, StartTime, length) VALUES (?, ?, ?, ?)");
        ps.setInt(1, getId());
        for (final MapleCoolDownValueHolder cooling : cd) {
          ps.setInt(2, cooling.skillId);
          ps.setLong(3, cooling.startTime);
          ps.setLong(4, cooling.length);
          ps.execute();
        }
        ps.close();
      }

      if (changed_savedlocations) {
        deleteWhereCharacterId(con, "DELETE FROM savedlocations WHERE characterid = ?");
        ps = con.prepareStatement(
                "INSERT INTO savedlocations (characterid, `locationtype`, `map`) VALUES (?, ?, ?)");
        ps.setInt(1, id);
        for (final SavedLocationType savedLocationType : SavedLocationType.values()) {
          if (savedLocations[savedLocationType.getValue()] != -1) {
            ps.setInt(2, savedLocationType.getValue());
            ps.setInt(3, savedLocations[savedLocationType.getValue()]);
            ps.execute();
          }
        }
        ps.close();
      }

      if (changed_achievements) {
        ps = con.prepareStatement("DELETE FROM achievements WHERE accountid = ?");
        ps.setInt(1, accountid);
        ps.executeUpdate();
        ps.close();
        ps = con.prepareStatement("INSERT INTO achievements(charid, achievementid, accountid) VALUES(?, ?, ?)");
        for (Integer achid : finishedAchievements) {
          ps.setInt(1, id);
          ps.setInt(2, achid);
          ps.setInt(3, accountid);
          ps.executeUpdate();
        }
        ps.close();
      }

      if (changed_reports) {
        deleteWhereCharacterId(con, "DELETE FROM reports WHERE characterid = ?");
        ps = con.prepareStatement("INSERT INTO reports VALUES(DEFAULT, ?, ?, ?)");
        for (Map.Entry<ReportType, Integer> achid : reports.entrySet()) {
          ps.setInt(1, id);
          ps.setByte(2, achid.getKey().i);
          ps.setInt(3, achid.getValue());
          ps.execute();
        }
        ps.close();
      }

      deleteWhereCharacterId(con, "DELETE FROM `buddyentries` WHERE `owner` = ?");
      ps = con.prepareStatement("INSERT INTO `buddyentries` (owner, `buddyid`,`groupName`) VALUES (?, ?, ?)");
      ps.setInt(1, id);
      for (BuddyListEntry entry : buddylist.getBuddies()) {
        ps.setInt(2, entry.getCharacterId());
        ps.setString(3, entry.getGroup());
        ps.addBatch();
      }
      ps.executeBatch();
      ps.close();

      ps = con.prepareStatement("UPDATE accounts SET `nxCredit` = ?, `mPoints` = ?, `points` = ? WHERE id = ?");
      ps.setInt(1, nxcredit);
      ps.setInt(2, maplepoints);
      ps.setInt(3, points);
      ps.setInt(4, client.getAccID());
      ps.execute();
      ps.close();

      if (storage != null) {
        storage.saveToDB();
      }
      if (cs != null) {
        cs.save();
      }
      keylayout.saveKeys(id, this);
      mount.saveMount(id);
      monsterbook.saveCards(id);

      if (changed_wishlist) {
        deleteWhereCharacterId(con, "DELETE FROM wishlist WHERE characterid = ?");
        for (int i = 0; i < getWishlistSize(); i++) {
          ps = con.prepareStatement("INSERT INTO wishlist(characterid, sn) VALUES(?, ?) ");
          ps.setInt(1, getId());
          ps.setInt(2, wishlist[i]);
          ps.execute();
          ps.close();
        }
      }

      if (changed_trocklocations) {
        deleteWhereCharacterId(con, "DELETE FROM trocklocations WHERE characterid = ?");
        for (int i = 0; i < rocks.length; i++) {
          if (rocks[i] != 999999999) {
            ps = con.prepareStatement("INSERT INTO trocklocations(characterid, mapid) VALUES(?, ?) ");
            ps.setInt(1, getId());
            ps.setInt(2, rocks[i]);
            ps.execute();
            ps.close();
          }
        }
      }

      if (changed_regrocklocations) {
        deleteWhereCharacterId(con, "DELETE FROM regrocklocations WHERE characterid = ?");
        for (int i = 0; i < regrocks.length; i++) {
          if (regrocks[i] != 999999999) {
            ps = con.prepareStatement("INSERT INTO regrocklocations(characterid, mapid) VALUES(?, ?) ");
            ps.setInt(1, getId());
            ps.setInt(2, regrocks[i]);
            ps.execute();
            ps.close();
          }
        }
      }

      changed_wishlist = false;
      changed_trocklocations = false;
      changed_regrocklocations = false;
      changed_skillmacros = false;
      changed_savedlocations = false;
      changed_questinfo = false;
      changed_achievements = false;
      changed_skills = false;
      changed_reports = false;

      con.commit();
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println(MapleClient.getLogMessage(this, "[charsave] Error saving character data") + e);
      try {
        con.rollback();
      } catch (SQLException ex) {
        System.err.println(MapleClient.getLogMessage(this, "[charsave] Error Rolling Back") + e);
      }
    } finally {
      try {
        if (ps != null) {
          ps.close();
        }
        if (pse != null) {
          pse.close();
        }
        if (rs != null) {
          rs.close();
        }
        con.setAutoCommit(true);
        con.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      } catch (SQLException e) {
        System.err
                .println(MapleClient.getLogMessage(this, "[charsave] Error going back to autocommit mode") + e);
      }
    }
  }

  private void deleteWhereCharacterId(Connection con, String sql) throws SQLException {
    deleteWhereCharacterId(con, sql, id);
  }

  public static void deleteWhereCharacterId(Connection con, String sql, int id) throws SQLException {
    PreparedStatement ps = con.prepareStatement(sql);
    ps.setInt(1, id);
    ps.executeUpdate();
    ps.close();
  }

  public static void deleteWhereCharacterName(Connection con, String sql, String name) throws SQLException {
    PreparedStatement ps = con.prepareStatement(sql);
    ps.setString(1, name);
    ps.executeUpdate();
    ps.close();
  }

  public void saveInventory(final Connection con) throws SQLException {
    List<Pair<IItem, MapleInventoryType>> listing = new ArrayList<Pair<IItem, MapleInventoryType>>();
    for (final MapleInventory iv : inventory) {
      for (final IItem item : iv.list()) {
        listing.add(new Pair<IItem, MapleInventoryType>(item, iv.getType()));
      }
    }
    if (con != null) {
      ItemLoader.INVENTORY.saveItems(listing, con, id);
    } else {
      ItemLoader.INVENTORY.saveItems(listing, id);
    }
  }

  public final PlayerStats getStat() {
    return stats;
  }

  public final PlayerRandomStream CRand() {
    return CRand;
  }

  public final void QuestInfoPacket(final tools.data.output.MaplePacketLittleEndianWriter mplew) {
    mplew.writeShort(questinfo.size());

    for (final Entry<Integer, String> q : questinfo.entrySet()) {
      mplew.writeShort(q.getKey());
      mplew.writeMapleAsciiString(q.getValue() == null ? "" : q.getValue());
    }
  }

  public final void updateInfoQuest(final int questid, final String data) {
    questinfo.put(questid, data);
    changed_questinfo = true;
    client.getSession().write(MaplePacketCreator.updateInfoQuest(questid, data));
  }

  public final String getInfoQuest(final int questid) {
    if (questinfo.containsKey(questid)) {
      return questinfo.get(questid);
    }
    return "";
  }

  public final int getNumQuest() {
    int i = 0;
    for (final MapleQuestStatus q : quests.values()) {
      if (q.getStatus() == 2) {
        i++;
      }
    }
    return i;
  }

  public final byte getQuestStatus(final int quest) {
    return getQuest(MapleQuest.getInstance(quest)).getStatus();
  }

  public final MapleQuestStatus getQuest(final MapleQuest quest) {
    if (!quests.containsKey(quest)) {
      return new MapleQuestStatus(quest, (byte) 0);
    }
    return quests.get(quest);
  }

  public final void completeQuest(int id, int npc) {
    MapleQuest.getInstance(id).complete(this, npc);
  }

  public final void setQuestAdd(final MapleQuest quest, final byte status, final String customData) {
    if (!quests.containsKey(quest)) {
      final MapleQuestStatus stat = new MapleQuestStatus(quest, status);
      stat.setCustomData(customData);
      quests.put(quest, stat);
    }
  }

  public final MapleQuestStatus getQuestNAdd(final MapleQuest quest) {
    if (!quests.containsKey(quest)) {
      final MapleQuestStatus status = new MapleQuestStatus(quest, (byte) 0);
      quests.put(quest, status);
      return status;
    }
    return quests.get(quest);
  }

  public final MapleQuestStatus getQuestNoAdd(final MapleQuest quest) {
    return quests.get(quest);
  }

  public final void updateQuest(final MapleQuestStatus quest) {
    updateQuest(quest, false);
  }

  public final void updateQuest(final MapleQuestStatus quest, final boolean update) {
    quests.put(quest.getQuest(), quest);
    client.getSession().write(CWVsContextOnMessagePackets.onQuestRecordMessage(quest));
    if (quest.getStatus() == 1 && !update) {
      client.getSession().write(
              MaplePacketCreator.updateQuestInfo(this, quest.getQuest().getId(), quest.getNpc(), (byte) 8));
    }
  }

  public final Map<Integer, String> getInfoQuest_Map() {
    return questinfo;
  }

  public final Map<MapleQuest, MapleQuestStatus> getQuest_Map() {
    return quests;
  }

  public boolean isActiveBuffedValue(int skillid) {
    LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
        return true;
      }
    }
    return false;
  }

  public Integer getBuffedValue(MapleBuffStat effect) {
    if (effect == MapleBuffStat.MORPH && morphId > 0) {
      return (int) morphId;
    }
    final MapleBuffStatValueHolder mbsvh = effects.get(effect);
    return mbsvh == null ? null : Integer.valueOf(mbsvh.value);
  }

  public final Integer getBuffedSkill_X(final MapleBuffStat effect) {
    final MapleBuffStatValueHolder mbsvh = effects.get(effect);
    if (mbsvh == null) {
      return null;
    }
    return mbsvh.effect.getX();
  }

  public final Integer getBuffedSkill_Y(final MapleBuffStat effect) {
    final MapleBuffStatValueHolder mbsvh = effects.get(effect);
    if (mbsvh == null) {
      return null;
    }
    return mbsvh.effect.getY();
  }

  public int getTrueBuffSource(MapleBuffStat stat) {
    final MapleBuffStatValueHolder mbsvh = effects.get(stat);
    return mbsvh == null ? -1 : (mbsvh.effect.isSkill() ? mbsvh.effect.getSourceId() : -mbsvh.effect.getSourceId());
  }

  public boolean isBuffFrom(MapleBuffStat stat, ISkill skill) {
    final MapleBuffStatValueHolder mbsvh = effects.get(stat);
    if (mbsvh == null) {
      return false;
    }
    return mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skill.getId();
  }

  public int getBuffSource(MapleBuffStat stat) {
    final MapleBuffStatValueHolder mbsvh = effects.get(stat);
    return mbsvh == null ? -1 : mbsvh.effect.getSourceId();
  }

  /*
   * public final byte getFactionId() { return faction.getTeamId(); }
   */

  public int getItemQuantity(int itemid, boolean checkEquipped) {
    int possesed = inventory[GameConstants.getInventoryType(itemid).ordinal()].countById(itemid);
    if (checkEquipped) {
      possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
    }
    return possesed;
  }

  public void setBuffedValue(MapleBuffStat effect, int value) {
    final MapleBuffStatValueHolder mbsvh = effects.get(effect);
    if (mbsvh == null) {
      return;
    }
    mbsvh.value = value;
  }

  public Long getBuffedStarttime(MapleBuffStat effect) {
    final MapleBuffStatValueHolder mbsvh = effects.get(effect);
    return mbsvh == null ? null : Long.valueOf(mbsvh.startTime);
  }

  public MapleStatEffect getStatForBuff(MapleBuffStat effect) {
    final MapleBuffStatValueHolder mbsvh = effects.get(effect);
    return mbsvh == null ? null : mbsvh.effect;
  }

  public void startMapTimeLimitTask(int time, final MapleMap to) {
    client.getSession().write(MaplePacketCreator.getClock(time));

    time *= 1000;
    mapTimeLimitTask = MapTimer.getInstance().register(new Runnable() {

      @Override
      public void run() {
        changeMap(to, to.getPortal(0));
      }
    }, time, time);
  }

  public void startFishingTask(final boolean VIP) {
    final int time = GameConstants.getFishingTime(VIP, isGM());
    cancelFishingTask();

    fishing = EtcTimer.getInstance().register(new Runnable() { // no real
      // reason
      // for
      // clone.

      @Override
      public void run() {
        final boolean expMulti = haveItem(2300001, 1, false, true);
        if (!expMulti && !haveItem(2300000, 1, false, true)) {
          cancelFishingTask();
          return;
        }
        MapleInventoryManipulator.removeById(client, MapleInventoryType.USE, expMulti ? 2300001 : 2300000, 1,
                false, false);

        final int randval = RandomRewards.getInstance().getFishingReward();

        switch (randval) {
          case 0: // Meso
            final int money = Randomizer.rand(expMulti ? 15 : 10, expMulti ? 75000 : 50000);
            gainMeso(money, true);
            // client.getSession().write(UIPacket.fishingUpdate((byte)
            // 1, money));
            break;
          case 1: // EXP
            final int experi = Randomizer
                    .nextInt(Math.abs(GameConstants.getExpNeededForLevel(level) / 200) + 1);
            gainExp(expMulti ? (experi * 3 / 2) : experi, true, false, true);
            break;
          default:
            MapleInventoryManipulator.addById(client, randval, (short) 1, "");
            break;
        }
      }
    }, time, time);
  }

  public void cancelMapTimeLimitTask() {
    if (mapTimeLimitTask != null) {
      mapTimeLimitTask.cancel(false);
    }
  }

  public void cancelFishingTask() {
    if (fishing != null) {
      fishing.cancel(false);
    }
  }

  public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule) {
    registerEffect(effect, starttime, schedule, effect.getStatups());
  }

  public void registerEffect(MapleStatEffect effect, long starttime, ScheduledFuture<?> schedule,
                             List<Pair<MapleBuffStat, Integer>> statups) {
    if (effect.isHide()) {
      if (isGM()) {
        this.hidden = true;
        client.getSession().write(MaplePacketCreator.GameMaster_Func(0x12, 1));
        map.broadcastNONGMMessage(this, MaplePacketCreator.removePlayerFromMap(getId()));
      } else {
        FileoutputUtil.logUsers(getName(), "TRIED TO USE GM HIDE");
      }
    } else if (effect.isDragonBlood()) {
      prepareDragonBlood();
    } else if (effect.isBerserk()) {
      checkBerserk();
    } else if (effect.isMonsterRiding_()) {
      getMount().startSchedule();
    } else if (effect.isRecovery()) {
      prepareRecovery();
    } else if (effect.isBeholder()) {
      prepareBeholderEffect();
    }
    int clonez = 0;
    for (Pair<MapleBuffStat, Integer> statup : statups) {
      if (statup.getLeft() == MapleBuffStat.ILLUSION) {
        clonez = statup.getRight();
      }
      int value = statup.getRight().intValue();
      if (statup.getLeft() == MapleBuffStat.MONSTER_RIDING && effect.getSourceId() == 5221006) {
        if (battleshipHP <= 0) {// quick hack
          battleshipHP = value; // copy this as well
        }
      }
      effects.put(statup.getLeft(), new MapleBuffStatValueHolder(effect, starttime, schedule, value));
    }

    stats.recalcLocalStats();
    // System.out.println("Effect registered. Effect: " +
    // effect.getSourceId());
  }

  public List<MapleBuffStat> getBuffStats(final MapleStatEffect effect, final long startTime) {
    final List<MapleBuffStat> bstats = new ArrayList<MapleBuffStat>();
    final Map<MapleBuffStat, MapleBuffStatValueHolder> allBuffs = new EnumMap<MapleBuffStat, MapleBuffStatValueHolder>(
            effects);
    for (Entry<MapleBuffStat, MapleBuffStatValueHolder> stateffect : allBuffs.entrySet()) {
      final MapleBuffStatValueHolder mbsvh = stateffect.getValue();
      if (mbsvh.effect.sameSource(effect) && (startTime == -1 || startTime == mbsvh.startTime)) {
        bstats.add(stateffect.getKey());
      }
    }
    return bstats;
  }

  private boolean deregisterBuffStats(List<MapleBuffStat> stats) {
    boolean clonez = false;
    List<MapleBuffStatValueHolder> effectsToCancel = new ArrayList<MapleBuffStatValueHolder>(stats.size());
    for (MapleBuffStat stat : stats) {
      final MapleBuffStatValueHolder mbsvh = effects.remove(stat);
      if (mbsvh != null) {
        boolean addMbsvh = true;
        for (MapleBuffStatValueHolder contained : effectsToCancel) {
          if (mbsvh.startTime == contained.startTime && contained.effect == mbsvh.effect) {
            addMbsvh = false;
          }
        }
        if (addMbsvh) {
          effectsToCancel.add(mbsvh);
        }
        if (stat == MapleBuffStat.SUMMON || stat == MapleBuffStat.PUPPET || stat == MapleBuffStat.REAPER) {
          final int summonId = mbsvh.effect.getSourceId();
          final MapleSummon summon = summons.get(summonId);
          if (summon != null) {
            map.broadcastMessage(MaplePacketCreator.removeSummon(summon, true));
            map.removeMapObject(summon);
            removeVisibleMapObject(summon);
            summons.remove(summonId);
            if (summon.getSkill() == 1321007) {
              if (beholderHealingSchedule != null) {
                beholderHealingSchedule.cancel(false);
                beholderHealingSchedule = null;
              }
              if (beholderBuffSchedule != null) {
                beholderBuffSchedule.cancel(false);
                beholderBuffSchedule = null;
              }
            }
          }
        } else if (stat == MapleBuffStat.DRAGONBLOOD) {
          lastDragonBloodTime = 0;
        } else if (stat == MapleBuffStat.RECOVERY || mbsvh.effect.getSourceId() == 35121005) {
          lastRecoveryTime = 0;
        } else if (stat == MapleBuffStat.HOMING_BEACON) {
          linkMobs.clear();
        } else if (stat == MapleBuffStat.ILLUSION) {
          clonez = true;
        } else if (stat == MapleBuffStat.MORPH) {
          if (morphId > 0) {
            this.morphId = 0;
          }
        }
      }
    }
    for (MapleBuffStatValueHolder cancelEffectCancelTasks : effectsToCancel) {
      if (getBuffStats(cancelEffectCancelTasks.effect, cancelEffectCancelTasks.startTime).size() == 0) {
        if (cancelEffectCancelTasks.schedule != null) {
          cancelEffectCancelTasks.schedule.cancel(false);
        }
      }
    }
    return clonez;
  }

  /**
   * @param effect
   * @param overwrite when overwrite is set no data is sent and all the Buffstats in
   *                  the StatEffect are deregistered
   * @param startTime
   */
  public void cancelEffect(final MapleStatEffect effect, final boolean overwrite, final long startTime) {
    cancelEffect(effect, overwrite, startTime, effect.getStatups());
  }

  public void cancelEffect(final MapleStatEffect effect, final boolean overwrite, final long startTime,
                           List<Pair<MapleBuffStat, Integer>> statups) {
    List<MapleBuffStat> buffstats;
    if (!overwrite) {
      buffstats = getBuffStats(effect, startTime);
    } else {
      buffstats = new ArrayList<MapleBuffStat>(statups.size());
      for (Pair<MapleBuffStat, Integer> statup : statups) {
        buffstats.add(statup.getLeft());
      }
    }
    if (buffstats.size() <= 0) {
      return;
    }
    deregisterBuffStats(buffstats);
    if (effect.isMagicDoor()) {
      if (!getDoors().isEmpty()) {
        removeDoor();
        silentPartyUpdate();
      }
    } else if (effect.isMonsterRiding_()) {
      getMount().cancelSchedule();
    } else if (effect.isAranCombo()) {
      combo = 0;
    }
    // check if we are still logged in o.o
    if (!overwrite) {
      cancelPlayerBuffs(buffstats);
      if (client.getChannelServer().getPlayerStorage().getCharacterById(this.getId()) != null) {
        this.hidden = false;
        client.getSession().write(MaplePacketCreator.GameMaster_Func(0x12, 0));
        map.broadcastMessage(this, MaplePacketCreator.spawnPlayerMapobject(this), false);

        for (final MaplePet pet : pets) {
          if (pet.getSummoned()) {
            map.broadcastMessage(this, PetPacket.showPet(this, pet, false, false), false);
          }
        }

      }
    }
    // System.out.println("Effect deregistered. Effect: " +
    // effect.getSourceId());
  }

  public void cancelBuffStats(MapleBuffStat... stat) {
    List<MapleBuffStat> buffStatList = Arrays.asList(stat);
    deregisterBuffStats(buffStatList);
    cancelPlayerBuffs(buffStatList);
  }

  public void cancelEffectFromBuffStat(MapleBuffStat stat) {
    if (effects.get(stat) != null) {
      cancelEffect(effects.get(stat).effect, false, -1);
    }
  }

  private void cancelPlayerBuffs(List<MapleBuffStat> buffstats) {
    boolean write = client.getChannelServer().getPlayerStorage().getCharacterById(getId()) != null;
    if (buffstats.contains(MapleBuffStat.MONSTER_RIDING) && GameConstants.isEvan(getJob()) && getJob() >= 2200) {
      makeDragon();
      map.spawnDragon(dragon);
      map.updateMapObjectVisibility(this, dragon);
    }
    if (buffstats.contains(MapleBuffStat.HOMING_BEACON)) {
      if (write) {
        client.getSession().write(MaplePacketCreator.cancelHoming());
      }
    } else {
      if (write) {
        stats.recalcLocalStats();
      }
      client.getSession().write(MaplePacketCreator.cancelBuff(buffstats));
      map.broadcastMessage(this, MaplePacketCreator.cancelForeignBuff(getId(), buffstats), false);
    }
  }

  public void dispel() {
    if (!isHidden()) {
      final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(
              effects.values());
      for (MapleBuffStatValueHolder mbsvh : allBuffs) {
        if (mbsvh.effect.isSkill() && mbsvh.schedule != null && !mbsvh.effect.isMorph()) {
          cancelEffect(mbsvh.effect, false, mbsvh.startTime);
        }
      }
    }
  }

  public void dispelSkill(int skillid) {
    final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(
            effects.values());

    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      if (skillid == 0) {
        if (mbsvh.effect.isSkill()
                && (mbsvh.effect.getSourceId() == 4331003 || mbsvh.effect.getSourceId() == 4331002
                || mbsvh.effect.getSourceId() == 4341002 || mbsvh.effect.getSourceId() == 22131001
                || mbsvh.effect.getSourceId() == 1321007 || mbsvh.effect.getSourceId() == 2121005
                || mbsvh.effect.getSourceId() == 2221005 || mbsvh.effect.getSourceId() == 2311006
                || mbsvh.effect.getSourceId() == 2321003 || mbsvh.effect.getSourceId() == 3111002
                || mbsvh.effect.getSourceId() == 3111005 || mbsvh.effect.getSourceId() == 3211002
                || mbsvh.effect.getSourceId() == 3211005 || mbsvh.effect.getSourceId() == 4111002)) {
          cancelEffect(mbsvh.effect, false, mbsvh.startTime);
          break;
        }
      } else {
        if (mbsvh.effect.isSkill() && mbsvh.effect.getSourceId() == skillid) {
          cancelEffect(mbsvh.effect, false, mbsvh.startTime);
          break;
        }
      }
    }
  }

  public void dispelBuff(int skillid) {
    final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(
            effects.values());

    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      if (mbsvh.effect.getSourceId() == skillid) {
        cancelEffect(mbsvh.effect, false, mbsvh.startTime);
        break;
      }
    }
  }

  public void cancelAllBuffs_() {
    effects.clear();
  }

  public void cancelAllBuffs() {
    final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(
            effects.values());

    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      cancelEffect(mbsvh.effect, false, mbsvh.startTime);
    }
  }

  public void cancelMorphs() {
    cancelMorphs(false);
  }

  public void cancelMorphs(boolean force) {
    final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(
            effects.values());

    boolean questBuff = false;
    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      switch (mbsvh.effect.getSourceId()) {
        case 5111005:
        case 5121003:
        case 15111002:
        case 13111005:
          return; // Since we can't have more than 1, save up on loops
        case 2210062:
        case 2210063:
        case 2210064:
        case 2210065:
          questBuff = true;
          // fall through
        default:
          if (mbsvh.effect.isMorph()) {
            if (questBuff && MapConstants.isStorylineMap(getMapId()) && !force) {
              return;
            }
            if (questBuff) {
              for (int i = 1066; i <= 1067; i++) {
                final ISkill skill = SkillFactory.getSkill(GameConstants.getSkillByJob(i, getJob()));
                changeSkillLevel_Skip(skill, (byte) -1, (byte) 0);
              }
            }
            cancelEffect(mbsvh.effect, false, mbsvh.startTime);
            return;
          }
      }
    }
  }

  public int getMorphState() {
    LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      if (mbsvh.effect.isMorph()) {
        return mbsvh.effect.getSourceId();
      }
    }
    return -1;
  }

  public void silentGiveBuffs(List<PlayerBuffValueHolder> buffs) {
    if (buffs == null) {
      return;
    }
    for (PlayerBuffValueHolder mbsvh : buffs) {
      mbsvh.effect.silentApplyBuff(this, mbsvh.startTime);
    }
  }

  public List<PlayerBuffValueHolder> getAllBuffs() {
    List<PlayerBuffValueHolder> ret = new ArrayList<PlayerBuffValueHolder>();
    LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(effects.values());
    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      ret.add(new PlayerBuffValueHolder(mbsvh.startTime, mbsvh.effect));
    }
    return ret;
  }

  public void cancelMagicDoor() {
    final LinkedList<MapleBuffStatValueHolder> allBuffs = new LinkedList<MapleBuffStatValueHolder>(
            effects.values());

    for (MapleBuffStatValueHolder mbsvh : allBuffs) {
      if (mbsvh.effect.isMagicDoor()) {
        cancelEffect(mbsvh.effect, false, mbsvh.startTime);
        break;
      }
    }
  }

  public int getSkillLevel(int skillid) {
    return getSkillLevel(SkillFactory.getSkill(skillid));
  }

  public final void handleEnergyCharge(final int skillid, final int targets) {
    final ISkill echskill = SkillFactory.getSkill(skillid);
    final byte skilllevel = getSkillLevel(echskill);
    if (skilllevel > 0) {
      final MapleStatEffect echeff = echskill.getEffect(skilllevel);
      if (targets > 0) {
        if (getBuffedValue(MapleBuffStat.ENERGY_CHARGE) == null) {
          echeff.applyEnergyBuff(this, true); // Infinity time
        } else {
          Integer energyLevel = getBuffedValue(MapleBuffStat.ENERGY_CHARGE);
          // TODO: bar going down
          if (energyLevel < 10000) {
            energyLevel += (echeff.getX() * targets);

            client.getSession().write(MaplePacketCreator.showOwnBuffEffect(skillid, 2));
            map.broadcastMessage(this, MaplePacketCreator.showBuffeffect(id, skillid, 2), false);

            if (energyLevel >= 10000) {
              energyLevel = 10000;
            }
            client.getSession().write(
                    MaplePacketCreator.giveEnergyChargeTest(energyLevel, echeff.getDuration() / 1000));
            setBuffedValue(MapleBuffStat.ENERGY_CHARGE, Integer.valueOf(energyLevel));
          } else if (energyLevel == 10000) {
            echeff.applyEnergyBuff(this, false); // One with time
            setBuffedValue(MapleBuffStat.ENERGY_CHARGE, Integer.valueOf(10001));
          }
        }
      }
    }
  }

  public final void handleBattleshipHP(int damage) {
    if (isActiveBuffedValue(5221006)) {
      battleshipHP -= damage;
      if (battleshipHP <= 0) {
        battleshipHP = 0;
        final MapleStatEffect effect = getStatForBuff(MapleBuffStat.MONSTER_RIDING);
        client.getSession().write(MaplePacketCreator.skillCooldown(5221006, effect.getCooldown()));
        addCooldown(5221006, System.currentTimeMillis(), effect.getCooldown() * 1000);
        dispelSkill(5221006);
      }
    }
  }

  public final void handleOrbgain() {
    int orbcount = getBuffedValue(MapleBuffStat.COMBO);
    ISkill combo;
    ISkill advcombo;

    switch (getJob()) {
      case 1110:
      case 1111:
      case 1112:
        combo = SkillFactory.getSkill(11111001);
        advcombo = SkillFactory.getSkill(11110005);
        break;
      default:
        combo = SkillFactory.getSkill(1111002);
        advcombo = SkillFactory.getSkill(1120003);
        break;
    }

    MapleStatEffect ceffect = null;
    int advComboSkillLevel = getSkillLevel(advcombo);
    if (advComboSkillLevel > 0) {
      ceffect = advcombo.getEffect(advComboSkillLevel);
    } else if (getSkillLevel(combo) > 0) {
      ceffect = combo.getEffect(getSkillLevel(combo));
    } else {
      return;
    }

    if (orbcount < ceffect.getX() + 1) {
      int neworbcount = orbcount + 1;
      if (advComboSkillLevel > 0 && ceffect.makeChanceResult()) {
        if (neworbcount < ceffect.getX() + 1) {
          neworbcount++;
        }
      }
      List<Pair<MapleBuffStat, Integer>> stat = Collections
              .singletonList(new Pair<MapleBuffStat, Integer>(MapleBuffStat.COMBO, neworbcount));
      setBuffedValue(MapleBuffStat.COMBO, neworbcount);
      int duration = ceffect.getDuration();
      duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

      client.getSession().write(MaplePacketCreator.giveBuff(combo.getId(), duration, stat, ceffect));
      map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(getId(), stat, ceffect), false);
    }
  }

  public void handleOrbconsume() {
    ISkill combo;

    switch (getJob()) {
      case 1110:
      case 1111:
        combo = SkillFactory.getSkill(11111001);
        break;
      default:
        combo = SkillFactory.getSkill(1111002);
        break;
    }
    if (getSkillLevel(combo) <= 0) {
      return;
    }
    MapleStatEffect ceffect = getStatForBuff(MapleBuffStat.COMBO);
    if (ceffect == null) {
      return;
    }
    List<Pair<MapleBuffStat, Integer>> stat = Collections
            .singletonList(new Pair<MapleBuffStat, Integer>(MapleBuffStat.COMBO, 1));
    setBuffedValue(MapleBuffStat.COMBO, 1);
    int duration = ceffect.getDuration();
    duration += (int) ((getBuffedStarttime(MapleBuffStat.COMBO) - System.currentTimeMillis()));

    client.getSession().write(MaplePacketCreator.giveBuff(combo.getId(), duration, stat, ceffect));
    map.broadcastMessage(this, MaplePacketCreator.giveForeignBuff(getId(), stat, ceffect), false);
  }

  public void silentEnforceMaxHpMp() {
    stats.setMp(stats.getMp());
    stats.setHp(stats.getHp(), true);
  }

  public void enforceMaxHpMp() {
    List<Pair<MapleStat, Integer>> statups = new ArrayList<Pair<MapleStat, Integer>>(2);
    if (stats.getMp() > stats.getCurrentMaxMp()) {
      stats.setMp(stats.getMp());
      statups.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(stats.getMp())));
    }
    if (stats.getHp() > stats.getCurrentMaxHp()) {
      stats.setHp(stats.getHp());
      statups.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(stats.getHp())));
    }
    if (statups.size() > 0) {
      client.getSession().write(MaplePacketCreator.updatePlayerStats(statups, getJob()));
    }
  }

  public MapleMap getMap() {
    return map;
  }

  public MonsterBook getMonsterBook() {
    return monsterbook;
  }

  public void setMap(MapleMap newmap) {
    this.map = newmap;
  }

  public void setMap(int PmapId) {
    this.mapid = PmapId;
  }

  public int getMapId() {
    if (map != null) {
      return map.getId();
    }
    return mapid;
  }

  public byte getInitialSpawnpoint() {
    return initialSpawnPoint;
  }

  public int getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public final String getBlessOfFairyOrigin() {
    return this.BlessOfFairy_Origin;
  }

  public final short getLevel() {
    return level;
  }

  public final short getFame() {
    return fame;
  }

  public final int getDojo() {
    return dojo;
  }

  public final int getDojoRecord() {
    return dojoRecord;
  }

  public final int getFallCounter() {
    return fallcounter;
  }

  public final MapleClient getClient() {
    return client;
  }

  public final void setClient(final MapleClient client) {
    this.client = client;
  }

  public int getExp() {
    return exp;
  }

  public int getRemainingAp() {
    return remainingAp;
  }

  public short getHpApUsed() {
    return hpApUsed;
  }

  public boolean isHidden() {
    return hidden;
  }

  public void setHpApUsed(short hpApUsed) {
    this.hpApUsed = hpApUsed;
  }

  public byte getSkinColor() {
    return skinColor;
  }

  public void setSkinColor(byte skinColor) {
    this.skinColor = skinColor;
  }

  public short getJob() {
    return job;
  }

  public MapleJob getJobValue() {
    return MapleJob.getById(job);
  }

  public byte getGender() {
    return gender;
  }

  public int getHair() {
    return hair;
  }

  public int getFace() {
    return face;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setExp(int exp) {
    if (isCygnus() && level >= 120) {
      this.exp = 0;
      return;
    }
    this.exp = exp;
  }

  public void setHair(int hair) {
    this.hair = hair;
  }

  public void setFace(int face) {
    this.face = face;
  }

  public void setFame(short fame) {
    this.fame = fame;
  }

  public void setDojo(final int dojo) {
    this.dojo = dojo;
  }

  public void setDojoRecord(final boolean reset) {
    if (reset) {
      dojo = 0;
      dojoRecord = 0;
    } else {
      dojoRecord++;
    }
  }

  public void setFallCounter(int fallcounter) {
    this.fallcounter = fallcounter;
  }

  public Point getOldPosition() {
    return old;
  }

  public void setOldPosition(Point x) {
    this.old = x;
  }

  public void setRemainingAp(int remainingAp) {
    this.remainingAp = remainingAp;
  }

  public void setGender(byte gender) {
    this.gender = gender;
  }

  public void setInvincible(boolean invinc) {
    invincible = invinc;
  }

  public boolean isInvincible() {
    return invincible;
  }

  public CheatTracker getCheatTracker() {
    return anticheat;
  }

  public MapleBuddyList getBuddylist() {
    return buddylist;
  }

  public void addFame(int famechange) {
    this.fame += famechange;
    if (this.fame >= 50) {
      finishAchievement(7);
    }
  }

  public void changeMapBanish(final int mapid, final String portal, final String msg) {
    dropMessage(5, msg);
    final MapleMap map = client.getChannelServer().getMapFactory().getMap(mapid);
    changeMap(map, map.getPortal(portal));
  }

  public void changeMap(final MapleMap to, final Point pos) {
    changeMapInternal(to, pos, MaplePacketCreator.getWarpToMap(to, 0x81, this), null);
  }

  public void changeMap(final MapleMap to, final MaplePortal pto) {
    changeMapInternal(to, pto.getPosition(), MaplePacketCreator.getWarpToMap(to, pto.getId(), this), null);
  }

  public void changeMapPortal(final MapleMap to, final MaplePortal pto) {
    changeMapInternal(to, pto.getPosition(), MaplePacketCreator.getWarpToMap(to, pto.getId(), this), pto);
  }

  private void changeMapInternal(final MapleMap to, final Point pos, byte[] warpPacket, final MaplePortal pto) {
    if (to == null) {
      return;
    }

    final int nowmapid = map.getId();
    updatePetAuto();
    if (eventInstance != null) {
      eventInstance.changedMap(this, to.getId());
    }
    final boolean pyramid = pyramidSubway != null;
    if (map.getId() == nowmapid) {

      client.getSession().write(warpPacket);
      map.removePlayer(this);
      if (client.getChannelServer().getPlayerStorage().getCharacterById(getId()) != null) {
        map = to;
        mapid = to.getId();
        setPosition(pos);
        to.addPlayer(this);
        stats.relocHeal();
        expirationTask(false, false); // do this when change map

      }
    }
    if (pyramid && pyramidSubway != null) { // checks if they had pyramid
      // before AND after changing
      pyramidSubway.onChangeMap(this, to.getId());
    }

  }

  public void leaveMap() {
    controlled.clear();
    visibleMapObjectsLock.writeLock().lock();
    try {
      visibleMapObjects.clear();
    } finally {
      visibleMapObjectsLock.writeLock().unlock();
    }
    if (chair != 0) {
      cancelFishingTask();
      chair = 0;
    }
    cancelMapTimeLimitTask();
  }

  public void changeJob(int newJob) {
    try {
      MapleJob varJob = MapleJob.getById(newJob);
      if (varJob == null) {
        varJob = MapleJob.BEGINNER;
        // Bad..
        newJob = MapleJob.BEGINNER.getId();
      }
      this.job = (short) varJob.getId();
      if (newJob > 0 && !isGM()) {
        resetStatsByJob(true);
        if (newJob == 2200) {
          MapleQuest.getInstance(22100).forceStart(this, 0, null);
          MapleQuest.getInstance(22100).forceComplete(this, 0);
          client.getSession().write(MaplePacketCreator.getEvanTutorial("UI/tutorial/evan/14/0"));
          dropMessage(5,
                  "The baby Dragon hatched and appears to have something to tell you. Click the baby Dragon to start a conversation.");
        }
      }
      updateSingleStat(MapleStat.JOB, newJob);

      int maxhp = stats.getMaxHp(), maxmp = stats.getMaxMp();

      switch (job) {
        case 100: // Warrior
        case 1100: // Soul Master
        case 2100: // Aran
        case 3200:
          maxhp += Randomizer.rand(200, 250);
          break;
        case 200: // Magician
        case 2200: // evan
        case 2210: // evan
          maxmp += Randomizer.rand(100, 150);
          break;
        case 300: // Bowman
        case 400: // Thief
        case 500: // Pirate
        case 3300:
        case 3500:
          maxhp += Randomizer.rand(100, 150);
          maxmp += Randomizer.rand(25, 50);
          break;
        case 110: // Fighter
          maxhp += Randomizer.rand(300, 350);
          break;
        case 120: // Page
        case 130: // Spearman
        case 1110: // Soul Master
        case 2110: // Aran
        case 3210:
          maxhp += Randomizer.rand(300, 350);
          break;
        case 210: // FP
        case 220: // IL
        case 230: // Cleric
          maxmp += Randomizer.rand(400, 450);
          break;
        case 310: // Bowman
        case 320: // Crossbowman
        case 410: // Assasin
        case 420: // Bandit
        case 430: // Semi Dualer
        case 1310: // Wind Breaker
        case 1410: // Night Walker
        case 3310:
        case 3510:
          maxhp += Randomizer.rand(300, 350);
          maxhp += Randomizer.rand(150, 200);
          break;
        case 900: // GM
        case 800: // Manager
          maxhp += 30000;
          maxhp += 30000;
          break;
      }
      if (maxhp >= 30000) {
        maxhp = 30000;
      }
      if (maxmp >= 30000) {
        maxmp = 30000;
      }
      stats.setMaxHp((short) maxhp);
      stats.setMaxMp((short) maxmp);
      stats.setHp((short) maxhp);
      stats.setMp((short) maxmp);
      List<Pair<MapleStat, Integer>> statup = new ArrayList<Pair<MapleStat, Integer>>(4);
      statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXHP, Integer.valueOf(maxhp)));
      statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXMP, Integer.valueOf(maxmp)));
      statup.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(maxhp)));
      statup.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(maxmp)));
      stats.recalcLocalStats();
      client.getSession().write(MaplePacketCreator.updatePlayerStats(statup, getJob()));
      map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(getId(), 8), false);
      silentPartyUpdate();
      guildUpdate();
      if (dragon != null) {
        map.broadcastMessage(MaplePacketCreator.removeDragon(this.id));
        map.removeMapObject(dragon);
        dragon = null;
      }
      if (autoSkill) {
        loadAutoSkills(this);// TODO: Remove auto skill
        sendAutoSkills();
      }

      sendSkills();
      if (newJob >= 2200 && newJob <= 2218) { // make new
        if (getBuffedValue(MapleBuffStat.MONSTER_RIDING) != null) {
          cancelBuffStats(MapleBuffStat.MONSTER_RIDING);
        }
        makeDragon();
        map.spawnDragon(dragon);
        map.updateMapObjectVisibility(this, dragon);
      }
      maxMastery();
      equipChanged();
      sendServerChangeJobCongratulations();
    } catch (Exception e) {
      FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, e);
    }
  }

  private void sendServerChangeJobCongratulations() {
    if (this.isGM()) {
      return;
    }
    String str = "[Lv. %s] Congratulations to %s on becoming a %s!";
    World.Broadcast.broadcastMessage(
            MaplePacketCreator.serverNotice(6, String.format(str, level, name, this.getJobValue().getName())));
  }

  public void makeDragon() {
    dragon = new MapleDragon(this);
  }

  public MapleDragon getDragon() {
    return dragon;
  }

  public void gainAp(int ap) {
    this.remainingAp += ap;
    updateSingleStat(MapleStat.AVAILABLEAP, Math.min(199, this.remainingAp));
  }

  public void gainSp(int sp) {
    if (isEvan()) {
      addEvanSP(sp);
      return;
    }
    this.remainingSp += sp;
    updateSingleStat(MapleStat.AVAILABLESP, Math.min(199, this.remainingSp));
  }

  public void resetAPSP() {
    gainAp(-this.remainingAp);
  }

  public void changeSkillLevel(final ISkill skill, byte newLevel, byte newMasterlevel) { // 1
    // month
    if (skill == null) {
      return;
    }
    changeSkillLevel(skill, newLevel, newMasterlevel,
            skill.isTimeLimited() ? (System.currentTimeMillis() + (long) (30L * 24L * 60L * 60L * 1000L)) : -1);
  }

  public void changeSkillLevel(final ISkill skill, byte newLevel, byte newMasterlevel, long expiration) {
    if (skill == null || (!GameConstants.isApplicableSkill(skill.getId())
            && !GameConstants.isApplicableSkill_(skill.getId()))) {
      return;
    }
    client.getSession().write(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel, expiration));
    if (newLevel == 0 && newMasterlevel == 0) {
      if (skills.containsKey(skill)) {
        skills.remove(skill);
      } else {
        return; // nothing happen
      }
    } else {
      skills.put(skill, new SkillEntry(newLevel, newMasterlevel, expiration));
    }
    changed_skills = true;
    if (GameConstants.isRecoveryIncSkill(skill.getId())) {
      stats.relocHeal();
    } else if (GameConstants.isElementAmp_Skill(skill.getId())) {
      stats.recalcLocalStats();
    }

  }

  public void changeSkillLevel_Skip(final ISkill skill, byte newLevel, byte newMasterlevel) {
    if (skill == null) {
      return;
    }
    client.getSession().write(MaplePacketCreator.updateSkill(skill.getId(), newLevel, newMasterlevel, -1L));
    if (newLevel == 0 && newMasterlevel == 0) {
      if (skills.containsKey(skill)) {
        skills.remove(skill);
      } else {
        return; // nothing happen
      }
    } else {
      skills.put(skill, new SkillEntry(newLevel, newMasterlevel, -1L));
    }
  }

  public void playerDead() {
    final MapleStatEffect statss = getStatForBuff(MapleBuffStat.SOUL_STONE);
    if (statss != null) {
      client.getSession().write(MaplePacketCreator.showSpecialEffect(26));
      getStat().setHp(((getStat().getMaxHp() / 100) * statss.getX()));
      setStance(0);
      changeMap(getMap(), getMap().getPortal(0));
      return;
    }
    if (getEventInstance() != null) {
      getEventInstance().playerKilled(this);
    }
    if (getNewEventInstance() != null) {
      getNewEventInstance().onPlayerDied(this);
    }
    dispelSkill(0);
    cancelMorphs(true); // dead = cancel
    cancelBuffStats(MapleBuffStat.DRAGONBLOOD);
    cancelEffectFromBuffStat(MapleBuffStat.MORPH);
    cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
    cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
    cancelEffectFromBuffStat(MapleBuffStat.REAPER);
    cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
    checkFollow();
    if (job != 0 && job != 1000 && job != 2000 && job != 2001 && job != 3000) {
      int charms = getItemQuantity(5130000, false);
      if (charms > 0) {
        MapleInventoryManipulator.removeById(client, MapleInventoryType.CASH, 5130000, 1, true, false);

        charms--;
        if (charms > 0xFF) {
          charms = 0xFF;
        }
        client.getSession().write(MTSCSPacket.useCharm((byte) charms, (byte) 0));
      } else {
        float diepercentage = 0.0f;
        int expforlevel = GameConstants.getExpNeededForLevel(level);
        if (map.isTown() || FieldLimitType.RegularExpLoss.check(map.getFieldLimit())) {
          diepercentage = 0.01f;
        } else {
          float v8 = 0.0f;
          if (this.job / 100 == 3) {
            v8 = 0.08f;
          } else {
            v8 = 0.2f;
          }
          diepercentage = (float) (v8 / this.stats.getLuk() + 0.05);
        }
        int v10 = (int) (exp - (long) ((double) expforlevel * diepercentage));
        if (v10 < 0) {
          v10 = 0;
        }
        this.exp = v10;
      }
    }
    this.updateSingleStat(MapleStat.EXP, this.exp);
    if (!stats.checkEquipDurabilitys(this, -100)) { // i guess this is how
      // it works ?
      dropMessage(5, "An item has run out of durability but has no inventory room to go to.");
    } // lol
    if (pyramidSubway != null) {
      stats.setHp((short) 50);
      pyramidSubway.fail(this);
    }
  }

  public void receivePartyMemberHP() {
    if (party == null) {
      return;
    }
    for (MaplePartyCharacter partychar : party.getMembers()) {
      final MapleCharacter partyMate = client.getChannelServer().getPlayerStorage()
              .getCharacterByName(partychar.getName());
      if (partyMate != null && partyMate != this) {
        System.out.println("Received HP from " + partyMate.getName());
        this.getClient().sendPacket(MapleUserPackets.updatePartyHpForCharacter(partyMate));
      }
    }
  }

  public void updatePartyMemberHP() {
    if (party == null) {
      return;
    }
    for (MaplePartyCharacter partychar : party.getMembers()) {
      final MapleCharacter partyMates = client.getChannelServer().getPlayerStorage()
              .getCharacterByName(partychar.getName());
      if (partyMates != null && partyMates != this) {
        partyMates.getClient().sendPacket(MapleUserPackets.updatePartyHpForCharacter(this));
      }
    }
  }

  public void silentPartyUpdate() {
    if (party != null) {
      World.Party.updateParty(party.getId(), PartyOperation.SILENT_UPDATE, new MaplePartyCharacter(this));
    }
  }

  public void healHP(int delta) {
    addHP(delta);
    client.getSession().write(MaplePacketCreator.showOwnHpHealed(delta));
    getMap().broadcastMessage(this, MaplePacketCreator.showHpHealed(getId(), delta), false);
  }

  public void healMP(int delta) {
    addMP(delta);
    client.getSession().write(MaplePacketCreator.showOwnHpHealed(delta));
    getMap().broadcastMessage(this, MaplePacketCreator.showHpHealed(getId(), delta), false);
  }

  /**
   * Convenience function which adds the supplied parameter to the current hp
   * then directly does a updateSingleStat.
   *
   * @param delta
   * @see MapleCharacter#setHp(int)
   */
  public void addHP(int delta) {
    if (stats.setHp(stats.getHp() + delta)) {
      updateSingleStat(MapleStat.HP, stats.getHp());
    }
  }

  /**
   * Convenience function which adds the supplied parameter to the current mp
   * then directly does a updateSingleStat.
   *
   * @param delta
   * @see MapleCharacter#setMp(int)
   */
  public void addMP(int delta) {
    if (stats.setMp(stats.getMp() + delta)) {
      updateSingleStat(MapleStat.MP, stats.getMp());
    }
  }

  public void addMPHP(int hpDiff, int mpDiff) {
    List<Pair<MapleStat, Integer>> statups = new ArrayList<Pair<MapleStat, Integer>>();

    if (stats.setHp(stats.getHp() + hpDiff)) {
      statups.add(new Pair<MapleStat, Integer>(MapleStat.HP, Integer.valueOf(stats.getHp())));
    }
    if (stats.setMp(stats.getMp() + mpDiff)) {
      statups.add(new Pair<MapleStat, Integer>(MapleStat.MP, Integer.valueOf(stats.getMp())));
    }
    if (statups.size() > 0) {
      client.getSession().write(MaplePacketCreator.updatePlayerStats(statups, getJob()));
      int hp = this.getStat().hp;
      if (hp <= 0) {// In case player die with disable actions
        getClient().enableActions();
        if (this.getNewEventInstance() != null) {
          this.getNewEventInstance().onPlayerDied(this);
        }

      }
    }
  }

  public void updateSingleStat(MapleStat stat, int newval) {
    updateSingleStat(stat, newval, false);
  }

  /**
   * Updates a single stat of this MapleCharacter for the client. This method
   * only creates and sends an update packet, it does not update the stat
   * stored in this MapleCharacter instance.
   *
   * @param stat
   * @param newval
   * @param itemReaction
   */
  public void updateSingleStat(MapleStat stat, int newval, boolean itemReaction) {
    if (stat == MapleStat.AVAILABLESP) {
      client.getSession().write(MaplePacketCreator.updateSp(this, itemReaction, false));
      return;
    }
    Pair<MapleStat, Integer> statpair = new Pair<MapleStat, Integer>(stat, Integer.valueOf(newval));
    client.getSession().write(
            MaplePacketCreator.updatePlayerStats(Collections.singletonList(statpair), itemReaction, getJob()));
  }

  public void gainExp(final int total, final boolean show, final boolean inChat, final boolean white) {
    if (!isAlive()) {
      return;
    }

    try {
      if (isCygnus() && level >= 120) {
        return;
      }
      int prevexp = getExp();
      int needed = GameConstants.getExpNeededForLevel(level);
      if (level >= 200) {
        if (exp + total > needed) {
          setExp(needed);
        } else {
          exp += total;
        }
      } else {
        boolean leveled = false;
        if (exp + total >= needed) {
          exp += total;
          levelUp(true);
          leveled = true;
          needed = GameConstants.getExpNeededForLevel(level);
          if (exp > needed) {
            setExp(needed);
          }
        } else {
          exp += total;
        }

      }

      if (total != 0) {
        if (exp < 0) { // After adding, and negative
          if (total > 0) {
            setExp(needed);
          } else if (total < 0) {
            setExp(0);
          }
        }
        updateSingleStat(MapleStat.EXP, getExp());
        if (show) { // still show the expgain even if it's not there
          client.getSession().write(MaplePacketCreator.GainEXP_Others(total, inChat, white));
        }
        if (total > 0) {
          stats.checkEquipLevels(this, total); // gms like
        }
      }
    } catch (Exception e) {
      FileoutputUtil.outputFileError(FileoutputUtil.ScriptEx_Log, e); // all
      // jobs
      // throw
      // errors
      // :(
    }
  }

  public void gainExpMonster(final int gain, final boolean show, final boolean white, final byte pty,
                             int Class_Bonus_EXP, int Equipment_Bonus_EXP, int Premium_Bonus_EXP, boolean real) {
    if (!isAlive()) {
      return;
    }
    if (isCygnus() && level >= 120) {
      return;
    }
    mobKilledNo++; // Reset back to 0 when cc

    long total = (long) (gain + Class_Bonus_EXP + Equipment_Bonus_EXP + Premium_Bonus_EXP);

    long Trio_Bonus_EXP = 0;
    short percentage = 0;
    double hoursFromLogin = 0.0;
    if (mobKilledNo == 3 && ServerConstants.TRIPLE_TRIO) { // Count begins
      // at 0
      // After 1 hour of login until 2 hours: Bonus 30% EXP at every 3rd
      // mob hunted
      // 2 hours to 3 hours: Bonus 100% EXP at every 3rd mob hunted
      // 3 hours to 4 hours: Bonus 150% EXP at every 3rd mob hunted
      // 4 hours to 5 hours: Bonus 180% EXP at every 3rd mob hunted
      // 5 hours and above: Bonus 200% EXP at every 3rd mob hunted
      hoursFromLogin = ((System.currentTimeMillis() - loginTime) / (1000 * 60 * 60));
      if (hoursFromLogin >= 1 && hoursFromLogin < 2) {
        percentage = 30;
      } else if (hoursFromLogin >= 2 && hoursFromLogin < 3) {
        percentage = 40;
      } else if (hoursFromLogin >= 3 && hoursFromLogin < 4) {
        percentage = 50;
      } else if (hoursFromLogin >= 4 && hoursFromLogin < 5) {
        percentage = 60;
      } else if (hoursFromLogin >= 5) {
        percentage = 100;
      }
      Trio_Bonus_EXP = (long) (((gain / 100) * percentage));
      total += Trio_Bonus_EXP;
      mobKilledNo = 0;
    }

    int partyinc = 0;
    if (pty > 1) {
      partyinc = (gain / 20) * (pty + 1);
      total += partyinc;
    }
    if (gain > 0 && total < gain) { // just in case
      total = Integer.MAX_VALUE;
    }
    if (exp < 0) { // Set first
      setExp(0);
      updateSingleStat(MapleStat.EXP, 0);
    }

    int needed = GameConstants.getExpNeededForLevel(level); // Calculate
    // based on the
    // first level
    boolean leveled = false;
    if (getLevel() < 200) {
      long newexp = total + exp;
      while (newexp >= GameConstants.getExpNeededForLevel(level) && level < 200) {
        newexp -= GameConstants.getExpNeededForLevel(level);
        levelUp(false); // Don't show animation for ALL of the levels.
        leveled = true;
      }
      if (newexp >= Integer.MAX_VALUE || level >= 200) {
        setExp(0);
      } else {
        setExp((int) newexp);
      }

    } else {
      return;
    }
    if (gain != 0) {
      if (exp < 0) { // After adding, and negative
        if (gain > 0) {
          setExp(GameConstants.getExpNeededForLevel(level));
        } else if (gain < 0) {
          setExp(0);
        }
      }
      updateSingleStat(MapleStat.EXP, getExp());
      if (leveled) {
        final List<Pair<MapleStat, Integer>> statup = new ArrayList<>(7);
        statup.add(new Pair<>(MapleStat.MAXHP, (int) Math.min(30000, stats.maxhp)));
        statup.add(new Pair<>(MapleStat.MAXMP, (int) Math.min(30000, stats.maxmp)));
        statup.add(new Pair<>(MapleStat.HP, (int) Math.min(30000, stats.maxhp)));
        statup.add(new Pair<>(MapleStat.MP, (int) Math.min(30000, stats.maxmp)));
        statup.add(new Pair<>(MapleStat.EXP, exp));
        statup.add(new Pair<>(MapleStat.LEVEL, (int) level));
        statup.add(new Pair<>(MapleStat.AVAILABLEAP, Math.min(199, remainingAp)));
        client.getSession().write(MaplePacketCreator.updatePlayerStats(statup, getJob()));
        map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(getId(), 0), false);
      }
      if (show) { // still show the expgain even if it's not there
        client.getSession()
                .write(MaplePacketCreator.GainEXP_Monster((int) Math.min(Integer.MAX_VALUE, gain), white,
                        (int) Math.min(Integer.MAX_VALUE, partyinc), Class_Bonus_EXP, Equipment_Bonus_EXP,
                        Premium_Bonus_EXP, (byte) percentage, hoursFromLogin));
      }
      stats.checkEquipLevels(this, (int) Math.min(Integer.MAX_VALUE, total));
    }
  }

  public void forceReAddItem_NoUpdate(IItem item, MapleInventoryType type) {
    getInventory(type).removeSlot(item.getPosition());
    getInventory(type).addFromDB(item);
  }

  public void forceReAddItem(IItem item, MapleInventoryType type) { // used
    // for
    // stuff
    // like
    // durability,
    // item
    // exp/level,
    // probably
    // owner?
    forceReAddItem_NoUpdate(item, type);
    if (type != MapleInventoryType.UNDEFINED) {
      client.getSession().write(MaplePacketCreator.updateSpecialItemUse(item,
              type == MapleInventoryType.EQUIPPED ? (byte) 1 : type.getType()));
    }
  }

  public void forceReAddItem_Flag(IItem item, MapleInventoryType type) { // used
    // for
    // flags
    forceReAddItem_NoUpdate(item, type);
    if (type != MapleInventoryType.UNDEFINED) {
      client.getSession().write(MaplePacketCreator.updateSpecialItemUse_(item,
              type == MapleInventoryType.EQUIPPED ? (byte) 1 : type.getType()));
    }
  }

  public boolean isGM() {
    return gmLevel > 0;
  }

  public boolean isAdmin() {
    return gmLevel >= 5;
  }

  public int getGMLevel() {
    return gmLevel;
  }

  public boolean hasGmLevel(int level) {
    return gmLevel >= level;
  }

  public final MapleInventory getInventory(MapleInventoryType type) {
    return inventory[type.ordinal()];
  }

  public final MapleInventory[] getInventorys() {
    return inventory;
  }

  public void removeItem(int id, int quantity) {
    MapleInventoryManipulator.removeById(client, GameConstants.getInventoryType(id), id, -quantity, true, false);
  }

  public final void expirationTask(boolean pending, boolean firstLoad) {
    final MapleItemInformationProvider ii = MapleItemInformationProvider.getInstance();
    if (pending) {
      if (pendingExpiration != null) {
        final List<String> replaceMsg = new ArrayList<>();
        for (Integer z : pendingExpiration) {
          final Triple<Integer, String, Integer> replace = ii.replaceItemInfo(z.intValue());
          if (replace == null) {
            client.getSession().write(MaplePacketCreator.itemExpired(z.intValue()));
          }
          if (!firstLoad) {
            if (replace != null && replace.getLeft() > 0 && replace.getMid().length() > 0) {
              replaceMsg.add(replace.getMid());
            }
          }
        }
        client.getSession().write(MaplePacketCreator.itemReplaced(replaceMsg));
      }
      pendingExpiration = null;
      if (pendingSkills != null) {
        for (Integer z : pendingSkills) {
          client.getSession().write(MaplePacketCreator.updateSkill(z, 0, 0, -1));
        }
        client.getSession().write(MaplePacketCreator.skillExpired(pendingSkills));
      }
      pendingSkills = null;
      if (pendingUnlock != null) {
        client.getSession().write(MaplePacketCreator.sealExpired(pendingUnlock));
      }
      return;
    }

    final List<Integer> ret = new ArrayList<Integer>();
    final List<Integer> retExpire = new ArrayList<Integer>();
    final long currenttime = System.currentTimeMillis();
    final List<Pair<MapleInventoryType, IItem>> toberemove = new ArrayList<Pair<MapleInventoryType, IItem>>(); // This
    // is
    // here
    // to
    // prevent
    // deadlock.
    final List<IItem> tobeunlock = new ArrayList<IItem>(); // This is here
    // to prevent
    // deadlock.

    for (final MapleInventoryType inv : MapleInventoryType.values()) {
      for (final IItem item : getInventory(inv)) {
        long expiration = item.getExpiration();

        if ((expiration != -1 && !GameConstants.isPet(item.getItemId()) && currenttime > expiration)) {
          if (ItemFlag.LOCK.check(item.getFlag())) {
            tobeunlock.add(item);
          } else if (currenttime > expiration) {
            toberemove.add(new Pair<MapleInventoryType, IItem>(inv, item));
          }
        } else if ((item.getItemId() == 5000054 && item.getPet() != null && item.getPet().getSecondsLeft() <= 0)
                || (firstLoad && ii.isLogoutExpire(item.getItemId()))) {
          toberemove.add(new Pair<MapleInventoryType, IItem>(inv, item));
        }
      }
    }
    IItem item;
    for (final Pair<MapleInventoryType, IItem> itemz : toberemove) {
      item = itemz.getRight();
      ret.add(item.getItemId());
      getInventory(itemz.getLeft()).removeItem(item.getPosition(), item.getQuantity(), false);
      if (!firstLoad) {
        final Triple<Integer, String, Integer> replace = ii.replaceItemInfo(item.getItemId());
        if (replace != null && replace.getLeft() > 0) {
          final int period = replace.getRight();
          if (GameConstants.getInventoryType(replace.getLeft()) == MapleInventoryType.EQUIP) {
            final IItem theNewItem = ii.getEquipById(replace.getLeft());
            theNewItem.setPosition(item.getPosition());
            theNewItem.setExpiration(System.currentTimeMillis() + (period * 60 * 1000));
            getInventory(itemz.getLeft()).addFromDB(theNewItem);
          } else {
            final Item newI = new Item(replace.getLeft(), item.getPosition(), (short) 1, (byte) 0, -1);
            newI.setExpiration(System.currentTimeMillis() + (period * 60 * 1000));
            getInventory(itemz.getLeft()).addItem(newI);
          }
        }
      }
    }
    this.pendingExpiration = ret;

    for (final IItem itemz : tobeunlock) {
      retExpire.add(itemz.getItemId());
      itemz.setExpiration(-1);
      itemz.setFlag((byte) (itemz.getFlag() - ItemFlag.LOCK.getValue()));
    }
    this.pendingUnlock = retExpire;

    final List<Integer> skilz = new ArrayList<Integer>();
    final List<ISkill> toberem = new ArrayList<ISkill>();
    for (Entry<ISkill, SkillEntry> skil : skills.entrySet()) {
      if (skil.getValue().expiration != -1 && currenttime > skil.getValue().expiration) {
        toberem.add(skil.getKey());
      }
    }
    for (ISkill skil : toberem) {
      skilz.add(skil.getId());
      this.skills.remove(skil);
      this.changed_skills = true;
    }
    this.pendingSkills = skilz;
  }

  public MapleShop getShop() {
    return shop;
  }

  public void setShop(MapleShop shop) {
    this.shop = shop;
  }

  public int getMeso() {
    return meso;
  }

  public final int[] getSavedLocations() {
    return savedLocations;
  }

  public int getSavedLocation(SavedLocationType type) {
    return savedLocations[type.getValue()];
  }

  public void saveLocation(SavedLocationType type) {
    savedLocations[type.getValue()] = getMapId();
    changed_savedlocations = true;
  }

  public void saveLocation(SavedLocationType type, int mapz) {
    savedLocations[type.getValue()] = mapz;
    changed_savedlocations = true;
  }

  public void clearSavedLocation(SavedLocationType type) {
    savedLocations[type.getValue()] = -1;
    changed_savedlocations = true;
  }

  public void gainMeso(int gain, boolean show) {
    gainMeso(gain, show, false, false);
  }

  public void gainMeso(int gain, boolean show, boolean enableActions) {
    gainMeso(gain, show, enableActions, false);
  }

  public void gainMeso(int gain, boolean show, boolean enableActions, boolean inChat) {
    final int startMeso = meso;
    final long total = (long) startMeso + (long) gain;
    int realGain = gain;
    if (total >= Integer.MAX_VALUE) {
      meso = Integer.MAX_VALUE;
      realGain = Integer.MAX_VALUE - startMeso;
    } else {
      meso += gain;
    }
    updateSingleStat(MapleStat.MESO, meso, enableActions);
    if (show && realGain > 0) {
      client.getSession().write(MaplePacketCreator.showMesoGain(realGain, inChat));
    }
  }

  public void controlMonster(MapleMonster monster, boolean aggro) {

    monster.setController(this);
    controlled.add(monster);
    client.getSession().write(MobPacket.controlMonster(monster, false, aggro));
  }

  public void stopControllingMonster(MapleMonster monster) {

    if (monster != null && controlled.contains(monster)) {
      controlled.remove(monster);
    }
  }

  public void checkMonsterAggro(MapleMonster monster) {
    if (monster == null) {
      return;
    }
    if (monster.getController() == this) {
      monster.setControllerHasAggro(true);
    } else {
      monster.switchController(this, true);
    }
  }

  public Set<MapleMonster> getControlled() {
    return controlled;
  }

  public int getControlledSize() {
    return controlled.size();
  }

  public int getAccountID() {
    return accountid;
  }

  public void mobKilled(final int id, final int skillID) {
    for (MapleQuestStatus q : quests.values()) {
      if (q.getStatus() != 1 || !q.hasMobKills()) {
        continue;
      }
      if (q.mobKilled(id, skillID)) {
        client.getSession().write(MaplePacketCreator.updateQuestMobKills(q));
        if (q.getQuest().canComplete(this, null)) {
          client.getSession().write(MaplePacketCreator.getShowQuestCompletion(q.getQuest().getId()));
        }
      }
    }
  }

  public final List<MapleQuestStatus> getStartedQuests() {
    List<MapleQuestStatus> ret = new LinkedList<MapleQuestStatus>();
    for (MapleQuestStatus q : quests.values()) {
      if (q.getStatus() == 1) {
        ret.add(q);
      }
    }
    return ret;
  }

  public final List<MapleQuestStatus> getCompletedQuests() {
    List<MapleQuestStatus> ret = new LinkedList<MapleQuestStatus>();
    for (MapleQuestStatus q : quests.values()) {
      if (q.getStatus() == 2) {
        ret.add(q);
      }
    }
    return ret;
  }

  public Map<ISkill, SkillEntry> getSkills() {
    return Collections.unmodifiableMap(skills);
  }

  public boolean hasSkill(int skill) {
    SkillEntry ret = skills.get(skill);
    return ret != null;
  }

  public byte getSkillLevel(final ISkill skill) {
    final SkillEntry ret = skills.get(skill);
    if (ret == null || ret.skillevel <= 0) {
      return 0;
    }
    return (byte) Math.min(skill.getMaxLevel(), ret.skillevel + (skill.isBeginnerSkill() ? 0 : stats.incAllskill));
  }

  public byte getMasterLevel(final int skill) {
    return getMasterLevel(SkillFactory.getSkill(skill));
  }

  public byte getMasterLevel(final ISkill skill) {
    final SkillEntry ret = skills.get(skill);
    if (ret == null) {
      return 0;
    }
    return ret.masterlevel;
  }

  public void levelUp(boolean show) {
    if (isCygnus() && getLevel() >= 120) {
      return;
    }
    if ((long) (remainingAp + 5) >= Integer.MAX_VALUE) {
      remainingAp = Integer.MAX_VALUE;
    } else {
      remainingAp += 5;
    }
    if (isCygnus() && getLevel() < 70) {
      remainingAp += 1;
    }

    int maxhp = stats.getMaxHp();
    int maxmp = stats.getMaxMp();

    if (isBeginner()) {
      maxhp += Randomizer.rand(12, 16);
      maxmp += Randomizer.rand(10, 12);
    } else if (isWarrior()) {
      final ISkill improvingMaxHP = SkillFactory.getSkill(1000001);
      final int slevel = getSkillLevel(improvingMaxHP);
      if (slevel > 0) {
        maxhp += improvingMaxHP.getEffect(slevel).getX();
      }
      maxhp += Randomizer.rand(24, 28);
      maxmp += Randomizer.rand(4, 6);
    } else if (isMage()) {
      final ISkill improvingMaxMP = SkillFactory.getSkill(2000001);
      final int slevel = getSkillLevel(improvingMaxMP);
      if (slevel > 0) {
        maxmp += improvingMaxMP.getEffect(slevel).getX() * 2;
      }
      maxhp += Randomizer.rand(10, 14);
      maxmp += Randomizer.rand(22, 24);
    } else if ((job >= 300 && job <= 322) || (job >= 400 && job <= 434) || (job >= 1300 && job <= 1311)
            || (job >= 1400 && job <= 1411)) {
      maxhp += Randomizer.rand(20, 24);
      maxmp += Randomizer.rand(14, 16);
    } else if (isPirate()) { // Pirate
      final ISkill improvingMaxHP = SkillFactory.getSkill(5100000);
      final int slevel = getSkillLevel(improvingMaxHP);
      if (slevel > 0) {
        maxhp += improvingMaxHP.getEffect(slevel).getX();
      }
      maxhp += Randomizer.rand(22, 26);
      maxmp += Randomizer.rand(18, 22);
    } else if (job >= 1100 && job <= 1111) { // Soul Master
      final ISkill improvingMaxHP = SkillFactory.getSkill(11000000);
      final int slevel = getSkillLevel(improvingMaxHP);
      if (slevel > 0) {
        maxhp += improvingMaxHP.getEffect(slevel).getX();
      }
      maxhp += Randomizer.rand(24, 28);
      maxmp += Randomizer.rand(4, 6);
    } else if (job >= 1200 && job <= 1211) { // Flame Wizard
      final ISkill improvingMaxMP = SkillFactory.getSkill(12000000);
      final int slevel = getSkillLevel(improvingMaxMP);
      if (slevel > 0) {
        maxmp += improvingMaxMP.getEffect(slevel).getX() * 2;
      }
      maxhp += Randomizer.rand(10, 14);
      maxmp += Randomizer.rand(22, 24);
    } else if (job >= 1500 && job <= 1512) { // Pirate
      final ISkill improvingMaxHP = SkillFactory.getSkill(15100000);
      final int slevel = getSkillLevel(improvingMaxHP);
      if (slevel > 0) {
        maxhp += improvingMaxHP.getEffect(slevel).getX();
      }
      maxhp += Randomizer.rand(22, 26);
      maxmp += Randomizer.rand(18, 22);
    } else if (job >= 2100 && job <= 2112) { // Aran
      maxhp += Randomizer.rand(50, 52);
      maxmp += Randomizer.rand(4, 6);
    } else if (job >= 2200 && job <= 2218) { // Evan
      maxhp += Randomizer.rand(12, 16);
      maxmp += Randomizer.rand(50, 52);
    } else { // GameMaster
      maxhp += Randomizer.rand(50, 100);
      maxmp += Randomizer.rand(50, 100);
    }
    maxmp += stats.getTotalInt() / 10;
    exp = 0;
    level += 1;
    int level = getLevel();

    maxhp = (short) Math.min(30000, Math.abs(maxhp));
    maxmp = (short) Math.min(30000, Math.abs(maxmp));

    stats.setMaxHp((short) maxhp);
    stats.setMaxMp((short) maxmp);
    stats.setHp((short) maxhp);
    stats.setMp((short) maxmp);

    final List<Pair<MapleStat, Integer>> statup = new ArrayList<Pair<MapleStat, Integer>>(7);
    statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXHP, maxhp));
    statup.add(new Pair<MapleStat, Integer>(MapleStat.MAXMP, maxmp));
    statup.add(new Pair<MapleStat, Integer>(MapleStat.HP, maxhp));
    statup.add(new Pair<MapleStat, Integer>(MapleStat.MP, maxmp));
    statup.add(new Pair<MapleStat, Integer>(MapleStat.EXP, exp));
    statup.add(new Pair<MapleStat, Integer>(MapleStat.LEVEL, (int) level));
    statup.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLEAP, remainingAp));

    if (!isEvan() && level >= 10 || (level > 8 && job == 200)) {
      remainingSp += 3;
      statup.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLESP, remainingSp));
    }

    client.getSession().write(MaplePacketCreator.updatePlayerStats(statup, getJob()));
    if ((isEvan()) && (this.getJobValue() != MapleJob.EVAN1)) {
      addEvanSP(3);
    }
    map.broadcastMessage(this, MaplePacketCreator.showForeignEffect(getId(), 0), false);
    stats.recalcLocalStats();

    checkForAchievements();
    sendDualbladeJobStarterMessage();
    sendLevel200Congratulations();
    silentPartyUpdate();
    guildUpdate();
    checkForChangeJob();
    equipChanged();

  }

  public void addEvanSP(int evanPoints) {
    this.evanSP.addSkillPoints(this.getJobValue().getId(), evanPoints);
    if (this.evanSP.getSkillPoints(this.getJobValue().getId()) < 0) {
      this.evanSP.setSkillPoints(this.getJobValue().getId(), 0);
    }
    this.client.getSession().write(MaplePacketCreator.updateExtendedSP(this.evanSP));

  }

  private boolean isPirate() {
    return job >= 500 && job <= 522;
  }

  private boolean isMage() {
    return job >= 200 && job <= 232;
  }

  private boolean isWarrior() {
    return job >= 100 && job <= 132;
  }

  private boolean isBeginner() {
    return job == 0 || job == 1000 || job == 2000 || job == 2001 || job == 3000;
  }

  private void checkForAchievements() {
    int level = getLevel();
    if (level >= 30) {
      finishAchievement(2);
    }
    if (level >= 70) {
      finishAchievement(3);
    }
    if (level >= 120) {
      finishAchievement(4);
    }
    if (level >= 200) {
      finishAchievement(5);
    }
  }

  private void sendDualbladeJobStarterMessage() {
    int level = getLevel();
    if (level == 2 && this.getSubCategoryField() == 1) {
      String shortMessage = "To become a DualBlade click on the lightbulb over you head";
      String completeMessage = shortMessage + " and start the quest Dualblade: The Seal of Destity";
      dropMessage(-1, shortMessage);
      dropMessage(5, completeMessage);

    }
  }

  private void sendLevel200Congratulations() {
    int level = getLevel();
    if (level == 200 && !isGM() || isCygnus() && level == 120) {
      final StringBuilder sb = new StringBuilder("[Congratulation] ");
      final IItem medal = getInventory(MapleInventoryType.EQUIPPED).getItem((byte) -46);
      if (medal != null) { // Medal
        sb.append("<");
        sb.append(MapleItemInformationProvider.getInstance().getName(medal.getItemId()));
        sb.append("> ");
      }
      sb.append(getName());
      sb.append(" has achieved Level " + level + ". Let us Celebrate Maplers!");
      World.Broadcast.broadcastMessage(MaplePacketCreator.serverNotice(6, sb.toString()));
    }
  }

  private void checkForChangeJob() {
    int level = getLevel();
    if (GameConstants.isKOC(job) && job > 1000) {
      final String base = (String.valueOf(job).substring(0, 2)) + "00";
      if (level >= 120 && job % 10 != 2 && job % 100 != 0) {
        changeJob(Integer.valueOf(base) + 12);
      } else if ((level >= 70 && level <= 119) && job % 10 != 1 && job % 100 != 0) {
        changeJob(Integer.valueOf(base) + 11);
      } else if ((level >= 30 && level <= 69) && job % 100 == 0) {
        changeJob(Integer.valueOf(base) + 10);
      }
    } else if (GameConstants.isEvan(job)) {
      if (level >= 160 && job != 2218) {
        changeJob(2218);
      } else if (level >= 120 && level <= 159 && job != 2217) {
        changeJob(2217);
      } else if (level >= 100 && level <= 119 && job != 2216) {
        changeJob(2216);
      } else if (level >= 80 && level <= 99 && job != 2215) {
        changeJob(2215);
      } else if (level >= 60 && level <= 79 && job != 2214) {
        changeJob(2214);
      } else if (level >= 50 && level <= 59 && job != 2213) {
        changeJob(2213);
      } else if (level >= 40 && level <= 49 && job != 2212) {
        changeJob(2212);
      } else if (level >= 30 && level <= 39 && job != 2211) {
        changeJob(2211);
      } else if (level >= 20 && level <= 29 && job != 2210) {
        changeJob(2210);
      } else if (level >= 10 && level <= 19 && job != 2200) {
        changeJob(2200);
      }
    }
  }

  public void changeKeybinding(int key, byte type, int action, byte fixed) {
    if (type != 0) {
      keylayout.Layout().put(Integer.valueOf(key), new Triple<>(type, action, fixed));
    } else {
      keylayout.Layout().remove(Integer.valueOf(key));
    }
  }

  public void sendMacros() {
    client.getSession().write(MaplePacketCreator.getMacros(skillMacros));
  }

  public void updateMacros(int position, SkillMacro updateMacro) {
    skillMacros[position] = updateMacro;
    changed_skillmacros = true;
  }

  public final SkillMacro[] getMacros() {
    return skillMacros;
  }

  public void tempban(String reason, Calendar duration, int greason, boolean IPMac) {
    if (IPMac) {
      client.banMacs();
    }

    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
      ps.setString(1, client.getSession().getRemoteAddress().toString().split(":")[0]);
      ps.execute();
      ps.close();

      client.getSession().close();

      ps = con.prepareStatement("UPDATE accounts SET tempban = ?, banreason = ?, greason = ? WHERE id = ?");
      Timestamp TS = new Timestamp(duration.getTimeInMillis());
      ps.setTimestamp(1, TS);
      ps.setString(2, reason);
      ps.setInt(3, greason);
      ps.setInt(4, accountid);
      ps.execute();
      ps.close();
    } catch (SQLException ex) {
      System.err.println("Error while tempbanning" + ex);
    }

  }

  public final boolean ban(String reason, boolean IPMac, boolean autoban, boolean hellban) {
    if (lastmonthfameids == null) {
      throw new RuntimeException("Trying to ban a non-loaded character (testhack)");
    }
    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con.prepareStatement("UPDATE accounts SET banned = ?, banreason = ? WHERE id = ?");
      ps.setInt(1, autoban ? 2 : 1);
      ps.setString(2, reason);
      ps.setInt(3, accountid);
      ps.execute();
      ps.close();

      if (IPMac) {
        client.banMacs();
        ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
        ps.setString(1, client.getSessionIPAddress());
        ps.execute();
        ps.close();

        if (hellban) {
          PreparedStatement psa = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
          psa.setInt(1, accountid);
          ResultSet rsa = psa.executeQuery();
          if (rsa.next()) {
            PreparedStatement pss = con.prepareStatement(
                    "UPDATE accounts SET banned = ?, banreason = ? WHERE email = ? OR SessionIP = ?");
            pss.setInt(1, autoban ? 2 : 1);
            pss.setString(2, reason);
            pss.setString(3, rsa.getString("email"));
            pss.setString(4, client.getSessionIPAddress());
            pss.execute();
            pss.close();
          }
          rsa.close();
          psa.close();
        }
      }
    } catch (SQLException ex) {
      System.err.println("Error while banning" + ex);
      return false;
    }
    client.getSession().close();
    return true;
  }

  public static boolean ban(String id, String reason, boolean accountId, int gmlevel, boolean hellban) {
    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps;
      if (id.matches("/[0-9]{1,3}\\..*")) {
        ps = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
        ps.setString(1, id);
        ps.execute();
        ps.close();
        return true;
      }
      if (accountId) {
        ps = con.prepareStatement("SELECT id FROM accounts WHERE name = ?");
      } else {
        ps = con.prepareStatement("SELECT accountid FROM characters WHERE name = ?");
      }
      boolean ret = false;
      ps.setString(1, id);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        int z = rs.getInt(1);
        PreparedStatement psb = con
                .prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE id = ? AND gm < ?");
        psb.setString(1, reason);
        psb.setInt(2, z);
        psb.setInt(3, gmlevel);
        psb.execute();
        psb.close();

        if (gmlevel > 100) { // admin ban
          PreparedStatement psa = con.prepareStatement("SELECT * FROM accounts WHERE id = ?");
          psa.setInt(1, z);
          ResultSet rsa = psa.executeQuery();
          if (rsa.next()) {
            String sessionIP = rsa.getString("sessionIP");
            if (sessionIP != null && sessionIP.matches("/[0-9]{1,3}\\..*")) {
              PreparedStatement psz = con.prepareStatement("INSERT INTO ipbans VALUES (DEFAULT, ?)");
              psz.setString(1, sessionIP);
              psz.execute();
              psz.close();
            }
            if (rsa.getString("macs") != null) {
              String[] macData = rsa.getString("macs").split(", ");
              if (macData.length > 0) {
                MapleClient.banMacs(macData);
              }
            }
            if (hellban) {
              PreparedStatement pss = con
                      .prepareStatement("UPDATE accounts SET banned = 1, banreason = ? WHERE email = ?"
                              + (sessionIP == null ? "" : " OR SessionIP = ?"));
              pss.setString(1, reason);
              pss.setString(2, rsa.getString("email"));
              if (sessionIP != null) {
                pss.setString(3, sessionIP);
              }
              pss.execute();
              pss.close();
            }
          }
          rsa.close();
          psa.close();
        }
        ret = true;
      }
      rs.close();
      ps.close();
      return ret;
    } catch (SQLException ex) {
      System.err.println("Error while banning" + ex);
    }
    return false;
  }

  /**
   * Oid of players is always = the cid
   */
  @Override
  public int getObjectId() {
    return getId();
  }

  /**
   * Throws unsupported operation exception, oid of players is read only
   */
  @Override
  public void setObjectId(int id) {
    throw new UnsupportedOperationException();
  }

  public MapleStorage getStorage() {
    return storage;
  }

  public void addVisibleMapObject(MapleMapObject mo) {
    visibleMapObjectsLock.writeLock().lock();
    try {
      visibleMapObjects.add(mo);
    } finally {
      visibleMapObjectsLock.writeLock().unlock();
    }
  }

  public void removeVisibleMapObject(MapleMapObject mo) {
    visibleMapObjectsLock.writeLock().lock();
    try {
      visibleMapObjects.remove(mo);
    } finally {
      visibleMapObjectsLock.writeLock().unlock();
    }
  }

  public boolean isMapObjectVisible(MapleMapObject mo) {
    visibleMapObjectsLock.readLock().lock();
    try {
      return visibleMapObjects.contains(mo);
    } finally {
      visibleMapObjectsLock.readLock().unlock();
    }
  }

  public Collection<MapleMapObject> getAndWriteLockVisibleMapObjects() {
    visibleMapObjectsLock.writeLock().lock();
    return visibleMapObjects;
  }

  public void unlockWriteVisibleMapObjects() {
    visibleMapObjectsLock.writeLock().unlock();
  }

  public boolean isAlive() {
    return stats.getHp() > 0;
  }

  @Override
  public void sendDestroyData(MapleClient client) {
    client.getSession().write(MaplePacketCreator.removePlayerFromMap(this.getObjectId()));
  }

  @Override
  public void sendSpawnData(MapleClient client) {
    // System.out.println(this + " Sending spawn data to " +
    // client.getPlayer().getName());
    if (client.getPlayer().allowedToTarget(this)) {
      client.getSession().write(MaplePacketCreator.spawnPlayerMapobject(this));
      if (party != null) {
        boolean isOnParty = this.getParty().getMemberById(client.getPlayer().getId()) != null;
        if (isOnParty) {
          client.sendPacket(MapleUserPackets.updatePartyHpForCharacter(this));
          getClient().sendPacket(MapleUserPackets.updatePartyHpForCharacter(client.getPlayer()));
        }

      }

      if (dragon != null) {
        client.getSession().write(MaplePacketCreator.spawnDragon(dragon));
      }
      if (summons != null) {
        for (final MapleSummon summon : summons.values()) {
          client.getSession().write(MaplePacketCreator.spawnSummon(summon, false));
        }
      }
      if (pets != null) {
        for (MaplePet pet : pets) {
          if (pet.getSummoned()) {
            client.sendPacket(PetPacket.showPet(this, pet, false, false));
          }
        }
      }
      if (followid > 0 && followon) {
        client.getSession().write(MaplePacketCreator.followEffect(followinitiator ? followid : id,
                followinitiator ? id : followid, null));
      }
    }
  }

  public final void equipChanged() {
    map.broadcastMessage(this, MaplePacketCreator.updateCharLook(this), false);
    stats.recalcLocalStats();
    if (getMessenger() != null) {
      World.Messenger.updateMessenger(getMessenger().getId(), getName(), client.getChannel());
    }
  }

  public final MaplePet getPet(final int index) {
    byte count = 0;
    if (pets == null) {
      return null;
    }
    for (final MaplePet pet : pets) {
      if (pet.getSummoned()) {
        if (count == index) {
          return pet;
        }
        count++;
      }
    }
    return null;
  }

  public final MaplePet getPetByUID(final int uid) {
    if (pets == null) {
      return null;
    }
    for (final MaplePet pet : pets) {
      if (pet.getSummoned()) {
        if (pet.getUniqueId() == uid) {
          return pet;
        }
      }
    }
    return null;
  }

  public void removePetCS(MaplePet pet) {
    pets.remove(pet);
  }

  public void addPet(final MaplePet pet) {
    if (pets.contains(pet)) {
      pets.remove(pet);
    }
    pets.add(pet);
    // So that the pet will be at the last
    // Pet index logic :(
  }

  public void removePet(MaplePet pet, boolean shiftLeft) {
    pet.setSummoned(false);
  }

  public final byte getPetIndex(final MaplePet petz) {
    byte count = 0;
    for (final MaplePet pet : pets) {
      if (pet.getSummoned()) {
        if (pet == petz) {
          return count;
        }
        count++;
      }
    }
    return -1;
  }

  public final ArrayList<MaplePet> getSummonedPets() {
    return getSummonedPets(new ArrayList<MaplePet>());
  }

  public final ArrayList<MaplePet> getSummonedPets(ArrayList<MaplePet> ret) {
    ret.clear();
    for (final MaplePet pet : pets) {
      if (pet.getSummoned()) {
        ret.add(pet);
      }
    }
    return ret;
  }

  public final byte getPetIndex(final int petId) {
    byte count = 0;
    for (final MaplePet pet : pets) {
      if (pet.getSummoned()) {
        if (pet.getUniqueId() == petId) {
          return count;
        }
        count++;
      }
    }
    return -1;
  }

  public final byte getPetById(final int petId) {
    byte count = 0;
    for (final MaplePet pet : pets) {
      if (pet.getSummoned()) {
        if (pet.getPetItemId() == petId) {
          return count;
        }
        count++;
      }
    }
    return -1;
  }

  public final List<MaplePet> getPets() {
    return pets;
  }

  public final void unequipAllPets() {
    for (final MaplePet pet : pets) {
      if (pet != null) {
        unequipPet(pet, true, false);
      }
    }
  }

  public void unequipPet(MaplePet pet, boolean shiftLeft, boolean hunger) {
    if (pet.getSummoned()) {
      pet.saveToDb();
      map.broadcastMessage(this, PetPacket.showPet(this, pet, true, hunger), true);
      // List<Pair<MapleStat, Integer>> stats = new
      // ArrayList<Pair<MapleStat, Integer>>();
      // stats.add(new Pair<MapleStat, Integer>(MapleStat.PET,
      // Integer.valueOf(0)));
      removePet(pet, shiftLeft);
      client.getSession().write(PetPacket.petStatUpdate(this));
      client.getSession().write(MaplePacketCreator.enableActions());
    }
  }

  public final long getLastFameTime() {
    return lastfametime;
  }

  public final List<Integer> getFamedCharacters() {
    return lastmonthfameids;
  }

  public FameStatus canGiveFame(MapleCharacter from) {
    if (lastfametime >= System.currentTimeMillis() - 60 * 60 * 24 * 1000) {
      return FameStatus.NOT_TODAY;
    } else if (from == null || lastmonthfameids == null
            || lastmonthfameids.contains(Integer.valueOf(from.getId()))) {
      return FameStatus.NOT_THIS_MONTH;
    }
    return FameStatus.OK;
  }

  public void hasGivenFame(MapleCharacter to) {
    lastfametime = System.currentTimeMillis();
    lastmonthfameids.add(Integer.valueOf(to.getId()));
    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con
              .prepareStatement("INSERT INTO famelog (characterid, characterid_to) VALUES (?, ?)");
      ps.setInt(1, getId());
      ps.setInt(2, to.getId());
      ps.execute();
      ps.close();
    } catch (SQLException e) {
      System.err.println("ERROR writing famelog for char " + getName() + " to " + to.getName() + e);
    }
  }

  public final MapleKeyLayout getKeyLayout() {
    return this.keylayout;
  }

  public MapleParty getParty() {
    if (party == null) {
      return null;
    }
    if (party.isDisbanded()) {
      return null;
    }
    return party;
  }

  public int getPartyId() {
    return (party != null ? party.getId() : -1);
  }

  public byte getWorld() {
    return world;
  }

  public void setWorld(byte world) {
    this.world = world;
  }

  public void setParty(MapleParty party) {
    this.party = party;
  }

  public MapleTrade getTrade() {
    return trade;
  }

  public void setTrade(MapleTrade trade) {
    this.trade = trade;
  }

  public EventInstanceManager getEventInstance() {
    return eventInstance;
  }

  public void setEventInstance(EventInstanceManager eventInstance) {
    this.eventInstance = eventInstance;
  }

  public void addDoor(MapleDoor door) {
    doors.add(door);
  }

  public void clearDoors() {
    doors.clear();
  }

  public List<MapleDoor> getDoors() {
    return new ArrayList<MapleDoor>(doors);
  }

  public void setSmega() {
    if (smega) {
      smega = false;
      dropMessage(5, "You have set megaphone to disabled mode");
    } else {
      smega = true;
      dropMessage(5, "You have set megaphone to enabled mode");
    }
  }

  public boolean getSmega() {
    return smega;
  }

  public Map<Integer, MapleSummon> getSummons() {
    return summons;
  }

  public int getChair() {
    return chair;
  }

  public int getItemEffect() {
    return itemEffect;
  }

  public void setChair(int chair) {
    this.chair = chair;
    stats.relocHeal();
  }

  public void setItemEffect(int itemEffect) {
    this.itemEffect = itemEffect;
  }

  @Override
  public MapleMapObjectType getType() {
    return MapleMapObjectType.PLAYER;
  }

  public int getGuildId() {
    return guildid;
  }

  public byte getGuildRank() {
    return guildrank;
  }

  public void setGuildId(int _id) {
    guildid = _id;
    if (guildid > 0) {
      if (mgc == null) {
        mgc = new MapleGuildCharacter(this);

      } else {
        mgc.setGuildId(guildid);
      }
    } else {
      mgc = null;
    }
  }

  public void setGuildRank(byte _rank) {
    guildrank = _rank;
    if (mgc != null) {
      mgc.setGuildRank(_rank);
    }
  }

  public MapleGuildCharacter getMGC() {
    return mgc;
  }

  public void setAllianceRank(byte rank) {
    allianceRank = rank;
    if (mgc != null) {
      mgc.setAllianceRank(rank);
    }
  }

  public byte getAllianceRank() {
    return allianceRank;
  }

  public MapleGuild getGuild() {
    if (getGuildId() <= 0) {
      return null;
    }
    return World.Guild.getGuild(getGuildId());
  }

  public void guildUpdate() {
    if (guildid <= 0) {
      return;
    }
    mgc.setLevel((short) level);
    mgc.setJobId(job);
    World.Guild.memberLevelJobUpdate(mgc);
  }

  public void saveGuildStatus() {
    MapleGuild.setOfflineGuildStatus(guildid, guildrank, allianceRank, id);
  }

  public void modifyCSPoints(int type, int quantity) {
    modifyCSPoints(type, quantity, false);
  }

  public void modifyCSPoints(int type, int quantity, boolean show) {
    switch (type) {
      case 1:
      case 4:
        if (nxcredit + quantity < 0) {
          if (show) {
            dropMessage(-1, "You have gained the max cash. No cash will be awarded.");
          }
          return;
        }
        nxcredit += quantity;
        break;
      case 2:
        if (maplepoints + quantity < 0) {
          if (show) {
            dropMessage(-1, "You have gained the max maple points. No cash will be awarded.");
          }
          return;
        }
        maplepoints += quantity;
        break;
    }
    if (show && quantity != 0) {
      dropMessage(-1, "You have " + (quantity > 0 ? "gained " : "lost ") + quantity
              + (type == 1 ? " cash." : " maple points."));
      // client.getSession().write(MaplePacketCreator.showSpecialEffect(19));
    }
  }

  public int getCSPoints(int type) {
    switch (type) {
      case 1:
      case 4:
        return nxcredit;
      case 2:
        return maplepoints;
    }
    return 0;
  }

  public final boolean hasEquipped(int itemid) {
    return inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid) >= 1;
  }

  public final boolean haveItem(int itemid, int quantity, boolean checkEquipped, boolean greaterOrEquals) {
    final MapleInventoryType type = GameConstants.getInventoryType(itemid);
    int possesed = inventory[type.ordinal()].countById(itemid);
    if (checkEquipped && type == MapleInventoryType.EQUIP) {
      possesed += inventory[MapleInventoryType.EQUIPPED.ordinal()].countById(itemid);
    }
    if (greaterOrEquals) {
      return possesed >= quantity;
    } else {
      return possesed == quantity;
    }
  }

  public final boolean haveItem(int itemid, int quantity) {
    return haveItem(itemid, quantity, true, true);
  }

  public final boolean haveItem(int itemid) {
    return haveItem(itemid, 1, true, true);
  }

  public static enum FameStatus {

    OK, NOT_TODAY, NOT_THIS_MONTH
  }

  public byte getBuddyCapacity() {
    return buddylist.getCapacity();
  }

  public void setBuddyCapacity(byte capacity) {
    buddylist.setCapacity(capacity);
    client.getSession().write(MaplePacketCreator.updateBuddyCapacity(capacity));
  }

  public MapleMessenger getMessenger() {
    return messenger;
  }

  public void setMessenger(MapleMessenger messenger) {
    this.messenger = messenger;
  }

  public void addCooldown(int skillId, long startTime, long length) {
    coolDowns.put(Integer.valueOf(skillId), new MapleCoolDownValueHolder(skillId, startTime, length));
  }

  public void removeCooldown(int skillId) {
    if (coolDowns.containsKey(Integer.valueOf(skillId))) {
      coolDowns.remove(Integer.valueOf(skillId));
    }
  }

  public boolean skillisCooling(int skillId) {
    return coolDowns.containsKey(Integer.valueOf(skillId));
  }

  public void giveCoolDowns(final int skillid, long starttime, long length) {
    addCooldown(skillid, starttime, length);
  }

  public void giveCoolDowns(final List<MapleCoolDownValueHolder> cooldowns) {
    int time;
    if (cooldowns != null) {
      for (MapleCoolDownValueHolder cooldown : cooldowns) {
        coolDowns.put(cooldown.skillId, cooldown);
      }
    } else {
      try {
        Connection con = DatabaseConnection.getConnection();
        PreparedStatement ps = con
                .prepareStatement("SELECT SkillID,StartTime,length FROM skills_cooldowns WHERE charid = ?");
        ps.setInt(1, getId());
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
          if (rs.getLong("length") + rs.getLong("StartTime") - System.currentTimeMillis() <= 0) {
            continue;
          }
          giveCoolDowns(rs.getInt("SkillID"), rs.getLong("StartTime"), rs.getLong("length"));
        }
        ps.close();
        rs.close();
        deleteWhereCharacterId(con, "DELETE FROM skills_cooldowns WHERE charid = ?");

      } catch (SQLException e) {
        System.err.println("Error while retriving cooldown from SQL storage");
      }
    }
  }

  public int getCooldownSize() {
    return coolDowns.size();
  }

  public int getDiseaseSize() {
    return diseases.size();
  }

  public final List<MapleCoolDownValueHolder> getCooldowns() {
    return getCooldowns(new ArrayList<MapleCoolDownValueHolder>());
  }

  public ArrayList<MapleCoolDownValueHolder> getCooldowns(ArrayList<MapleCoolDownValueHolder> ret) {
    ret.clear();
    for (MapleCoolDownValueHolder mc : coolDowns.values()) {
      if (mc != null) {
        ret.add(mc);
      }
    }
    return ret;
  }

  public final List<MapleDiseaseValueHolder> getAllDiseases() {
    return getAllDiseases(new ArrayList<MapleDiseaseValueHolder>());
  }

  public final ArrayList<MapleDiseaseValueHolder> getAllDiseases(ArrayList<MapleDiseaseValueHolder> ret) {
    ret.clear();
    for (MapleDiseaseValueHolder mc : diseases.values()) {
      if (mc != null) {
        ret.add(mc);
      }
    }
    return ret;
  }

  public final boolean hasDisease(final MapleDisease dis) {
    return diseases.keySet().contains(dis);
  }

  public void giveDebuff(final MapleDisease disease, MobSkill skill) {
    giveDebuff(disease, skill.getX(), skill.getDuration(), skill.getSkillId(), skill.getSkillLevel());
  }

  public void giveDebuff(final MapleDisease disease, int x, long duration, int skillid, int level) {
    final List<Pair<MapleDisease, Integer>> debuff = Collections
            .singletonList(new Pair<MapleDisease, Integer>(disease, Integer.valueOf(x)));

    if (!hasDisease(disease) && diseases.size() < 2) {
      if (!(disease == MapleDisease.SEDUCE || disease == MapleDisease.STUN)) {
        if (isActiveBuffedValue(2321005)) {
          return;
        }
      }

      diseases.put(disease, new MapleDiseaseValueHolder(disease, System.currentTimeMillis(), duration));
      client.getSession().write(MaplePacketCreator.giveDebuff(debuff, skillid, level, (int) duration));
      map.broadcastMessage(this, MaplePacketCreator.giveForeignDebuff(id, debuff, skillid, level), false);
    }
  }

  public final void giveSilentDebuff(final List<MapleDiseaseValueHolder> ld) {
    if (ld != null) {
      for (final MapleDiseaseValueHolder disease : ld) {
        diseases.put(disease.disease, disease);
      }
    }
  }

  public void dispelDebuff(MapleDisease debuff) {
    if (hasDisease(debuff)) {
      long mask = debuff.getValue();
      boolean first = debuff.isFirst();
      client.getSession().write(MaplePacketCreator.cancelDebuff(mask, first));
      map.broadcastMessage(this, MaplePacketCreator.cancelForeignDebuff(id, mask, first), false);

      diseases.remove(debuff);
    }
  }

  public void dispelDebuffs() {
    dispelDebuff(MapleDisease.CURSE);
    dispelDebuff(MapleDisease.DARKNESS);
    dispelDebuff(MapleDisease.POISON);
    dispelDebuff(MapleDisease.SEAL);
    dispelDebuff(MapleDisease.WEAKEN);
  }

  public void cancelAllDebuffs() {
    diseases.clear();
  }

  public void setLevel(final short level) {
    this.level = (short) (level - 1);
  }

  public void sendNote(String to, String msg) {
    sendNote(to, msg, 0);
  }

  public void sendNote(String to, String msg, int fame) {
    MapleCharacterUtil.sendNote(to, getName(), msg, fame);
  }

  public void showNote() {
    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con.prepareStatement("SELECT * FROM notes WHERE `to`=?",
              ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
      ps.setString(1, getName());
      ResultSet rs = ps.executeQuery();
      rs.last();
      int count = rs.getRow();
      rs.first();
      client.getSession().write(MTSCSPacket.showNotes(rs, count));
      rs.close();
      ps.close();
    } catch (SQLException e) {
      System.err.println("Unable to show note" + e);
    }
  }

  public void deleteNote(int id, int fame) {
    try {
      Connection con = DatabaseConnection.getConnection();
      PreparedStatement ps = con.prepareStatement("SELECT gift FROM notes WHERE `id`=?");
      ps.setInt(1, id);
      ResultSet rs = ps.executeQuery();
      if (rs.next()) {
        if (rs.getInt("gift") == fame && fame > 0) { // not exploited!
          // hurray
          addFame(fame);
          updateSingleStat(MapleStat.FAME, getFame());
          client.getSession().write(MaplePacketCreator.getShowFameGain(fame));
        }
      }
      rs.close();
      ps.close();
      ps = con.prepareStatement("DELETE FROM notes WHERE `id`=?");
      ps.setInt(1, id);
      ps.execute();
      ps.close();
    } catch (SQLException e) {
      System.err.println("Unable to delete note" + e);
    }
  }

  public void mulung_EnergyModify(boolean inc) {
    if (inc) {
      if (mulung_energy + 100 > 10000) {
        mulung_energy = 10000;
      } else {
        mulung_energy += 100;
      }
    } else {
      mulung_energy = 0;
    }
    client.getSession().write(MaplePacketCreator.MulungEnergy(mulung_energy));
  }

  public void writeMulungEnergy() {
    client.getSession().write(MaplePacketCreator.MulungEnergy(mulung_energy));
  }

  public void writeEnergy(String type, String inc) {
    client.getSession().write(MaplePacketCreator.sendPyramidEnergy(type, inc));
  }

  public void writeStatus(String type, String inc) {
    client.getSession().write(MaplePacketCreator.sendGhostStatus(type, inc));
  }

  public void writePoint(String type, String inc) {
    client.getSession().write(MaplePacketCreator.sendGhostPoint(type, inc));
  }

  public final short getCombo() {
    return combo;
  }

  public void setCombo(final short combo) {
    this.combo = combo;
  }

  public final long getLastCombo() {
    return lastCombo;
  }

  public void setLastCombo(final long combo) {
    this.lastCombo = combo;
  }

  public final long getKeyDownSkill_Time() {
    return keydown_skill;
  }

  public void setKeyDownSkill_Time(final long keydown_skill) {
    this.keydown_skill = keydown_skill;
  }

  public void checkBerserk() {
    if (/* job != 132 || */lastBerserkTime < 0 || lastBerserkTime + 10000 > System.currentTimeMillis()) {
      return;
    }
    final ISkill BerserkX = SkillFactory.getSkill(1320006);
    final int skilllevel = getSkillLevel(BerserkX);
    if (skilllevel >= 1 && map != null) {
      lastBerserkTime = System.currentTimeMillis();
      final MapleStatEffect ampStat = BerserkX.getEffect(skilllevel);
      stats.Berserk = (stats.getHp() * 100) / stats.getCurrentMaxHp() <= ampStat.getX();
      client.getSession().write(MaplePacketCreator.showOwnBuffEffect(1320006, 1, (byte) (stats.Berserk ? 1 : 0)));
      map.broadcastMessage(this,
              MaplePacketCreator.showBuffeffect(getId(), 1320006, 1, (byte) (stats.Berserk ? 1 : 0)), false);
    } else {
      lastBerserkTime = -1;
    }
  }

  private void prepareBeholderEffect() {
    if (beholderHealingSchedule != null) {
      beholderHealingSchedule.cancel(false);
    }
    if (beholderBuffSchedule != null) {
      beholderBuffSchedule.cancel(false);
    }
    ISkill bHealing = SkillFactory.getSkill(1320008);
    final int bHealingLvl = getSkillLevel(bHealing);
    final int berserkLvl = getSkillLevel(SkillFactory.getSkill(1320006));

    if (bHealingLvl > 0) {
      final MapleStatEffect healEffect = bHealing.getEffect(bHealingLvl);
      int healInterval = healEffect.getX() * 1000;
      beholderHealingSchedule = BuffTimer.getInstance().register(new Runnable() {

        @Override
        public void run() {
          int remhppercentage = (int) Math.ceil((getStat().getHp() * 100.0) / getStat().getMaxHp());
          if (berserkLvl == 0 || remhppercentage >= berserkLvl + 10) {
            addHP(healEffect.getHp());
          }
          client.getSession().write(MaplePacketCreator.showOwnBuffEffect(1321007, 2));
          map.broadcastMessage(MaplePacketCreator.summonSkill(getId(), 1321007, 5));
          map.broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), 1321007, 2),
                  false);
        }
      }, healInterval, healInterval);
    }
    ISkill bBuff = SkillFactory.getSkill(1320009);
    final int bBuffLvl = getSkillLevel(bBuff);
    if (bBuffLvl > 0) {
      final MapleStatEffect buffEffect = bBuff.getEffect(bBuffLvl);
      int buffInterval = buffEffect.getX() * 1000;
      beholderBuffSchedule = BuffTimer.getInstance().register(new Runnable() {

        @Override
        public void run() {
          buffEffect.applyTo(MapleCharacter.this);
          client.getSession().write(MaplePacketCreator.showOwnBuffEffect(1321007, 2));
          map.broadcastMessage(MaplePacketCreator.summonSkill(getId(), 1321007, Randomizer.nextInt(3) + 6));
          map.broadcastMessage(MapleCharacter.this, MaplePacketCreator.showBuffeffect(getId(), 1321007, 2),
                  false);
        }
      }, buffInterval, buffInterval);
    }
  }

  public void setChalkboard(String text) {
    this.chalktext = text;
    map.broadcastMessage(MTSCSPacket.useChalkboard(getId(), text));
  }

  public String getChalkboard() {
    return chalktext;
  }

  public MapleMount getMount() {
    return mount;
  }

  public int[] getWishlist() {
    return wishlist;
  }

  public void clearWishlist() {
    for (int i = 0; i < 10; i++) {
      wishlist[i] = 0;
    }
    changed_wishlist = true;
  }

  public int getWishlistSize() {
    int ret = 0;
    for (int i = 0; i < 10; i++) {
      if (wishlist[i] > 0) {
        ret++;
      }
    }
    return ret;
  }

  public void setWishlist(int[] wl) {
    this.wishlist = wl;
    this.changed_wishlist = true;
  }

  public int[] getRocks() {
    return rocks;
  }

  public int getRockSize() {
    int ret = 0;
    for (int i = 0; i < 10; i++) {
      if (rocks[i] != 999999999) {
        ret++;
      }
    }
    return ret;
  }

  public void deleteFromRocks(int map) {
    for (int i = 0; i < 10; i++) {
      if (rocks[i] == map) {
        rocks[i] = 999999999;
        changed_trocklocations = true;
        break;
      }
    }
  }

  public void addRockMap() {
    if (getRockSize() >= 10) {
      return;
    }
    rocks[getRockSize()] = getMapId();
    changed_trocklocations = true;
  }

  public boolean isRockMap(int id) {
    for (int i = 0; i < 10; i++) {
      if (rocks[i] == id) {
        return true;
      }
    }
    return false;
  }

  public int[] getRegRocks() {
    return regrocks;
  }

  public int getRegRockSize() {
    int ret = 0;
    for (int i = 0; i < 5; i++) {
      if (regrocks[i] != 999999999) {
        ret++;
      }
    }
    return ret;
  }

  public void deleteFromRegRocks(int map) {
    for (int i = 0; i < 5; i++) {
      if (regrocks[i] == map) {
        regrocks[i] = 999999999;
        changed_regrocklocations = true;
        break;
      }
    }
  }

  public void addRegRockMap() {
    if (getRegRockSize() >= 5) {
      return;
    }
    regrocks[getRegRockSize()] = getMapId();
    changed_regrocklocations = true;
  }

  public boolean isRegRockMap(int id) {
    for (int i = 0; i < 5; i++) {
      if (regrocks[i] == id) {
        return true;
      }
    }
    return false;
  }

  public void setMonsterBookCover(int bookCover) {
    this.bookCover = bookCover;
  }

  public int getMonsterBookCover() {
    return bookCover;
  }

  public void dropMessage(int type, String message) {
    dropMessage(type, message, false);
  }

  public void dropMessage(int type, String message, boolean mappp) {
    if (type == -1) {
      client.getSession().write(UIPacket.getTopMsg(message));
    } else if (type == -2) {
      client.getSession().write(PlayerShopPacket.shopChat(message, 0)); // 0
      // or
      // what
    } else if (type == -3) {
      if (mappp) {
        map.broadcastMessage(MaplePacketCreator.getChatText(getId(), message, false, 1));
      } else {
        client.getSession().write(MaplePacketCreator.getChatText(getId(), message, false, 1));
      }
    } else {
      client.getSession().write(MaplePacketCreator.serverNotice(type, message));
    }
  }

  public IMaplePlayerShop getPlayerShop() {
    return playerShop;
  }

  public void setPlayerShop(IMaplePlayerShop playerShop) {
    this.playerShop = playerShop;
  }

  public int getConversation() {
    return inst.get();
  }

  public void setConversation(int inst) {
    this.inst.set(inst);
  }

  public MapleCarnivalParty getCarnivalParty() {
    return carnivalParty;
  }

  public void setCarnivalParty(MapleCarnivalParty party) {
    carnivalParty = party;
  }

  public void addCP(int ammount) {
    totalCP += ammount;
    availableCP += ammount;
  }

  public void useCP(int ammount) {
    availableCP -= ammount;
  }

  public int getAvailableCP() {
    return availableCP;
  }

  public int getTotalCP() {
    return totalCP;
  }

  public void resetCP() {
    totalCP = 0;
    availableCP = 0;
  }

  public void addCarnivalRequest(MapleCarnivalChallenge request) {
    pendingCarnivalRequests.add(request);
  }

  public final MapleCarnivalChallenge getNextCarnivalRequest() {
    return pendingCarnivalRequests.pollLast();
  }

  public void clearCarnivalRequests() {
    pendingCarnivalRequests = new LinkedList<MapleCarnivalChallenge>();
  }

  public void startMonsterCarnival(final int enemyavailable, final int enemytotal) {
    client.getSession().write(MonsterCarnivalPacket.startMonsterCarnival(this, enemyavailable, enemytotal));
  }

  public void CPUpdate(final boolean party, final int available, final int total, final int team) {
    client.getSession().write(MonsterCarnivalPacket.CPUpdate(party, available, total, team));
  }

  public void playerDiedCPQ(final String name, final int lostCP, final int team) {
    client.getSession().write(MonsterCarnivalPacket.playerDiedMessage(name, lostCP, team));
  }

  public void setAchievementFinished(int id) {
    if (!finishedAchievements.contains(id)) {
      finishedAchievements.add(id);
      changed_achievements = true;
    }
  }

  public boolean achievementFinished(int achievementid) {
    return finishedAchievements.contains(achievementid);
  }

  public void finishAchievement(int id) {
    if (!achievementFinished(id)) {
      if (isAlive()) {
        MapleAchievements.getInstance().getById(id).finishAchievement(this);
      }
    }
  }

  public List<Integer> getFinishedAchievements() {
    return finishedAchievements;
  }

  public void modifyAchievementCSPoints(int type, int quantity) {
    switch (type) {
      case 1:
      case 4:
        nxcredit += quantity;
        break;
      case 2:
        maplepoints += quantity;
        break;
    }
  }

  public boolean getCanTalk() {
    return this.canTalk;
  }

  public void canTalk(boolean talk) {
    this.canTalk = talk;
  }

  public int getEXPMod() {
    return stats.expMod;
  }

  public int getDropMod() {
    return stats.dropMod;
  }

  public int getCashMod() {
    return stats.cashMod;
  }

  public void setPoints(int p) {
    this.points = p;
    if (this.points >= 1) {
      finishAchievement(1);
    }
  }

  public int getPoints() {
    return points;
  }


  public CashShop getCashInventory() {
    return cs;
  }

  public void removeAll(int id) {
    MapleInventoryType type = GameConstants.getInventoryType(id);
    int possessed = getInventory(type).countById(id);

    if (possessed > 0) {
      MapleInventoryManipulator.removeById(getClient(), type, id, possessed, true, false);
      getClient().getSession().write(MaplePacketCreator.getShowItemGain(id, (short) -possessed, true));
    }
  }

  public Triple<List<MapleRing>, List<MapleRing>, List<MapleRing>> getRings(boolean equip) {
    MapleInventory iv = getInventory(MapleInventoryType.EQUIPPED);
    Collection<IItem> equippedC = iv.list();
    List<Item> equipped = new ArrayList<>(equippedC.size());
    for (IItem item : equippedC) {
      equipped.add((Item) item);
    }
    Collections.sort(equipped);
    List<MapleRing> crings = new ArrayList<>();
    List<MapleRing> frings = new ArrayList<>();
    List<MapleRing> mrings = new ArrayList<>();
    MapleRing ring;
    for (Item item : equipped) {
      if (item.getRing() != null) {
        ring = item.getRing();
        ring.setEquipped(true);
        if (GameConstants.isFriendshipRing(item.getItemId()) || GameConstants.isCrushRing(item.getItemId())) {
          if (equip) {
            if (GameConstants.isCrushRing(item.getItemId())) {
              crings.add(ring);
            } else if (GameConstants.isFriendshipRing(item.getItemId())) {
              frings.add(ring);
            } else if (GameConstants.isMarriageRing(item.getItemId())) {
              mrings.add(ring);
            }
          } else {
            if (crings.isEmpty() && GameConstants.isCrushRing(item.getItemId())) {
              crings.add(ring);
            } else if (frings.isEmpty() && GameConstants.isFriendshipRing(item.getItemId())) {
              frings.add(ring);
            } else if (mrings.isEmpty() && GameConstants.isMarriageRing(item.getItemId())) {
              mrings.add(ring);
            } // for 3rd person the actual slot doesnt matter, so
            // we'll use this to have both shirt/ring same?
            // however there seems to be something else behind
            // this, will have to sniff someone with shirt and
            // ring, or more conveniently 3-4 of those
          }
        }
      }
    }
    if (equip) {
      iv = getInventory(MapleInventoryType.EQUIP);
      for (IItem item : iv.list()) {
        if (item.getRing() != null && GameConstants.isCrushRing(item.getItemId())) {
          ring = item.getRing();
          ring.setEquipped(false);
          if (GameConstants.isFriendshipRing(item.getItemId())) {
            frings.add(ring);
          } else if (GameConstants.isCrushRing(item.getItemId())) {
            crings.add(ring);
          } else if (GameConstants.isMarriageRing(item.getItemId())) {
            mrings.add(ring);
          }
        }
      }
    }
    Collections.sort(frings, new MapleRing.RingComparator());
    Collections.sort(crings, new MapleRing.RingComparator());
    Collections.sort(mrings, new MapleRing.RingComparator());
    return new Triple<>(crings, frings, mrings);
  }

  public int getFH() {
    MapleFoothold fh = getMap().getFootholds().findBelow(getPosition());
    if (fh != null) {
      return fh.getId();
    }
    return 0;
  }

  public int getCoconutTeam() {
    return coconutteam;
  }

  public void setCoconutTeam(int team) {
    coconutteam = team;
  }

  public void spawnPet(byte slot) {
    spawnPet(slot, false, true);
  }

  public void spawnPet(byte slot, boolean lead) {
    spawnPet(slot, lead, true);
  }

  public void spawnPet(byte slot, boolean lead, boolean broadcast) {
    final IItem item = getInventory(MapleInventoryType.CASH).getItem(slot);
    if (item == null || item.getItemId() > 5000100 || item.getItemId() < 5000000) {
      return;
    }
    switch (item.getItemId()) {
      case 5000047:
      case 5000028: {
        final MaplePet pet = MaplePet.createPet(item.getItemId() + 1, MapleInventoryIdentifier.getInstance());
        if (pet != null) {
          MapleInventoryManipulator.addById(client, item.getItemId() + 1, (short) 1, item.getOwner(), pet, 45);
          MapleInventoryManipulator.removeFromSlot(client, MapleInventoryType.CASH, slot, (short) 1, false);
        }
        break;
      }
      default: {
        final MaplePet pet = item.getPet();
        if (pet != null && (item.getItemId() != 5000054 || pet.getSecondsLeft() > 0)
                && (item.getExpiration() == -1 || item.getExpiration() > System.currentTimeMillis())) {

          int leadid = 8;
          if (GameConstants.isKOC(getJob())) {
            leadid = 10000018;
          } else if (GameConstants.isAran(getJob())) {
            leadid = 20000024;
          } else if (GameConstants.isEvan(getJob())) {
            leadid = 20011024;
          } else if (GameConstants.isResist(getJob())) {
            leadid = 30000024;
          }
          if (getSkillLevel(SkillFactory.getSkill(leadid)) == 0 && getPet(0) != null) {
            unequipPet(getPet(0), false, false);
          }

          pet.setPos(getPosition());
          try {
            pet.setFh(this.getFH());
          } catch (NullPointerException e) {
            pet.setFh(0); // lol, it can be fixed by movement
          }
          pet.setStance(0);
          pet.setSummoned(true);

          addPet(pet);
          if (broadcast) {
            getMap().broadcastMessage(this, PetPacket.showPet(this, pet, false, false), true);
            final List<Pair<MapleStat, Integer>> stats = new ArrayList<Pair<MapleStat, Integer>>(1);
            stats.add(new Pair<MapleStat, Integer>(MapleStat.PET, Integer.valueOf(pet.getUniqueId())));
            client.sendPacket(PetPacket.petStatUpdate(this));
          }

        }
        break;
      }
    }
    client.getSession().write(PetPacket.emptyStatUpdate());
  }

  public void addMoveMob(int mobid) {
    if (movedMobs.containsKey(mobid)) {
      movedMobs.put(mobid, movedMobs.get(mobid) + 1);
      if (movedMobs.get(mobid) > 30) { // trying to move not null monster
        // = broadcast dead
        for (MapleCharacter chr : getMap().getCharactersThreadsafe()) { // also
          // broadcast
          // to
          // others
          if (chr.getMoveMobs().containsKey(mobid)) { // they also
            // tried to move
            // this mob
            chr.getClient().getSession().write(MobPacket.killMonster(mobid, 1));
            chr.getMoveMobs().remove(mobid);
          }
        }
      }
    } else {
      movedMobs.put(mobid, 1);
    }
  }

  public Map<Integer, Integer> getMoveMobs() {
    return movedMobs;
  }

  public void clearLinkMid() {
    linkMobs.clear();
    cancelEffectFromBuffStat(MapleBuffStat.HOMING_BEACON);
    // cancelEffectFromBuffStat(client.MapleBuffStat.ARCANE_AIM);
  }

  public int getFirstLinkMid() {
    for (Integer lm : linkMobs.keySet()) {
      return lm.intValue();
    }
    return 0;
  }

  public Map<Integer, Integer> getAllLinkMid() {
    return linkMobs;
  }

  public void setLinkMid(int lm, int x) {
    linkMobs.put(lm, x);
  }

  public int getDamageIncrease(int lm) {
    if (linkMobs.containsKey(lm)) {
      return linkMobs.get(lm);
    }
    return 0;
  }

  public void setDragon(MapleDragon d) {
    this.dragon = d;
  }

  public final void spawnSavedPets() {
    for (int i = 0; i < petStore.length; i++) {
      if (petStore[i] > -1) {
        spawnPet(petStore[i], false, true);
      }
    }
    client.getSession().write(PetPacket.petStatUpdate(this));
    petStore = new byte[] {-1, -1, -1};
  }

  public final byte[] getPetStores() {
    return petStore;
  }

  public void resetStats(final int str, final int dex, final int int_, final int luk) {
    List<Pair<MapleStat, Integer>> stat = new ArrayList<Pair<MapleStat, Integer>>(2);
    int total = stats.getStr() + stats.getDex() + stats.getLuk() + stats.getInt() + getRemainingAp();

    total -= str;
    stats.setStr((short) str);

    total -= dex;
    stats.setDex((short) dex);

    total -= int_;
    stats.setInt((short) int_);

    total -= luk;
    stats.setLuk((short) luk);

    setRemainingAp(total);

    stat.add(new Pair<MapleStat, Integer>(MapleStat.STR, str));
    stat.add(new Pair<MapleStat, Integer>(MapleStat.DEX, dex));
    stat.add(new Pair<MapleStat, Integer>(MapleStat.INT, int_));
    stat.add(new Pair<MapleStat, Integer>(MapleStat.LUK, luk));
    stat.add(new Pair<MapleStat, Integer>(MapleStat.AVAILABLEAP, Math.min(199, total)));
    client.getSession().write(MaplePacketCreator.updatePlayerStats(stat, false, getJob()));
  }

  public Event_PyramidSubway getPyramidSubway() {
    return pyramidSubway;
  }

  public void setPyramidSubway(Event_PyramidSubway ps) {
    this.pyramidSubway = ps;
  }

  public byte getSubCategoryField() {
    return this.subcategory;
  }

  public int itemQuantity(final int itemid) {
    return getInventory(GameConstants.getInventoryType(itemid)).countById(itemid);
  }

  public void setRPS(RockPaperScissors rps) {
    this.rps = rps;
  }

  public RockPaperScissors getRPS() {
    return rps;
  }

  public long getNextConsume() {
    return nextConsume;
  }

  public void setNextConsume(long nc) {
    this.nextConsume = nc;
  }

  public int getRank() {
    return rank;
  }

  public int getRankMove() {
    return rankMove;
  }

  public int getJobRank() {
    return jobRank;
  }

  public int getJobRankMove() {
    return jobRankMove;
  }

  public void changeChannel(final int channel) {
    final ChannelServer toch = ChannelServer.getInstance(channel);

    if (channel == client.getChannel() || toch == null || toch.isShutdown()) {
      client.getSession().write(MaplePacketCreator.serverBlocked(1));
      return;
    }
    changeRemoval();

    final ChannelServer ch = ChannelServer.getInstance(client.getChannel());
    if (getMessenger() != null) {
      World.Messenger.silentLeaveMessenger(getMessenger().getId(), new MapleMessengerCharacter(this));
    }
    PlayerBuffStorage.addBuffsToStorage(getId(), getAllBuffs());
    PlayerBuffStorage.addCooldownsToStorage(getId(), getCooldowns());
    PlayerBuffStorage.addDiseaseToStorage(getId(), getAllDiseases());
    World.ChannelChange_Data(new CharacterTransfer(this), getId(), channel);
    ch.removePlayer(this);
    client.updateLoginState(MapleClient.CHANGE_CHANNEL, client.getSessionIPAddress());
    client.getSession().write(MaplePacketCreator.getChannelChange(Integer.parseInt(toch.getIP().split(":")[1])));
    getMap().removePlayer(this);
    saveToDB(false, false);

    client.setPlayer(null);
    client.setReceiving(false);
  }

  public void expandInventory(byte type, int amount) {
    final MapleInventory inv = getInventory(MapleInventoryType.getByType(type));
    inv.addSlot((byte) amount);
    client.getSession().write(MaplePacketCreator.getSlotUpdate(type, (byte) inv.getSlotLimit()));
  }

  public boolean allowedToTarget(MapleCharacter other) {
    return other != null && (!other.isHidden() || getGMLevel() >= other.getGMLevel());
  }

  public int getFollowId() {
    return followid;
  }

  public void setFollowId(int fi) {
    this.followid = fi;
    if (fi == 0) {
      this.followinitiator = false;
      this.followon = false;
    }
  }

  public void setFollowInitiator(boolean fi) {
    this.followinitiator = fi;
  }

  public void setFollowOn(boolean fi) {
    this.followon = fi;
  }

  public boolean isFollowOn() {
    return followon;
  }

  public boolean isFollowInitiator() {
    return followinitiator;
  }

  public void checkFollow() {
    if (followid <= 0) {
      return;
    }
    if (followon) {
      map.broadcastMessage(MaplePacketCreator.followEffect(id, 0, null));
      map.broadcastMessage(MaplePacketCreator.followEffect(followid, 0, null));
    }
    MapleCharacter tt = map.getCharacterById(followid);
    client.getSession().write(MaplePacketCreator.getFollowMessage("Follow canceled."));
    if (tt != null) {
      tt.setFollowId(0);
      tt.getClient().getSession().write(MaplePacketCreator.getFollowMessage("Follow canceled."));
    }
    setFollowId(0);
  }

  public int getMarriageId() {
    return marriageId;
  }

  public void setMarriageId(final int mi) {
    this.marriageId = mi;
  }

  public int getMarriageItemId() {
    return marriageItemId;
  }

  public void setMarriageItemId(final int mi) {
    this.marriageItemId = mi;
  }

  public boolean isStaff() {
    return this.gmLevel > ServerConstants.PlayerGMRank.NORMAL.getLevel();
  }

  // TODO: gvup, vic, lose, draw, VR
  public boolean startPartyQuest(final int questid) {
    boolean ret = false;
    if (!quests.containsKey(MapleQuest.getInstance(questid)) || !questinfo.containsKey(questid)) {
      final MapleQuestStatus status = getQuestNAdd(MapleQuest.getInstance(questid));
      status.setStatus((byte) 1);
      updateQuest(status);
      switch (questid) {
        case 1300:
        case 1301:
        case 1302: // carnival, ariants.
          updateInfoQuest(questid,
                  "min=0;sec=0;date=0000-00-00;have=0;rank=F;try=0;cmp=0;CR=0;VR=0;gvup=0;vic=0;lose=0;draw=0");
          break;
        case 1204: // herb town pq
          updateInfoQuest(questid,
                  "min=0;sec=0;date=0000-00-00;have0=0;have1=0;have2=0;have3=0;rank=F;try=0;cmp=0;CR=0;VR=0");
          break;
        case 1206: // ellin pq
          updateInfoQuest(questid, "min=0;sec=0;date=0000-00-00;have0=0;have1=0;rank=F;try=0;cmp=0;CR=0;VR=0");
          break;
        default:
          updateInfoQuest(questid, "min=0;sec=0;date=0000-00-00;have=0;rank=F;try=0;cmp=0;CR=0;VR=0");
          break;
      }
      ret = true;
    } // started the quest.
    return ret;
  }

  public String getOneInfo(final int questid, final String key) {
    if (!questinfo.containsKey(questid) || key == null) {
      return null;
    }
    final String[] split = questinfo.get(questid).split(";");
    for (String x : split) {
      final String[] split2 = x.split("="); // should be only 2
      if (split2.length == 2 && split2[0].equals(key)) {
        return split2[1];
      }
    }
    return null;
  }

  public void updateOneInfo(final int questid, final String key, final String value) {
    if (!questinfo.containsKey(questid) || key == null || value == null) {
      return;
    }
    final String[] split = questinfo.get(questid).split(";");
    boolean changed = false;
    final StringBuilder newQuest = new StringBuilder();
    for (String x : split) {
      final String[] split2 = x.split("="); // should be only 2
      if (split2.length != 2) {
        continue;
      }
      if (split2[0].equals(key)) {
        newQuest.append(key).append("=").append(value);
      } else {
        newQuest.append(x);
      }
      newQuest.append(";");
      changed = true;
    }

    updateInfoQuest(questid,
            changed ? newQuest.toString().substring(0, newQuest.toString().length() - 1) : newQuest.toString());
  }

  public void recalcPartyQuestRank(final int questid) {
    if (!startPartyQuest(questid)) {
      final String oldRank = getOneInfo(questid, "rank");
      if (oldRank == null || oldRank.equals("S")) {
        return;
      }
      final String[] split = questinfo.get(questid).split(";");
      String newRank = null;
      if (oldRank.equals("A")) {
        newRank = "S";
      } else if (oldRank.equals("B")) {
        newRank = "A";
      } else if (oldRank.equals("C")) {
        newRank = "B";
      } else if (oldRank.equals("D")) {
        newRank = "C";
      } else if (oldRank.equals("F")) {
        newRank = "D";
      } else {
        return;
      }
      final List<Pair<String, Pair<String, Integer>>> questInfo = MapleQuest.getInstance(questid)
              .getInfoByRank(newRank);
      for (Pair<String, Pair<String, Integer>> q : questInfo) {
        boolean found = false;
        final String val = getOneInfo(questid, q.right.left);
        if (val == null) {
          return;
        }
        int vall = 0;
        try {
          vall = Integer.parseInt(val);
        } catch (NumberFormatException e) {
          return;
        }
        if (q.left.equals("less")) {
          found = vall < q.right.right;
        } else if (q.left.equals("more")) {
          found = vall > q.right.right;
        } else if (q.left.equals("equal")) {
          found = vall == q.right.right;
        }
        if (!found) {
          return;
        }
      }
      // perfectly safe
      updateOneInfo(questid, "rank", newRank);
    }
  }

  public void tryPartyQuest(final int questid) {
    try {
      startPartyQuest(questid);
      pqStartTime = System.currentTimeMillis();
      updateOneInfo(questid, "try", String.valueOf(Integer.parseInt(getOneInfo(questid, "try")) + 1));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("tryPartyQuest error");
    }
  }

  public void endPartyQuest(final int questid) {
    try {
      startPartyQuest(questid);
      if (pqStartTime > 0) {
        final long changeTime = System.currentTimeMillis() - pqStartTime;
        final int mins = (int) (changeTime / 1000 / 60), secs = (int) (changeTime / 1000 % 60);
        final int mins2 = Integer.parseInt(getOneInfo(questid, "min")),
                secs2 = Integer.parseInt(getOneInfo(questid, "sec"));
        if (mins2 <= 0 || mins < mins2) {
          updateOneInfo(questid, "min", String.valueOf(mins));
          updateOneInfo(questid, "sec", String.valueOf(secs));
          updateOneInfo(questid, "date", FileoutputUtil.CurrentReadable_Date());
        }
        final int newCmp = Integer.parseInt(getOneInfo(questid, "cmp")) + 1;
        updateOneInfo(questid, "cmp", String.valueOf(newCmp));
        updateOneInfo(questid, "CR", String
                .valueOf((int) Math.ceil((newCmp * 100.0) / Integer.parseInt(getOneInfo(questid, "try")))));
        recalcPartyQuestRank(questid);
        pqStartTime = 0;
      }
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("endPartyQuest error");
    }

  }

  public void havePartyQuest(final int itemId) {
    int questid = 0, index = -1;
    switch (itemId) {
      case 1002798:
        questid = 1200; // henesys
        break;
      case 1072369:
        questid = 1201; // kerning
        break;
      case 1022073:
        questid = 1202; // ludi
        break;
      case 1082232:
        questid = 1203; // orbis
        break;
      case 1002571:
      case 1002572:
      case 1002573:
      case 1002574:
        questid = 1204; // herbtown
        index = itemId - 1002571;
        break;
      case 1122010:
        questid = 1205; // magatia
        break;
      case 1032061:
      case 1032060:
        questid = 1206; // ellin
        index = itemId - 1032060;
        break;
      case 3010018:
        questid = 1300; // ariant
        break;
      case 1122007:
        questid = 1301; // carnival
        break;
      case 1122058:
        questid = 1302; // carnival2
        break;
      default:
        return;
    }
    startPartyQuest(questid);
    updateOneInfo(questid, "have" + (index == -1 ? "" : index), "1");
  }

  public void resetStatsByJob(boolean beginnerJob) {
    int baseJob = (beginnerJob ? (job % 1000) : (job % 1000 / 100 * 100)); // 1112
    // ->
    // 112
    // ->
    // 1
    // ->
    // 100
    if (baseJob == 100) { // first job = warrior
      resetStats(25, 4, 4, 4);
    } else if (baseJob == 200) {
      resetStats(4, 4, 20, 4);
    } else if (baseJob == 300 || baseJob == 400) {
      resetStats(4, 25, 4, 4);
    } else if (baseJob == 500) {
      resetStats(4, 20, 4, 4);
    }
  }

  public boolean hasSummon() {
    return hasSummon;
  }

  public void setHasSummon(boolean summ) {
    this.hasSummon = summ;
  }

  public void removeDoor() {
    final MapleDoor door = getDoors().iterator().next();
    for (final MapleCharacter chr : door.getTarget().getCharactersThreadsafe()) {
      door.sendDestroyData(chr.getClient());
    }
    for (final MapleCharacter chr : door.getTown().getCharactersThreadsafe()) {
      door.sendDestroyData(chr.getClient());
    }
    for (final MapleDoor destroyDoor : getDoors()) {
      door.getTarget().removeMapObject(destroyDoor);
      door.getTown().removeMapObject(destroyDoor);
    }
    clearDoors();
  }

  public void changeRemoval() {
    changeRemoval(false);
  }

  public void changeRemoval(boolean dc) {
    if (getTrade() != null) {
      MapleTrade.cancelTrade(getTrade(), client);
    }
    if (getCheatTracker() != null) {
      getCheatTracker().dispose();
    }
    if (!dc) {
      cancelEffectFromBuffStat(MapleBuffStat.MONSTER_RIDING);
      cancelEffectFromBuffStat(MapleBuffStat.SUMMON);
      cancelEffectFromBuffStat(MapleBuffStat.REAPER);
      cancelEffectFromBuffStat(MapleBuffStat.PUPPET);
    }
    if (getPyramidSubway() != null) {
      getPyramidSubway().dispose(this);
    }
    if (playerShop != null && !dc) {
      playerShop.removeVisitor(this);
      if (playerShop.isOwner(this)) {
        playerShop.setOpen(true);
      }
    }
    if (!getDoors().isEmpty()) {
      removeDoor();
    }
    NPCScriptManager.getInstance().dispose(client);
  }

  public void updateTick(int newTick) {
    anticheat.updateTick(newTick);
  }

  public String getTeleportName() {
    return teleportname;
  }

  public void setTeleportName(final String tname) {
    teleportname = tname;
  }

  public int maxBattleshipHP(int skillid) {
    return (getSkillLevel(skillid) * 5000) + ((getLevel() - 120) * 3000);
  }

  public int currentBattleshipHP() {
    return battleshipHP;
  }

  public long getLoginTime() {
    return loginTime;
  }

  public void setLoginTime(long login) {
    this.loginTime = login;
  }

  public final boolean canRecover(long now) {
    return lastRecoveryTime > 0 && lastRecoveryTime + 5000 < now;
  }

  private void prepareRecovery() {
    lastRecoveryTime = System.currentTimeMillis();
  }

  public void doRecovery() {
    MapleStatEffect bloodEffect = getStatForBuff(MapleBuffStat.RECOVERY);
    if (bloodEffect == null) {
      lastRecoveryTime = 0;
    } else {
      prepareRecovery();
      if (stats.getHp() >= stats.getCurrentMaxHp()) {
        cancelEffectFromBuffStat(MapleBuffStat.RECOVERY);
      } else {
        healHP(bloodEffect.getX());
      }
    }
  }

  public final boolean canBlood(long now) {
    return lastDragonBloodTime > 0 && (lastDragonBloodTime + 4000 < now);
  }

  private void prepareDragonBlood() {
    lastDragonBloodTime = System.currentTimeMillis();
  }

  public void doDragonBlood() {
    MapleStatEffect bloodEffect = getStatForBuff(MapleBuffStat.DRAGONBLOOD);
    if (bloodEffect == null) {
      lastDragonBloodTime = 0;
      return;
    }
    prepareDragonBlood();
    if (stats.getHp() - bloodEffect.getX() <= 1) {
      cancelBuffStats(MapleBuffStat.DRAGONBLOOD);
    } else {
      addHP(-bloodEffect.getX());
      client.getSession().write(MaplePacketCreator.showOwnBuffEffect(bloodEffect.getSourceId(), 5));
      map.broadcastMessage(MapleCharacter.this,
              MaplePacketCreator.showBuffeffect(getId(), bloodEffect.getSourceId(), 5), false);
    }
  }

  public final boolean canHP(long now) {
    if (lastHPTime + 5000 < now) {
      lastHPTime = now;
      return true;
    }
    return false;
  }

  public final boolean canMP(long now) {
    if (lastMPTime + 5000 < now) {
      lastMPTime = now;
      return true;
    }
    return false;
  }

  public List<Integer> getPetItemIgnore(final MaplePet pet) {
    List<Integer> ret = new ArrayList<>(10);
    return ret;
  }

  public void updatePetAuto() {
    String petHp = get("PET_HP");
    String petMp = get("PET_MP");
    if (petHp != null && !petHp.isEmpty()) {
      client.getSession().write(PetPacket.petAutoHP(Integer.parseInt(petHp)));
    }
    if (petMp != null && !petMp.isEmpty()) {
      client.getSession().write(PetPacket.petAutoMP(Integer.parseInt(petMp)));
    }
  }

  public final boolean canFairy(long now) {
    return lastFairyTime > 0 && (lastFairyTime + 3600000 < now);
  }

  public void doFairy() {
    if (stats.equippedFairy && fairyExp < 30) {
      fairyExp += 10;
      lastFairyTime = System.currentTimeMillis();
      client.getSession().write(MaplePacketCreator.fairyPendantMessage(fairyExp));
      if (fairyExp == 30) {
        cancelFairySchedule(false); // Don't reset exp, just leave as
        // max
      }
    } else if (!stats.equippedFairy) { // Not equipped
      cancelFairySchedule(true); // Reset exp
    } else { // at Max for fairyExp
      cancelFairySchedule(false); // we don't reset it.
    }
  }

  // wear-1hr = 10%, 1hr-2hr already = 20%, 2 hrs + = 30%
  public void startFairySchedule() {
    cancelFairySchedule(true); // Reset exp
    if (stats.equippedFairy) { // Used for login
      lastFairyTime = System.currentTimeMillis();
      client.getSession().write(MaplePacketCreator.fairyPendantMessage(fairyExp));
    }
  }

  public void cancelFairySchedule(boolean onStart) {
    lastFairyTime = 0;
    if (onStart) {
      fairyExp = 10;
    }
  }

  public byte getFairyExp() {
    return fairyExp;
  }

  public long getLastRecoveryTime() {
    return lastRecoveryTime;
  }

  public long getLastDragonBloodTime() {
    return lastDragonBloodTime;
  }

  public long getLastBerserkTime() {
    return lastBerserkTime;
  }

  public long getLastHPTime() {
    return lastHPTime;
  }

  public long getLastMPTime() {
    return lastMPTime;
  }

  public long getLastFairyTime() {
    return lastFairyTime;
  }

  public Map<ReportType, Integer> getReports() {
    return reports;
  }

  public void addReport(ReportType type) {
    Integer value = reports.get(type);
    reports.put(type, (value == null ? 1 : (value + 1)));
    changed_reports = true;
  }

  public void clearReports(ReportType type) {
    reports.remove(type);
    changed_reports = true;
  }

  public void clearReports() {
    reports.clear();
    changed_reports = true;
  }

  public final int getReportPoints() {
    int ret = 0;
    for (Integer entry : reports.values()) {
      ret += entry.intValue();
    }
    return ret;
  }

  public final String getReportSummary() {
    StringBuilder ret = new StringBuilder();
    final List<Pair<ReportType, Integer>> offenseList = new ArrayList<>();
    for (Map.Entry<ReportType, Integer> entry : reports.entrySet()) {
      offenseList.add(new Pair<>(entry.getKey(), entry.getValue()));
    }
    Collections.sort(offenseList, new Comparator<Pair<ReportType, Integer>>() {

      public final int compare(Pair<ReportType, Integer> o1, Pair<ReportType, Integer> o2) {
        int thisVal = o1.getRight();
        int anotherVal = o2.getRight();
        return thisVal == anotherVal ? 0 : thisVal < anotherVal ? 1 : -1;
      }
    });
    for (int x = 0; x < offenseList.size(); x++) {
      ret.append(StringUtil.makeEnumHumanReadable(offenseList.get(x).left.name()));
      ret.append(": ");
      ret.append(offenseList.get(x).right);
      ret.append(" ");
    }
    return ret.toString();
  }

  public void sendSkills() {
    if (GameConstants.isEvan(getJob()) || getJob() == 900 || getJob() == 910) {
      client.getSession().write(MaplePacketCreator.updateSkill(this.getSkills()));
    }
  }

  private void sendAutoSkills() {
    client.getSession().write(MaplePacketCreator.updateSkill(this.getSkills()));
  }

  public boolean isSkillBelongToJob(final int skillid) {
    if (isGM()) {
      return true;
    }

    if (JobConstants.isFixedSkill(skillid)) {
      if (skillid >= 10000000 && skillid < 20000000) { // koc skills
        if ((skillid / 10000) <= getJob()) {
          if (!GameConstants.isJobFamily((skillid / 10000), getJob())) {
            return false;
          }
        }
      } else if (skillid >= 10000000 && skillid < 30000000) {
        if (GameConstants.isEvan((skillid / 10000))) {
          if ((skillid / 10000) <= getJob()) {
            if (!GameConstants.isJobFamily((skillid / 10000), getJob())) {
              return false;
            }
          }
        } else if (GameConstants.isAran((skillid / 10000))) {
          if ((skillid / 10000) <= getJob()) {
            if (!GameConstants.isJobFamily((skillid / 10000), getJob())) {
              return false;
            }
          }
        } else {
          return false;
        }
      } else { // All explorer skills
        if (skillid >= 1000000) {
          if (!GameConstants.isJobFamily((skillid / 10000), getJob())) {
            return false;
          } else {
            return true;
          }
        }
      }
    }
    return true;
  }

  public void resetKeyMap() {

  }

  public void removeAllKey(final List<Integer> x) {
    for (Integer i : x) {
      keylayout.Layout().remove(i);
    }
  }

  public Collection<Triple<Byte, Integer, Byte>> getKeymap() {
    return keylayout.Layout().values();
  }

  public void wipeSkillsWithException(int skill) {
    int NextKey = 0;
    Iterator<Entry<Integer, Triple<Byte, Integer, Byte>>> i = keylayout.Layout().entrySet().iterator();
    while (i.hasNext()) {
      Entry<Integer, Triple<Byte, Integer, Byte>> o = i.next();
      final int oid = o.getValue().getMid();
      if (o.getValue().getLeft() == 1 && oid >= 1000) {
        if (oid == skill && skill != -1) {
          NextKey = o.getKey();
        } else if (JobConstants.isEvanSkill(oid)
                || oid == 1004 || oid == 10001004 || oid == 20001004 || oid == 20011004
                || (o.getValue().getRight() <= 0 && GameConstants.getMountItem(oid) == 0)
                || (o.getValue().getRight() <= 0 && oid == 5221006)
                || (((oid / 10000 == 910) || (oid / 10000 == 900)) && !isGM())) {
          i.remove();
        }
      }
    }
    if (NextKey != 0) {
      changeKeybinding(NextKey, (byte) 1, skill, (byte) 1);
    }

    sendSkills();
    client.getSession().write(MaplePacketCreator.getKeymap(getKeyLayout()));
  }

  public void setSpeedQuiz(SpeedQuiz sq) {
    this.sq = sq;
  }

  public SpeedQuiz getSpeedQuiz() {
    return sq;
  }

  public byte getPortalCount(boolean add) {
    if (add) {
      if (this.portalCount >= Byte.MAX_VALUE) {
        this.portalCount = 1; // Reset back to 1
      } else {
        this.portalCount++;
      }
    }
    return portalCount;
  }

  public byte getMorphId() {
    return morphId;
  }

  public void setMorphId(byte id) {
    this.morphId = id;
  }

  public void reloadChar() {
    this.getClient().getSession().write(MaplePacketCreator.getCharInfo(this));
    this.getMap().removePlayer(this);
    this.getMap().addPlayer(this);
  }

  public int getTotalWatk() {
    return watk;
  }

  public void setstat(byte stat, short newval) {
    switch (stat) {
      case 1:
        stats.setStr(newval);
        break;
      case 2:
        stats.setDex(newval);
        break;
      case 3:
        stats.setInt(newval);
        break;
      case 4:
        stats.setLuk(newval);
        break;
    }
  }

  public void changeMap(int map) {
    changeMap(map, 0);
  }

  public void changeMap(int map, int portal) {
    MapleMap warpMap = client.getChannelServer().getMapFactory().getMap(map);
    changeMap(warpMap, warpMap.getPortal(portal));
  }

  public void changeMap(int map, String portal) {
    MapleMap warpMap = client.getChannelServer().getMapFactory().getMap(map);
    changeMap(warpMap, warpMap.getPortal(portal));
  }

  public void changeMap(int map, MaplePortal portal) {
    MapleMap warpMap = client.getChannelServer().getMapFactory().getMap(map);
    changeMap(warpMap, portal);
  }

  public void changeMap(MapleMap to) {
    changeMap(to, to.getPortal(0));
  }

  public void changeMapScripting(MapleMap to) {
    changeMap(to, to.getPortal(0));
  }

  public void dcolormsg(int color, String message) {
    client.getSession().write(MaplePacketCreator.getGameMessage(color, message));
  }

  public void expfix(int newval) {
    setExp(newval);
    updateSingleStat(MapleStat.EXP, getExp());
  }

  public void changeChannel() {
    if (!isAlive() || getEventInstance() != null || FieldLimitType.ChannelSwitch.check(getMap().getFieldLimit())) {
      dropMessage(1, "Auto change channel failed.");
      return;
    }
    changeChannel(this.getClient().getChannel() == ChannelServer.getChannelCount() ? 1
            : (this.getClient().getChannel() + 1));
  }

  public EvanSkillPoints getEvanSP() {
    return this.evanSP;
  }

  public void setRemainingSp(int remainingSp) {
    this.remainingSp = remainingSp;

  }

  public int getRemainingSp() {
    return remainingSp;
  }

  public boolean isEvan() {
    return (getJob() == 2001 || getJob() / 100 == 22);
  }

  private static void loadEvanSkills(MapleCharacter ret) {
    EvanSkillPoints sp = new EvanSkillPoints();
    ResultSet rs = null;
    PreparedStatement ps = null;
    Connection con = DatabaseConnection.getConnection();
    try {
      ps = con.prepareStatement("SELECT * FROM evan_skillpoints WHERE characterid = ?");
      ps.setInt(1, ret.id);
      rs = ps.executeQuery();
      if (rs.next()) {
        sp.setSkillPoints(MapleJob.EVAN2.getId(), rs.getInt("Evan1"));
        sp.setSkillPoints(MapleJob.EVAN3.getId(), rs.getInt("Evan2"));
        sp.setSkillPoints(MapleJob.EVAN4.getId(), rs.getInt("Evan3"));
        sp.setSkillPoints(MapleJob.EVAN5.getId(), rs.getInt("Evan4"));
        sp.setSkillPoints(MapleJob.EVAN6.getId(), rs.getInt("Evan5"));
        sp.setSkillPoints(MapleJob.EVAN7.getId(), rs.getInt("Evan6"));
        sp.setSkillPoints(MapleJob.EVAN8.getId(), rs.getInt("Evan7"));
        sp.setSkillPoints(MapleJob.EVAN9.getId(), rs.getInt("Evan8"));
        sp.setSkillPoints(MapleJob.EVAN10.getId(), rs.getInt("Evan9"));
        sp.setSkillPoints(MapleJob.EVAN11.getId(), rs.getInt("Evan10"));
      }
      ret.evanSP = sp;
    } catch (SQLException e) {
      e.printStackTrace();
    }

  }

  public boolean isAran() {
    return (this.getJob() / 100 == 21) || (this.getJob() == 2000);
  }

  public int getJobType() {
    return this.getJob() / 1000;
  }

  public boolean isCygnus() {
    return getJobType() == 1;
  }

  public boolean isDualblade() {
    return getJob() >= 430 && getJob() <= 434;
  }

  public final void maxMastery() {
    for (ISkill skill_ : SkillFactory.getAllSkills()) {
      try {
        int skillid = skill_.getId();
        if ((skillid % 10000000 >= 1000000) && ((skillid >= 9000000) && (skillid <= 10000000))) {
          continue;
        }
        ISkill skill = SkillFactory.getSkill(skillid);
        boolean add = ((skillid / 10000000 == this.getJob() / 1000) && (skill.hasMastery())) || (isCygnus());
        if ((!add) && (isAran())) {
          switch (skillid) {
            case 21000000:
            case 21001003:
            case 21100000:
            case 21100002:
            case 21100004:
            case 21100005:
            case 21110002:
              add = true;
          }

        }
        if (add) {
          int masterLevel = skill.getMasterLevel();
          if (masterLevel == 0) {
            continue;
          }
          changeSkillLevel(skill, getSkillLevel(skill), (byte) masterLevel);
        }
      } catch (NumberFormatException nfe) {
        continue;
      } catch (NullPointerException npe) {
        continue;
      }
    }
  }

  public void checkForDarkSight() {
    if (isActiveBuffedValue(Rogue.DARK_SIGHT)) {
      int incresePercent = 10 + getSkillLevel(BladeLord.ADVANCED_DARK_SIGHT) * 2;
      int randomNumber = new Random().nextInt(100) + 1;
      if (isDualblade()) {
        if (incresePercent < randomNumber) {
          cancelBuffStats(MapleBuffStat.DARKSIGHT);
        }
      } else {
        cancelBuffStats(MapleBuffStat.DARKSIGHT);
      }
    }
  }

  public boolean isGameMasterJob() {
    return this.getJobValue() == MapleJob.GM || this.getJobValue() == MapleJob.SUPERGM;
  }

  public int getJobCategoryForEquips() {
    if (isEvan()) {
      return 2;
    }
    if (isDualblade()) {
      return 4;
    }
    if (isAran()) {
      return 1;
    }
    return this.job / 100;
  }

  public void addTemporaryData(String key, Object value) {
    temporaryData.put(key, value);
  }

  public Object getTemporaryData(String key) {
    return temporaryData.get(key);
  }

  public Object removeTemporaryData(String key) {
    return temporaryData.remove(key);
  }

  public void clearTemporaryData() {
    temporaryData.clear();
  }

  public int getPossibleReports() {
    return 1;
  }

  public void set(String key, String value) {
    MapleVar var = new SimpleMapleVar(this);
    var.set(key, value);
  }

  public String get(String key) {
    MapleVar var = new SimpleMapleVar(this);
    return var.get(key);
  }

  public boolean isRideFinished() {
    boolean ret = travelTime < System.currentTimeMillis();
    return ret;
  }

  public void setTravelTime(int duration) {
    travelTime = System.currentTimeMillis() + (duration * 1000);
  }

  public ChannelServer getChannelServer() {
    return ChannelServer.getInstance(client.getChannel());
  }

  @Override
  public String toString() {
    return name + " at " + getPosition() + " in map: " + map.getId();
  }

  public void setNewEventInstance(EventInstance instance) {
    this.newEventInstance = instance;
  }

  public EventInstance getNewEventInstance() {
    return this.newEventInstance;
  }

  public Messages getMessages() {
    return new Messages(client);
  }

}