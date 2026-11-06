#!/usr/bin/env python3
"""Rewrites the late waves in content.json from the tables below.

Waves 1 to 25 are written by hand in content.json and are left alone. Everything
after that comes from these tables, so that tuning the long game means changing a
few numbers here and checking the result with ./gradlew balanceReport.

Health grows as a polynomial of the wave number and never exponentially.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CONTENT = os.path.join(ROOT, "src", "main", "resources", "com", "runedefence", "content.json")

LAST_WAVE = 99
AUTHORED = 25

# Health multiplier, for waves past the hand-written ones.
HEALTH_SCALE = 1.9
HEALTH_POWER = 2.1

# (enemy, how many at wave 26, ms between spawns). Count grows with the wave number.
TIERS = {
    (26, 39): [
        [("goblin_raider", 26, 500)],
        [("armoured_zombie", 10, 1200), ("skeleton", 10, 900)],
        [("spider", 22, 500)],
        [("hill_giant", 6, 2600)],
        [("basilisk", 6, 1800), ("goblin_raider", 14, 700)],
        [("giant_rat", 24, 400), ("armoured_zombie", 8, 1300)],
    ],
    (40, 59): [
        [("big_frog", 10, 900), ("giant_rat", 16, 450)],
        [("armoured_zombie", 20, 800)],
        [("giant_spider", 12, 900), ("spider", 16, 500)],
        [("ogre", 7, 2200), ("hill_giant", 4, 3000)],
        [("basilisk", 8, 1500), ("armoured_zombie", 10, 1000)],
        [("big_frog", 8, 1000), ("ogre", 4, 2600)],
    ],
    (60, 79): [
        [("big_frog", 24, 550)],
        [("armoured_zombie", 18, 800), ("ogre", 6, 2200)],
        [("giant_spider", 22, 650)],
        [("minotaur", 7, 2200)],
        [("basilisk", 10, 1300), ("minotaur", 4, 2600)],
        [("ogre_chieftain", 3, 3500), ("big_frog", 14, 700)],
    ],
    (80, 98): [
        [("big_frog", 22, 500), ("giant_spider", 12, 800)],
        [("ogre_chieftain", 4, 3000), ("armoured_zombie", 14, 800)],
        [("giant_spider", 28, 500)],
        [("minotaur", 7, 2000), ("ogre_chieftain", 2, 4000)],
        [("basilisk", 12, 1100), ("ogre_chieftain", 3, 3200)],
        [("minotaur", 5, 2200), ("ogre", 5, 2200), ("big_frog", 12, 700)],
    ],
}

# Boss waves replace the normal wave. (enemy, count, health multiplier)
BOSSES = {
    30: [("goblin_champion", 1, 2.6)],
    40: [("goblin_champion", 1, 4.5), ("armoured_zombie", 8, 3.0)],
    50: [("goblin_warlord", 1, 0.6)],
    60: [("goblin_champion", 3, 9.0), ("hill_giant", 6, 10.0)],
    70: [("goblin_warlord", 1, 1.3)],
    75: [("goblin_warlord", 1, 1.6)],
    85: [("goblin_warlord", 2, 1.9)],
    90: [("goblin_warlord", 1, 3.2)],
    99: [("goblin_warlord", 3, 3.2), ("ogre_chieftain", 4, 26.0)],
}


def health(wave):
    # The last stretch climbs faster: it's meant to need a full board of the best units.
    late = 1 + max(0, wave - 80) * 0.03
    return round(HEALTH_SCALE * (wave / AUTHORED) ** HEALTH_POWER * late, 2)


def tier(wave):
    for (low, high), themes in TIERS.items():
        if low <= wave <= high:
            return themes
    raise ValueError(wave)


def normal_wave(wave):
    themes = tier(wave)
    theme = themes[wave % len(themes)]
    # Numbers rise inside a tier, but gently: most of the challenge comes from health.
    grow = 1 + (wave - AUTHORED) / 110
    groups = []
    delay = 0
    for enemy, count, interval in theme:
        total = int(round(count * grow))
        groups.append({"enemy": enemy, "count": total, "intervalMs": interval, "delayMs": delay, "health": health(wave)})
        delay += 3000
    return {"groups": groups}


def boss_wave(wave):
    groups = []
    delay = 0
    for enemy, count, multiplier in BOSSES[wave]:
        groups.append({"enemy": enemy, "count": count, "intervalMs": 6000, "delayMs": delay, "health": multiplier})
        delay += 5000
    return {"groups": groups}


def dump(content):
    """One defender, enemy or wave per line, which keeps the file reviewable."""
    def line(value):
        return json.dumps(value, separators=(",", ":"))

    out = ['{\n"version":%s,\n"defenders":[' % line(content["version"])]
    out.append(",\n".join(line(d) for d in content["defenders"]))
    out.append('],\n"enemies":[')
    out.append(",\n".join(line(e) for e in content["enemies"]))
    out.append('],\n"maps":[')
    maps = []
    for m in content["maps"]:
        head = {k: v for k, v in m.items() if k != "waves"}
        body = line(head)[:-1]
        waves = ",\n".join(line(w) for w in m["waves"])
        maps.append(body + ',"waves":[\n' + waves + "\n]}")
    out.append(",\n".join(maps))
    out.append("]\n}\n")
    return "\n".join(out)


# Each of the other maps: how many waves, how much later on the health curve it starts,
# which themes it cycles through, and its bosses.
MAPS = {
    "varrock_outskirts": dict(waves=40, offset=0, themes=[
        [("giant_rat", 14, 500), ("barbarian", 8, 800)],
        [("barbarian", 14, 600)],
        [("hobgoblin", 8, 1000), ("giant_rat", 10, 500)],
        [("black_knight", 8, 1200)],
        [("hobgoblin", 14, 700)],
        [("barbarian", 8, 800), ("black_knight", 5, 1300)],
    ], intro=[
        [("giant_rat", 14, 500)],
        [("barbarian", 8, 900)],
        [("hobgoblin", 6, 1200)],
        [("giant_rat", 10, 500), ("barbarian", 6, 900)],
        [("hobgoblin", 8, 1000)],
        [("barbarian", 12, 700)],
    ], bosses={10: [("delrith", 1, 0.32)], 20: [("delrith", 1, 0.64)], 30: [("black_knight", 5, 6.0)],
               40: [("delrith", 2, 1.44), ("barbarian", 8, 6.0)]}),
    "morytania_marshes": dict(waves=50, offset=8, themes=[
        [("ghoul", 12, 900), ("bloodveld", 4, 1400)],
        [("ankou", 8, 1100)],
        [("vampyre", 7, 1500)],
        [("bloodveld", 10, 1000)],
        [("banshee", 12, 800)],
        [("vampyre", 5, 1600), ("ankou", 6, 1100)],
    ], bosses={15: [("bloodveld", 4, 4.0)], 25: [("vampyre_lord", 1, 0.55)], 40: [("vampyre_lord", 1, 1.0)],
               50: [("vampyre_lord", 2, 1.4), ("vampyre", 8, 8.0)]}),
    "tzhaar_caverns": dict(waves=60, offset=2, themes=[
        [("tzhaar_ket", 6, 1600)],
        [("tz_kih", 14, 700)],
        [("tzhaar_ket", 4, 1800), ("tz_kek", 3, 2400)],
        [("hellhound", 12, 800)],
        [("tz_kek", 4, 1800)],
        [("hellhound", 8, 900), ("tzhaar_ket", 3, 2000)],
    ], bosses={10: [("obsidian_golem", 1, 0.09)], 20: [("obsidian_golem", 1, 0.2)], 30: [("obsidian_golem", 1, 0.35)],
               40: [("obsidian_golem", 1, 0.7)], 50: [("obsidian_golem", 1, 0.9)],
               60: [("obsidian_golem", 2, 1.2), ("tzhaar_ket", 8, 14.0)]}),
    "chambers_of_xeric": dict(waves=60, offset=2, themes=[
        [("lizardman_shaman", 8, 1300)],
        [("vanguard", 10, 1000)],
        [("guardian", 4, 2400), ("lizardman_shaman", 4, 1400)],
        [("vanguard", 6, 1000), ("guardian", 3, 2600)],
        [("skeletal_mystic", 14, 800)],
        [("guardian", 4, 2200), ("skeletal_mystic", 4, 1100)],
    ], bosses={20: [("great_olm", 1, 0.25)], 40: [("great_olm", 1, 0.7)],
               60: [("great_olm", 2, 1.2), ("vanguard", 8, 14.0)]}),
    "wilderness_crossing": dict(waves=60, offset=6, themes=[
        [("revenant", 7, 1100)],
        [("chaos_druid", 12, 700)],
        [("green_dragon", 2, 3000), ("revenant", 4, 1200)],
        [("chaos_druid", 8, 800), ("revenant", 4, 1200)],
        [("greater_demon", 4, 2400)],
        [("revenant", 10, 900)],
    ], bosses={15: [("greater_demon", 3, 3.0)], 30: [("king_black_dragon", 1, 0.7)], 45: [("king_black_dragon", 1, 1.5)],
               60: [("king_black_dragon", 2, 1.8), ("kril_tsutsaroth", 1, 1.0), ("revenant", 8, 12.0)]}),
    "theatre_of_blood": dict(waves=70, offset=12, themes=[
        [("nylocas", 8, 1200)],
        [("bloat", 2, 3600), ("blood_spawn", 12, 500)],
        [("nylocas", 12, 900)],
        [("bloat", 3, 3000)],
        [("blood_spawn", 30, 300)],
        [("nylocas", 8, 1100), ("bloat", 2, 3400)],
    ], bosses={35: [("verzik", 1, 0.5)], 55: [("verzik", 1, 1.1)],
               70: [("verzik", 2, 1.6), ("bloat", 6, 14.0)]}),
}


def monsters_of(map_data, enemies):
    """Every monster a map can field, in order of appearance, including what they split into."""
    found = []

    def add(enemy):
        if enemy not in found:
            found.append(enemy)
            child = enemies[enemy].get("splitInto")
            if child:
                add(child)

    for wave in map_data["waves"]:
        for group in wave["groups"]:
            add(group["enemy"])
    return found


def themed_waves(config):
    waves = []
    for wave in range(1, config["waves"] + 1):
        if wave in config["bosses"]:
            groups = []
            delay = 0
            for enemy, count, multiplier in config["bosses"][wave]:
                groups.append({"enemy": enemy, "count": count, "intervalMs": 6000, "delayMs": delay, "health": multiplier})
                delay += 5000
            waves.append({"groups": groups})
            continue
        # An optional gentler opening, so that a map's toughest monsters arrive later.
        intro = config.get("intro", [])
        theme = intro[wave - 1] if wave <= len(intro) else config["themes"][wave % len(config["themes"])]
        grow = 0.6 + wave / config["waves"] * 0.8
        effective = wave + config["offset"]
        # The first waves ease in, so that an opening board has time to pay for itself.
        mult = max(0.4, round(HEALTH_SCALE * (effective / AUTHORED) ** HEALTH_POWER * min(1, 0.25 + wave / 16), 2))
        groups = []
        delay = 0
        for enemy, count, interval in theme:
            groups.append({"enemy": enemy, "count": max(1, int(round(count * grow))), "intervalMs": interval,
                           "delayMs": delay, "health": mult})
            delay += 3000
        waves.append({"groups": groups})
    return waves


def main():
    with open(CONTENT) as f:
        content = json.load(f)
    lumbridge = content["maps"][0]
    waves = lumbridge["waves"][:AUTHORED]
    for wave in range(AUTHORED + 1, LAST_WAVE + 1):
        waves.append(boss_wave(wave) if wave in BOSSES else normal_wave(wave))
    lumbridge["waves"] = waves
    for m in content["maps"][1:]:
        m["waves"] = themed_waves(MAPS[m["id"]])
    enemies = {e["id"]: e for e in content["enemies"]}
    for m in content["maps"]:
        m["monsters"] = monsters_of(m, enemies)
    with open(CONTENT, "w") as f:
        f.write(dump(content))
    print("wrote waves: " + ", ".join("%s %d" % (m["id"], len(m["waves"])) for m in content["maps"]))


if __name__ == "__main__":
    main()
