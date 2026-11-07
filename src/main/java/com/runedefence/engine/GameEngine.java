package com.runedefence.engine;

import com.runedefence.model.Content;
import com.runedefence.model.DamageType;
import com.runedefence.model.DefenderDefinition;
import com.runedefence.model.Effects;
import com.runedefence.model.EnemyDefinition;
import com.runedefence.model.MapDefinition;
import com.runedefence.model.MatchSave;
import com.runedefence.model.UpgradeDefinition;
import com.runedefence.model.WaveDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The whole game simulation, with no RuneLite or Swing dependencies. It is not
 * thread-safe and is owned by one thread (the Swing EDT in the plugin), which
 * both steps and paints it, so nothing can be mutated while it is being drawn.
 *
 * Given the same seed, content and commands, a match always plays out the same.
 */
public final class GameEngine
{
	public static final double STEP_SECONDS = 0.05;
	public static final int RESALE_PERCENT = 70;

	private static final double ATTACK_ANIM_SECONDS = 0.25;
	private static final double HIT_ANIM_SECONDS = 0.1;
	public static final double ARMOUR_BREAK_SECONDS = 4;
	public static final double DEFAULT_CRIT_MULTIPLIER = 2;
	/**
	 * Measured from the moment a bind lands, as a multiple of how long it holds. At 2, an
	 * enemy walks for at least as long as it was held, so it can't be frozen more than
	 * half the time, however many binders cover it.
	 */
	private static final double BIND_LOCKOUT_RATIO = 2;
	private static final int FX_CAPACITY = 64;
	private static final int MAX_PROJECTILES = 200;
	private static final double MAX_SLOW = 0.8;
	private static final double MAX_RESIST = 0.9;
	private static final double SPLIT_SPACING = 0.25;

	public enum State
	{
		MENU,
		PREPARING,
		RUNNING,
		PAUSED,
		WAVE_COMPLETE,
		VICTORY,
		DEFEAT
	}

	public enum Result
	{
		OK,
		WRONG_STATE,
		LOCKED,
		NOT_ENOUGH_GOLD,
		INVALID_TILE,
		NOT_FOUND,
		PATH_MAXED,
		PATH_BLOCKED
	}

	public interface MatchEndListener
	{
		void onMatchEnd(GameEngine engine, boolean victory);
	}

	private final Content content;
	private final List<DefenderInstance> defenders = new ArrayList<>();
	private final List<EnemyInstance> enemies = new ArrayList<>();
	private final List<Projectile> projectiles = new ArrayList<>();
	/** Killed this step and due to burst, which waits so that a blast can't hit what it just created. */
	private final List<EnemyInstance> splitting = new ArrayList<>();

	private final FxEvent[] fx = new FxEvent[FX_CAPACITY];
	private int fxHead;
	private int fxSize;

	private State state = State.MENU;
	private State resumeState = State.MENU;
	private MapDefinition map;
	private PathTable[] lanes;
	private Random rng = new Random(0);
	private long seed;

	private int playerLevel = 1;
	private double autoStartDelay;
	private double autoStartTimer;

	private int gold;
	private int baseHealth;
	private int waveIndex;
	private int goldSpent;
	private int enemiesDefeated;
	private long ticks;

	private double waveTime;
	private int[] spawned = new int[0];
	private MatchEndListener matchEndListener;
	private MatchSave checkpoint;

	public GameEngine(Content content)
	{
		this.content = content;
	}

	// ---- Match lifecycle ----------------------------------------------------

	public Result newMatch(String mapId, long matchSeed)
	{
		MapDefinition m = content.map(mapId);
		if (m == null)
		{
			return Result.NOT_FOUND;
		}
		if (playerLevel < m.requiredLevel)
		{
			return Result.LOCKED;
		}
		loadMap(m, matchSeed);
		gold = m.startingGold;
		baseHealth = m.startingHealth;
		waveIndex = 0;
		goldSpent = 0;
		enemiesDefeated = 0;
		ticks = 0;
		state = State.PREPARING;
		checkpoint = null;
		return Result.OK;
	}

	private void loadMap(MapDefinition m, long matchSeed)
	{
		map = m;
		lanes = new PathTable[m.paths.length];
		for (int i = 0; i < lanes.length; i++)
		{
			lanes[i] = new PathTable(m.paths[i]);
		}
		seed = matchSeed;
		rng = new Random(matchSeed);
		clearField();
		defenders.clear();
	}

	private void clearField()
	{
		enemies.clear();
		projectiles.clear();
		splitting.clear();
		fxSize = 0;
		waveTime = 0;
	}

	/** Leaves the match without recording it. Permanent progression is never affected. */
	public void abandon()
	{
		clearField();
		defenders.clear();
		map = null;
		lanes = null;
		checkpoint = null;
		state = State.MENU;
	}

	public Result startWave()
	{
		if (state != State.PREPARING && state != State.WAVE_COMPLETE)
		{
			return Result.WRONG_STATE;
		}
		checkpoint = buildCheckpoint();
		beginWave();
		return Result.OK;
	}

	private void beginWave()
	{
		clearField();
		spawned = new int[currentWave().groups.length];
		// Reseeding per wave lets a checkpoint resume with an identical random sequence.
		rng = new Random(seed * 31 + waveIndex);
		for (int i = 0; i < defenders.size(); i++)
		{
			DefenderInstance d = defenders.get(i);
			d.cooldown = 0;
			d.attackCount = 0;
			d.attackAnim = 0;
			d.stunTimer = 0;
		}
		state = State.RUNNING;
	}

	public Result pause()
	{
		if (state != State.RUNNING && state != State.PREPARING && state != State.WAVE_COMPLETE)
		{
			return Result.WRONG_STATE;
		}
		resumeState = state;
		state = State.PAUSED;
		return Result.OK;
	}

	public Result resume()
	{
		if (state != State.PAUSED)
		{
			return Result.WRONG_STATE;
		}
		state = resumeState;
		return Result.OK;
	}

	public boolean isMatchActive()
	{
		return state != State.MENU && state != State.VICTORY && state != State.DEFEAT;
	}

	// ---- Economy commands ---------------------------------------------------

	/** Can this defender be placed at all, regardless of where? */
	public Result canPurchase(DefenderDefinition def)
	{
		if (!isMatchActive())
		{
			return Result.WRONG_STATE;
		}
		if (playerLevel < def.requiredLevel)
		{
			return Result.LOCKED;
		}
		return gold < def.cost ? Result.NOT_ENOUGH_GOLD : Result.OK;
	}

	public boolean isTileFree(int col, int row)
	{
		return canPlaceAt(1, col, row);
	}

	/** Is a square of this size clear of scenery, path and other defenders? */
	public boolean canPlaceAt(int size, int col, int row)
	{
		if (map == null || !map.canFit(col, row, size))
		{
			return false;
		}
		for (int r = row; r < row + size; r++)
		{
			for (int c = col; c < col + size; c++)
			{
				if (defenderAt(c, r) != null)
				{
					return false;
				}
			}
		}
		return true;
	}

	public Result place(String defenderId, int col, int row)
	{
		DefenderDefinition def = content.defender(defenderId);
		if (def == null)
		{
			return Result.NOT_FOUND;
		}
		Result allowed = canPurchase(def);
		if (allowed != Result.OK)
		{
			return allowed;
		}
		if (!canPlaceAt(def.size, col, row))
		{
			return Result.INVALID_TILE;
		}
		DefenderInstance d = new DefenderInstance(def, col, row);
		d.invested = def.cost;
		spend(def.cost);
		defenders.add(d);
		recalculateAuras();
		return Result.OK;
	}

	/** The next upgrade on a path, or null if the path is complete. */
	public static UpgradeDefinition nextUpgrade(DefenderInstance d, int pathIndex)
	{
		UpgradeDefinition[] upgrades = d.def.path(pathIndex);
		int tier = d.tier(pathIndex);
		return tier < upgrades.length ? upgrades[tier] : null;
	}

	/** Paths are exclusive past tier one: only one of them can reach tier two. */
	public static boolean isPathBlocked(DefenderInstance d, int pathIndex)
	{
		return d.tier(pathIndex) >= 1 && d.tier(1 - pathIndex) >= 2;
	}

	public Result upgrade(DefenderInstance d, int pathIndex)
	{
		if (!isMatchActive())
		{
			return Result.WRONG_STATE;
		}
		if (!defenders.contains(d))
		{
			return Result.NOT_FOUND;
		}
		UpgradeDefinition next = nextUpgrade(d, pathIndex);
		if (next == null)
		{
			return Result.PATH_MAXED;
		}
		if (isPathBlocked(d, pathIndex))
		{
			return Result.PATH_BLOCKED;
		}
		if (gold < next.cost)
		{
			return Result.NOT_ENOUGH_GOLD;
		}
		spend(next.cost);
		d.invested += next.cost;
		if (pathIndex == 0)
		{
			d.tierA++;
		}
		else
		{
			d.tierB++;
		}
		d.recalculate();
		recalculateAuras();
		return Result.OK;
	}

	public static int sellValue(DefenderInstance d)
	{
		// Whole numbers only: as a double, 90 * 0.7 is 62.99999 and would short-change a sale.
		return d.invested * RESALE_PERCENT / 100;
	}

	public Result sell(DefenderInstance d)
	{
		if (!isMatchActive())
		{
			return Result.WRONG_STATE;
		}
		if (!defenders.remove(d))
		{
			return Result.NOT_FOUND;
		}
		gold += sellValue(d);
		recalculateAuras();
		return Result.OK;
	}

	public void cycleTargeting(DefenderInstance d)
	{
		d.targeting = d.targeting.next();
	}

	/** Each defender gets the single strongest aura of each kind in reach, so they never stack. */
	private void recalculateAuras()
	{
		for (int i = 0; i < defenders.size(); i++)
		{
			DefenderInstance d = defenders.get(i);
			d.auraDamage = 0;
			d.auraSpeed = 0;
			for (int j = 0; j < defenders.size(); j++)
			{
				DefenderInstance a = defenders.get(j);
				if (a != d && a.stats.auraRadius > 0 && Math.hypot(a.x - d.x, a.y - d.y) <= a.stats.auraRadius)
				{
					d.auraDamage = Math.max(d.auraDamage, a.stats.auraDamagePercent);
					d.auraSpeed = Math.max(d.auraSpeed, a.stats.auraSpeedPercent);
				}
			}
		}
	}

	/** Package-private on purpose: nothing in the plugin can create gold from nowhere. */
	void addGold(int amount)
	{
		gold += amount;
	}

	private void spend(int amount)
	{
		gold -= amount;
		goldSpent += amount;
	}

	public DefenderInstance defenderAt(int col, int row)
	{
		for (int i = 0; i < defenders.size(); i++)
		{
			DefenderInstance d = defenders.get(i);
			if (d.occupies(col, row))
			{
				return d;
			}
		}
		return null;
	}

	// ---- Simulation ---------------------------------------------------------

	/** Advances the game by one fixed {@link #STEP_SECONDS} step. */
	public void step()
	{
		if (state == State.WAVE_COMPLETE)
		{
			if (autoStartDelay > 0)
			{
				autoStartTimer -= STEP_SECONDS;
				if (autoStartTimer <= 0)
				{
					startWave();
				}
			}
			animateDefenders();
			return;
		}
		if (state != State.RUNNING)
		{
			return;
		}
		ticks++;
		waveTime += STEP_SECONDS;
		spawnEnemies();
		moveEnemies();
		if (state != State.RUNNING)
		{
			// The keep fell. Nothing else may happen, so the recorded result stays final.
			return;
		}
		attack();
		moveProjectiles();
		removeDeadEnemies();
		spawnSplits();
		if (state == State.RUNNING && isWaveFinished())
		{
			finishWave();
		}
	}

	private void spawnEnemies()
	{
		WaveDefinition.Group[] groups = currentWave().groups;
		for (int i = 0; i < groups.length; i++)
		{
			WaveDefinition.Group g = groups[i];
			while (spawned[i] < g.count && waveTime * 1000 >= g.delayMs + spawned[i] * (double) g.intervalMs)
			{
				spawn(content.enemy(g.enemy), g.health * map.enemyHealth, laneFor(spawned[i]), 0);
				spawned[i]++;
			}
		}
	}

	/** Either the one open lane, or groups taking turns between lanes so that none can be ignored. */
	private PathTable laneFor(int spawnedSoFar)
	{
		return lanes[map.rotateLanesEvery > 0 ? activeLane() : spawnedSoFar % lanes.length];
	}

	/** The lane open for the current or next wave, on a map where the route shifts. */
	public int activeLane()
	{
		return map.rotateLanesEvery > 0 ? (waveIndex / map.rotateLanesEvery) % lanes.length : -1;
	}

	private void spawn(EnemyDefinition def, double healthMultiplier, PathTable lane, double progress)
	{
		double resist = Math.min(MAX_RESIST, def.magicResist + map.extraMagicResist);
		EnemyInstance e = new EnemyInstance(def, def.maxHealth * healthMultiplier, (rng.nextDouble() - 0.5) * 0.3, lane, resist);
		e.progress = progress;
		lane.locate(progress, e);
		e.prevX = e.x;
		e.prevY = e.y;
		enemies.add(e);
	}

	private void spawnSplits()
	{
		for (int i = 0; i < splitting.size(); i++)
		{
			EnemyInstance parent = splitting.get(i);
			EnemyDefinition child = content.enemy(parent.def.splitInto);
			// Children keep the parent's wave scaling, and fan out a little along the path.
			double multiplier = parent.maxHp / parent.def.maxHealth;
			for (int n = 0; n < parent.def.splitCount; n++)
			{
				double offset = (n - (parent.def.splitCount - 1) / 2.0) * SPLIT_SPACING;
				spawn(child, multiplier, parent.path, Math.max(0, Math.min(parent.path.length - 0.01, parent.progress + offset)));
			}
		}
		splitting.clear();
	}

	private void moveEnemies()
	{
		for (int i = 0; i < enemies.size() && state == State.RUNNING; i++)
		{
			EnemyInstance e = enemies.get(i);
			e.prevX = e.x;
			e.prevY = e.y;
			if (e.hitAnim > 0)
			{
				e.hitAnim -= STEP_SECONDS;
			}
			updateStatuses(e);
			if (e.dead)
			{
				continue;
			}
			if (e.def.gazeRange > 0)
			{
				gaze(e);
			}
			if (e.rootTimer <= 0)
			{
				double slow = e.slowTimer > 0 ? e.slowPercent : 0;
				e.progress += e.def.speed * map.enemySpeed * (1 - slow) * STEP_SECONDS;
			}
			if (e.progress >= e.path.length)
			{
				escape(e);
			}
			else
			{
				e.path.locate(e.progress, e);
				if (e.x != e.prevX)
				{
					e.facingLeft = e.x < e.prevX;
				}
			}
		}
	}

	private void updateStatuses(EnemyInstance e)
	{
		if (e.armourBreakTimer > 0)
		{
			e.armourBreakTimer -= STEP_SECONDS;
			if (e.armourBreakTimer <= 0)
			{
				e.armourBroken = 0;
			}
		}
		e.slowTimer -= STEP_SECONDS;
		e.rootTimer -= STEP_SECONDS;
		e.bindLockout -= STEP_SECONDS;
		e.weakenTimer -= STEP_SECONDS;
		if (e.def.regenPerSecond > 0 && e.hp < e.maxHp)
		{
			e.hp = Math.min(e.maxHp, e.hp + e.def.regenPerSecond * STEP_SECONDS);
		}
		if (e.burnTimer > 0)
		{
			e.burnTimer -= STEP_SECONDS;
			damage(e, e.burnDps * STEP_SECONDS);
		}
	}

	private void gaze(EnemyInstance e)
	{
		e.gazeTimer -= STEP_SECONDS;
		if (e.gazeTimer > 0)
		{
			return;
		}
		DefenderInstance victim = null;
		double best = e.def.gazeRange * e.def.gazeRange;
		for (int i = 0; i < defenders.size(); i++)
		{
			DefenderInstance d = defenders.get(i);
			double dx = d.x - e.x;
			double dy = d.y - e.y;
			double distSq = dx * dx + dy * dy;
			if (distSq <= best)
			{
				best = distSq;
				victim = d;
			}
		}
		if (victim != null)
		{
			victim.stunTimer = Math.max(victim.stunTimer, e.def.gazeSeconds);
			e.gazeTimer = e.def.gazeEverySeconds;
			emit(FxEvent.Kind.GAZE, victim.x, victim.y, 0);
		}
	}

	private void escape(EnemyInstance e)
	{
		e.dead = true;
		baseHealth = Math.max(0, baseHealth - e.def.baseDamage);
		emit(FxEvent.Kind.LEAK, e.x, e.y, e.def.baseDamage);
		if (baseHealth == 0)
		{
			endMatch(false);
		}
	}

	private void attack()
	{
		for (int i = 0; i < defenders.size(); i++)
		{
			DefenderInstance d = defenders.get(i);
			animate(d);
			if (d.stunTimer > 0)
			{
				d.stunTimer -= STEP_SECONDS;
				continue;
			}
			d.cooldown -= STEP_SECONDS;
			if (d.cooldown > 0)
			{
				continue;
			}
			EnemyInstance target = CombatSystem.select(d, enemies);
			if (target == null)
			{
				d.cooldown = 0;
				continue;
			}
			fire(d, target);
			d.cooldown += attackInterval(d);
		}
	}

	/** Real seconds between this defender's attacks, on this map, with any aura. Lower is faster. */
	public double attackInterval(DefenderInstance d)
	{
		return 1 / (d.effectiveAttacksPerSecond() * map.defenderSpeed);
	}

	private void animateDefenders()
	{
		for (int i = 0; i < defenders.size(); i++)
		{
			animate(defenders.get(i));
		}
	}

	private static void animate(DefenderInstance d)
	{
		if (d.attackAnim > 0)
		{
			d.attackAnim -= STEP_SECONDS;
		}
	}

	private void fire(DefenderInstance d, EnemyInstance target)
	{
		d.attackAnim = ATTACK_ANIM_SECONDS;
		d.facingLeft = target.x < d.x;
		d.attackCount++;
		int strikes = d.stats.strikeEvery > 0 && d.attackCount % d.stats.strikeEvery == 0 ? 2 : 1;
		DamageType type = d.def.damageType;
		if (d.def.alternatesDamage && d.attackCount % 2 == 0)
		{
			type = type == DamageType.MAGIC ? DamageType.PHYSICAL : DamageType.MAGIC;
		}

		if (d.def.projectile == null || d.def.projectile.isEmpty() || projectiles.size() >= MAX_PROJECTILES)
		{
			impact(d, type, strikes, target, target.x, target.y);
			return;
		}
		Projectile p = new Projectile();
		p.kind = d.def.projectile;
		p.x = p.prevX = d.x;
		p.y = p.prevY = d.y;
		p.speed = d.def.projectileSpeed;
		p.source = d;
		p.target = target;
		p.targetX = target.x;
		p.targetY = target.y;
		p.type = type;
		p.strikes = strikes;
		projectiles.add(p);
	}

	private void moveProjectiles()
	{
		int write = 0;
		for (int read = 0, n = projectiles.size(); read < n; read++)
		{
			Projectile p = projectiles.get(read);
			if (p.target != null && !p.target.dead)
			{
				p.targetX = p.target.x;
				p.targetY = p.target.y;
			}
			double dx = p.targetX - p.x;
			double dy = p.targetY - p.y;
			double dist = Math.hypot(dx, dy);
			double travel = p.speed * STEP_SECONDS;
			p.prevX = p.x;
			p.prevY = p.y;
			if (dist <= travel)
			{
				boolean targetAlive = p.target != null && !p.target.dead;
				// A direct shot at something already dead fizzles. A splash still lands.
				if (targetAlive || p.source.stats.splash > 0)
				{
					impact(p.source, p.type, p.strikes, targetAlive ? p.target : null, p.targetX, p.targetY);
				}
				continue;
			}
			p.dirX = dx / dist;
			p.dirY = dy / dist;
			p.x += p.dirX * travel;
			p.y += p.dirY * travel;
			projectiles.set(write++, p);
		}
		while (projectiles.size() > write)
		{
			projectiles.remove(projectiles.size() - 1);
		}
	}

	private void impact(DefenderInstance d, DamageType type, int strikes, EnemyInstance target, double x, double y)
	{
		double splash = d.stats.splash;
		if (splash > 0)
		{
			FxEvent blast = emit(FxEvent.Kind.BLAST, x, y, 0);
			blast.sprite = d.def.blast;
			blast.radius = splash;
			double splashSq = splash * splash;
			for (int i = 0; i < enemies.size(); i++)
			{
				EnemyInstance e = enemies.get(i);
				double dx = e.x - x;
				double dy = e.y - y;
				if (!e.dead && dx * dx + dy * dy <= splashSq)
				{
					hit(d, e, type, strikes);
				}
			}
			return;
		}
		if (target == null || target.dead)
		{
			return;
		}
		if (!d.def.precise && target.def.evasion > 0 && rng.nextDouble() < target.def.evasion)
		{
			emit(FxEvent.Kind.MISS, target.x, target.y, 0);
			return;
		}
		hit(d, target, type, strikes);
	}

	private void hit(DefenderInstance d, EnemyInstance e, DamageType type, int strikes)
	{
		// Statuses land first, so a heavy strike benefits from its own armour crack and curse.
		applyStatuses(d.stats, e);
		boolean crit = d.stats.critChance > 0 && rng.nextDouble() < d.stats.critChance;
		double multiplier = e.weakenTimer > 0 ? 1 + e.weakenPercent : 1;
		if (crit)
		{
			multiplier *= d.stats.critMultiplier > 0 ? d.stats.critMultiplier : DEFAULT_CRIT_MULTIPLIER;
		}
		int dealt = 0;
		for (int s = 0; s < strikes; s++)
		{
			dealt += (int) Math.round(CombatSystem.damageFor(d.effectiveDamage(), type, e, d.stats.armourPierce) * multiplier);
		}
		e.hitAnim = HIT_ANIM_SECONDS;
		emit(crit ? FxEvent.Kind.CRIT : FxEvent.Kind.HIT, e.x, e.y, dealt);
		damage(e, dealt);
	}

	private void applyStatuses(Effects s, EnemyInstance e)
	{
		if (s.armourBreak > 0)
		{
			e.armourBroken = Math.max(e.armourBroken, s.armourBreak);
			e.armourBreakTimer = ARMOUR_BREAK_SECONDS;
		}
		if (s.burnDps > 0)
		{
			e.burnDps = e.burnTimer > 0 ? Math.max(e.burnDps, s.burnDps) : s.burnDps;
			e.burnTimer = s.burnSeconds;
		}
		if (s.slowPercent > 0)
		{
			double slow = Math.min(MAX_SLOW, s.slowPercent) * (e.def.boss ? 0.5 : 1);
			e.slowPercent = e.slowTimer > 0 ? Math.max(e.slowPercent, slow) : slow;
			e.slowTimer = s.slowSeconds;
		}
		if (s.rootChance > 0 && e.canBeBound() && rng.nextDouble() < s.rootChance)
		{
			e.rootTimer = s.rootSeconds;
			e.bindLockout = s.rootSeconds * BIND_LOCKOUT_RATIO;
		}
		if (s.weakenPercent > 0)
		{
			e.weakenPercent = e.weakenTimer > 0 ? Math.max(e.weakenPercent, s.weakenPercent) : s.weakenPercent;
			e.weakenTimer = s.weakenSeconds;
		}
	}

	/** All health loss goes through here, so a kill pays out exactly once, however it happens. */
	private void damage(EnemyInstance e, double amount)
	{
		e.hp -= amount;
		if (e.hp <= 0 && !e.dead)
		{
			e.dead = true;
			gold += (int) Math.round(e.def.goldReward * map.goldMultiplier);
			enemiesDefeated++;
			emit(FxEvent.Kind.DEATH, e.x, e.y, e.def.boss ? 2 : 1);
			if (e.def.splitCount > 0)
			{
				splitting.add(e);
			}
			if (map.deathHealRadius > 0)
			{
				healAround(e);
			}
		}
	}

	private void healAround(EnemyInstance fallen)
	{
		FxEvent blast = emit(FxEvent.Kind.BLAST, fallen.x, fallen.y, 0);
		blast.sprite = "blood_blast";
		blast.radius = map.deathHealRadius;
		double radiusSq = map.deathHealRadius * map.deathHealRadius;
		for (int i = 0; i < enemies.size(); i++)
		{
			EnemyInstance e = enemies.get(i);
			double dx = e.x - fallen.x;
			double dy = e.y - fallen.y;
			if (!e.dead && dx * dx + dy * dy <= radiusSq)
			{
				e.hp = Math.min(e.maxHp, e.hp + e.maxHp * map.deathHealPercent);
			}
		}
	}

	private void removeDeadEnemies()
	{
		int write = 0;
		for (int read = 0, n = enemies.size(); read < n; read++)
		{
			EnemyInstance e = enemies.get(read);
			if (!e.dead)
			{
				enemies.set(write++, e);
			}
		}
		while (enemies.size() > write)
		{
			enemies.remove(enemies.size() - 1);
		}
	}

	private boolean isWaveFinished()
	{
		if (!enemies.isEmpty())
		{
			return false;
		}
		WaveDefinition.Group[] groups = currentWave().groups;
		for (int i = 0; i < groups.length; i++)
		{
			if (spawned[i] < groups[i].count)
			{
				return false;
			}
		}
		return true;
	}

	private void finishWave()
	{
		projectiles.clear();
		gold += (int) Math.round(waveBonus(waveIndex) * map.goldMultiplier);
		waveIndex++;
		if (waveIndex >= map.waves.length)
		{
			endMatch(true);
			return;
		}
		autoStartTimer = autoStartDelay;
		state = State.WAVE_COMPLETE;
		checkpoint = buildCheckpoint();
	}

	/** Wave bonus by zero-based index, so the first wave clear pays 30 + 5 x 1. */
	public static int waveBonus(int index)
	{
		return 30 + 5 * (index + 1);
	}

	private void endMatch(boolean victory)
	{
		state = victory ? State.VICTORY : State.DEFEAT;
		checkpoint = null;
		if (matchEndListener != null)
		{
			matchEndListener.onMatchEnd(this, victory);
		}
	}

	private FxEvent emit(FxEvent.Kind kind, double x, double y, int value)
	{
		int slot = (fxHead + fxSize) % FX_CAPACITY;
		if (fxSize == FX_CAPACITY)
		{
			fxHead = (fxHead + 1) % FX_CAPACITY;
		}
		else
		{
			fxSize++;
		}
		FxEvent e = fx[slot];
		if (e == null)
		{
			e = fx[slot] = new FxEvent();
		}
		e.kind = kind;
		e.x = x;
		e.y = y;
		e.value = value;
		return e;
	}

	/** Returns the next pending visual event, or null. The returned object gets reused. */
	public FxEvent pollFx()
	{
		if (fxSize == 0)
		{
			return null;
		}
		FxEvent e = fx[fxHead];
		fxHead = (fxHead + 1) % FX_CAPACITY;
		fxSize--;
		return e;
	}

	// ---- Save and load ------------------------------------------------------

	/**
	 * The resume point for this match, or null if there is nothing to resume.
	 * Between waves it reflects the board now. Mid-wave it is the start of the wave,
	 * so reloading can't bank gold from a wave that was never finished.
	 */
	public MatchSave checkpoint()
	{
		if (!isMatchActive() || map == null)
		{
			return null;
		}
		boolean between = state == State.PREPARING || state == State.WAVE_COMPLETE
			|| (state == State.PAUSED && resumeState != State.RUNNING);
		return between ? buildCheckpoint() : checkpoint;
	}

	private MatchSave buildCheckpoint()
	{
		MatchSave s = new MatchSave();
		s.contentVersion = content.version;
		s.mapId = map.id;
		s.seed = seed;
		s.wave = waveIndex;
		s.gold = gold;
		s.baseHealth = baseHealth;
		s.goldSpent = goldSpent;
		s.enemiesDefeated = enemiesDefeated;
		s.ticks = ticks;
		for (int i = 0; i < defenders.size(); i++)
		{
			DefenderInstance d = defenders.get(i);
			MatchSave.SavedDefender sd = new MatchSave.SavedDefender();
			sd.id = d.def.id;
			sd.col = d.col;
			sd.row = d.row;
			sd.tierA = d.tierA;
			sd.tierB = d.tierB;
			sd.invested = d.invested;
			sd.targeting = d.targeting;
			s.defenders.add(sd);
		}
		return s;
	}

	/** Resumes a checkpoint, paused before its next wave. Returns false if the save is unusable. */
	public boolean restore(MatchSave s)
	{
		if (s == null || s.schemaVersion != MatchSave.SCHEMA || !content.version.equals(s.contentVersion))
		{
			return false;
		}
		MapDefinition m = content.map(s.mapId);
		if (m == null || s.wave < 0 || s.wave >= m.waves.length || s.gold < 0 || s.baseHealth <= 0)
		{
			return false;
		}
		List<DefenderInstance> restored = new ArrayList<>();
		for (MatchSave.SavedDefender sd : s.defenders)
		{
			DefenderDefinition def = content.defender(sd.id);
			if (def == null || !m.canFit(sd.col, sd.row, def.size) || sd.tierA < 0 || sd.tierB < 0
				|| sd.tierA > def.pathA.length || sd.tierB > def.pathB.length)
			{
				return false;
			}
			DefenderInstance d = new DefenderInstance(def, sd.col, sd.row);
			d.tierA = sd.tierA;
			d.tierB = sd.tierB;
			d.invested = sd.invested;
			d.targeting = sd.targeting == null ? def.targeting : sd.targeting;
			d.recalculate();
			for (DefenderInstance other : restored)
			{
				if (overlaps(d, other))
				{
					return false;
				}
			}
			restored.add(d);
		}
		loadMap(m, s.seed);
		defenders.addAll(restored);
		recalculateAuras();
		gold = s.gold;
		baseHealth = s.baseHealth;
		waveIndex = s.wave;
		goldSpent = s.goldSpent;
		enemiesDefeated = s.enemiesDefeated;
		ticks = s.ticks;
		state = State.PREPARING;
		checkpoint = null;
		return true;
	}

	private static boolean overlaps(DefenderInstance a, DefenderInstance b)
	{
		return a.col < b.col + b.def.size && b.col < a.col + a.def.size
			&& a.row < b.row + b.def.size && b.row < a.row + a.def.size;
	}

	// ---- Accessors ----------------------------------------------------------

	public void setMatchEndListener(MatchEndListener listener)
	{
		matchEndListener = listener;
	}

	public void setPlayerLevel(int level)
	{
		playerLevel = level;
	}

	/** Seconds before the next wave starts by itself; 0 for manual play. */
	public void setAutoStartDelay(double seconds)
	{
		if (seconds != autoStartDelay)
		{
			autoStartDelay = seconds;
			autoStartTimer = seconds;
		}
	}

	public double autoStartTimer()
	{
		return autoStartDelay > 0 ? autoStartTimer : 0;
	}

	public State state()
	{
		return state;
	}

	public Content content()
	{
		return content;
	}

	public MapDefinition map()
	{
		return map;
	}

	public List<DefenderInstance> defenders()
	{
		return defenders;
	}

	public List<EnemyInstance> enemies()
	{
		return enemies;
	}

	public List<Projectile> projectiles()
	{
		return projectiles;
	}

	public int gold()
	{
		return gold;
	}

	public int baseHealth()
	{
		return baseHealth;
	}

	/** Zero-based index of the current wave, or of the next one between waves. */
	public int waveIndex()
	{
		return waveIndex;
	}

	public int waveCount()
	{
		return map == null ? 0 : map.waves.length;
	}

	public int wavesCleared()
	{
		return waveIndex;
	}

	public WaveDefinition currentWave()
	{
		return map.waves[Math.min(waveIndex, map.waves.length - 1)];
	}

	public int goldSpent()
	{
		return goldSpent;
	}

	public int enemiesDefeated()
	{
		return enemiesDefeated;
	}

	public long ticks()
	{
		return ticks;
	}

	public int score()
	{
		return ScoreCalculator.score(wavesCleared(), baseHealth, enemiesDefeated, state == State.VICTORY);
	}
}
