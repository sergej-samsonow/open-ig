/*
 * Copyright 2008-2012, David Karnok 
 * The file is part of the Open Imperium Galactica project.
 * 
 * The code should be distributed under the LGPL license.
 * See http://www.gnu.org/licenses/lgpl.html for details.
 */

package hu.openig.mechanics;

import hu.openig.core.Action0;
import hu.openig.core.Difficulty;
import hu.openig.core.Pred1;
import hu.openig.model.AIControls;
import hu.openig.model.AIFleet;
import hu.openig.model.AIInventoryItem;
import hu.openig.model.AIPlanet;
import hu.openig.model.AIWorld;
import hu.openig.model.BattleGroundVehicle;
import hu.openig.model.BattleProjectile;
import hu.openig.model.BattleProjectile.Mode;
import hu.openig.model.EquipmentSlot;
import hu.openig.model.Fleet;
import hu.openig.model.FleetTask;
import hu.openig.model.GroundwarUnitType;
import hu.openig.model.InventorySlot;
import hu.openig.model.Planet;
import hu.openig.model.Production;
import hu.openig.model.ResearchMainCategory;
import hu.openig.model.ResearchSubCategory;
import hu.openig.model.ResearchType;
import hu.openig.model.VehiclePlan;
import hu.openig.utils.U;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plans the creation of various ships, equipment and vehicles.
 * @author akarnokd, 2012.01.05.
 */
public class OffensePlanner extends Planner {
	/** Comparator for firepower, ascending order. */
	final Comparator<AIFleet> firepowerAsc = new Comparator<AIFleet>() {
		@Override
		public int compare(AIFleet o1, AIFleet o2) {
			return o1.statistics.firepower - o2.statistics.firepower;
		}
	};
	/**
	 * Compare effective firepower of two cruiser/destroyer technologies.
	 */
	final Comparator<ResearchType> effectiveFirepower = new Comparator<ResearchType>() {
		@Override
		public int compare(ResearchType o1, ResearchType o2) {
			int v1 = firepower(o1);
			int v2 = firepower(o2);
			return v2 - v1;
		}
	};
	/**
	 * Initializes the planner.
	 * @param world the current world
	 * @param controls the controls
	 */
	public OffensePlanner(AIWorld world, AIControls controls) {
		super(world, controls);
	}

	@Override
	protected void plan() {
//		if (checkSellOldTech()) {
//			return;
//		}
		if (world.money < 100000) {
			return;
		}
		
		if (upgradeFleets()) {
			return;
		}
		// have a fleet for every N planets + 1
		int divider = 5;
		if (w.difficulty == Difficulty.NORMAL) {
			divider = 4;
		} else
		if (w.difficulty == Difficulty.HARD) {
			divider = 3;
		}
		// construct fleets
		if (world.ownPlanets.size() / divider + 1 > world.ownFleets.size()) {
			if (createNewFleet()) {
				return;
			}
		}
	}
	/** 
	 * Create a new fleet.
	 * @return action taken 
	 */
	boolean createNewFleet() {
		final List<ResearchType> fighters = U.sort2(availableResearchOf(EnumSet.of(ResearchSubCategory.SPACESHIPS_FIGHTERS)), ResearchType.EXPENSIVE_FIRST);
		final List<ResearchType> cruisers = U.sort2(availableResearchOf(EnumSet.of(ResearchSubCategory.SPACESHIPS_CRUISERS)), effectiveFirepower);
		final List<ResearchType> battleships = U.sort2(availableResearchOf(EnumSet.of(ResearchSubCategory.SPACESHIPS_BATTLESHIPS)), ResearchType.EXPENSIVE_FIRST);
		battleships.remove(world.isAvailable("ColonyShip"));
		
		if (!fighters.isEmpty()) {
			ResearchType rt = fighters.get(0);
			UpgradeResult r = checkProduction(rt, 1, 0);
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			}
		}
		if (!cruisers.isEmpty()) {
			ResearchType rt = cruisers.get(0);
			UpgradeResult r = checkProduction(rt, 1, 0);
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			}
		}
		if (!battleships.isEmpty()) {
			ResearchType rt = battleships.get(0);
			UpgradeResult r = checkProduction(rt, 1, 0);
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			}
		}

		if (checkMilitarySpaceport()) {
			return true;
		}
		
		final Planet spaceport = findBestMilitarySpaceport().planet;
		
		add(new Action0() {
			@Override
			public void invoke() {
				Fleet f = controls.actionCreateFleet(w.env.labels().get(p.id + ".fleet"), spaceport);
				if (!fighters.isEmpty()) {
					ResearchType rt = fighters.get(0);
					if (f.owner.inventoryCount(rt) > 0) {
						f.addInventory(rt, 1);
						f.owner.changeInventoryCount(rt, -1);
					}
				}
				if (!cruisers.isEmpty()) {
					ResearchType rt = cruisers.get(0);
					if (f.owner.inventoryCount(rt) > 0) {
						f.addInventory(rt, 1);
						f.owner.changeInventoryCount(rt, -1);
					}
				}
				if (!battleships.isEmpty()) {
					ResearchType rt = battleships.get(0);
					if (f.owner.inventoryCount(rt) > 0) {
						f.addInventory(rt, 1);
						f.owner.changeInventoryCount(rt, -1);
					}
				}
				if (f.inventory.isEmpty()) {
					w.removeFleet(f);
					log("DeployFleet, Fleet = %s, Planet = %s, Failed = Not enough inventory", f.name, spaceport.id);
				}
			}
		});
		
		return true;
	}
	/**
	 * Compute the effective firepower of the ship by considering the best available weapon technology.
	 * @param ship the ship type
	 * @return the reachable firepower
	 */
	int firepower(ResearchType ship) {
		int result = 0;
		for (EquipmentSlot es : ship.slots.values()) {
			ResearchType w = null;
			if (es.fixed) {
				w = es.items.get(0);
			} else {
				for (ResearchType rt0 : es.items) {
					if (world.isAvailable(rt0)) {
						w = rt0;
					}
				}
			}
			if (w != null) {
				BattleProjectile proj = this.w.battle.projectiles.get(w.id);
				if (proj != null) {
					result += proj.damage * es.max;
				}
			}
		}
		return result;
	}
	/**
	 * Compute the equipment demands for the best available technologies to fill-in the ship.
	 * @param ship the ship technology
	 * @param demands the map from equipment to demand
	 */
	void equipmentDemands(ResearchType ship, Map<ResearchType, Integer> demands) {
		for (EquipmentSlot es : ship.slots.values()) {
			if (!es.fixed) {
				ResearchType w = null;
				for (ResearchType rt0 : es.items) {
					if (world.isAvailable(rt0)) {
						w = rt0;
					}
				}
				if (w != null) {
					Integer cnt = demands.get(w);
					demands.put(w, cnt != null ? cnt + es.max : es.max);
				}
			}
		}		
	}
	/** The upgrade result. */
	enum UpgradeResult {
		/** Wait, return with false. */
		WAIT,
		/** Action taken, return with true. */
		ACTION,
		/** Bring in fleet for upgrades. */
		DEPLOY,
		/** Continue with further checks. */
		CONTINUE
	}
	/**
	 * Organize the upgrade of fleets.
	 * @return action taken
	 */
	boolean upgradeFleets() {
		if (world.ownFleets.size() == 0) {
			return false;
		}

		final List<ResearchType> fighters = U.sort2(availableResearchOf(EnumSet.of(ResearchSubCategory.SPACESHIPS_FIGHTERS)), ResearchType.EXPENSIVE_FIRST);
		final List<ResearchType> cruisers = U.sort2(availableResearchOf(EnumSet.of(ResearchSubCategory.SPACESHIPS_CRUISERS)), effectiveFirepower);
		final List<ResearchType> battleships = U.sort2(availableResearchOf(EnumSet.of(ResearchSubCategory.SPACESHIPS_BATTLESHIPS)), ResearchType.EXPENSIVE_FIRST);
		battleships.remove(world.isAvailable("ColonyShip"));

		if (checkDeploy(cruisers, battleships)) {
			return true;
		}
		if (checkCounts(fighters, cruisers, battleships)) {
			return true;
		}
		
		return false;
	}
	/**
	 * Check if a fleet is in upgrade position over a planet.
	 * @param cruisers the list of cruiser/destroyer technology ordered by expense
	 * @param battleships the list of battleships ordered by expense
	 * @return true if action taken
	 */
	boolean checkDeploy(final List<ResearchType> cruisers,
			final List<ResearchType> battleships) {

		List<AIFleet> upgradeTasks = findFleetsWithTask(FleetTask.UPGRADE, new Pred1<AIFleet>() {
			@Override
			public Boolean invoke(AIFleet value) {
				return !value.isMoving() && value.statistics.planet != null;
			}
		});
		if (upgradeTasks.isEmpty()) {
			if (!findFleetsWithTask(FleetTask.UPGRADE, null).isEmpty()) {
				return true;
			}
			return false;
		}
		
		final Fleet fleet = Collections.min(upgradeTasks, firepowerAsc).fleet;
		
		add(new Action0() {
			@Override
			public void invoke() {
				fleet.upgradeAll();
				if (!cruisers.isEmpty()) {
					fleet.replaceWithShip(cruisers.get(0), 25);
				}
				if (!battleships.isEmpty()) {
					fleet.replaceWithShip(battleships.get(0), 3);
				}
				fleet.task = FleetTask.IDLE;
				log("UpgradeFleet, Fleet = %s", fleet.name);
			}
		});
		
		return true;
	}
	/**
	 * Check if the ship or equipment counts and levels are okay.
	 * @param fighters the list of fighter technology ordered by expense
	 * @param cruisers the list of cruiser/destroyer technology ordered by expense
	 * @param battleships the list of battleships ordered by expense
	 * @return true if action taken
	 */
	boolean checkCounts(final List<ResearchType> fighters,
			final List<ResearchType> cruisers,
			final List<ResearchType> battleships) {
		List<AIFleet> upgradeCandidates = findFleetsFor(FleetTask.UPGRADE, null);
		if (upgradeCandidates.isEmpty()) {
			return false;
		}
		final AIFleet fleet = Collections.min(upgradeCandidates, firepowerAsc);

		
		Map<ResearchType, Integer> currentInventory = U.newHashMap();
		
		for (AIInventoryItem ii : fleet.inventory) {
			Integer cnt = currentInventory.get(ii.type);
			currentInventory.put(ii.type, cnt != null ? cnt + ii.count : ii.count);
		}
		
		// check if figthers are well equipped?
		for (ResearchType rt : fighters) {
			int ci = nvl(currentInventory.get(rt));
			UpgradeResult r = checkProduction(rt, Math.min(30, ci + 10), ci);
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.DEPLOY) {
				bringinFleet(fleet);
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			}
		}
		
		// check if best cruiser is filled in
		if (!cruisers.isEmpty()) {
			ResearchType rt = cruisers.get(0);
			int ci = nvl(currentInventory.get(rt));
			UpgradeResult r = checkProduction(rt, Math.min(25, ci + 5), ci);
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			} else
			if (r == UpgradeResult.DEPLOY) {
				bringinFleet(fleet);
				return true;
			}
		}
		
		// check if best battleship is filled in
		if (!battleships.isEmpty()) {
			ResearchType rt = battleships.get(0);
			int ci = nvl(currentInventory.get(rt));
			UpgradeResult r = checkProduction(rt, Math.min(3, ci + 1), ci);
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			} else
			if (r == UpgradeResult.DEPLOY) {
				bringinFleet(fleet);
				return true;
			}
		}
		
		// scan for upgradable slots
		Map<ResearchType, Integer> equipmentDemands = U.newHashMap();
		for (AIInventoryItem ii : fleet.inventory) {
			for (InventorySlot is : ii.slots) {
				if (!is.slot.fixed) {
					// current
					ResearchType current = is.type;
					// find best
					ResearchType best = null;
					for (ResearchType rt : is.slot.items) {
						if (world.isAvailable(rt)) {
							if (best == null || best.productionCost < rt.productionCost) {
								best = rt;
							}
						}
					}
					if (best != null) {
						int cnt = nvl(equipmentDemands.get(best));
						// if we have better, add full max demand
						if (best != current) {
							equipmentDemands.put(best, cnt + is.slot.max * ii.count);
						} else {
							if (is.slot.max > is.count) {
								// else add only demand for the missing counts
								equipmentDemands.put(best, cnt + (is.slot.max - is.count) * ii.count);
							}
						}
					}
				}
			}
		}
		
		// create equipment and upgrade the fleet
		Set<ResearchMainCategory> running = U.newHashSet();
		for (Map.Entry<ResearchType, Integer> e : equipmentDemands.entrySet()) {
			ResearchType rt = e.getKey();
			if (!running.contains(rt.category.main)) {
				int count = Math.min(30, e.getValue());
				if (count > 0) {
					UpgradeResult r = checkProduction(rt, count, world.inventoryCount(rt));
					if (r == UpgradeResult.ACTION) {
						return true;
					} else
					if (r == UpgradeResult.WAIT || r == UpgradeResult.CONTINUE) {
						running.add(rt.category.main);
					} else
					if (r == UpgradeResult.DEPLOY) {
						bringinFleet(fleet);
						return true;
					}
				}
			}
		}
		if (running.size() > 0) {
			return false;
		}
		// plan for vehicles
		VehiclePlan plan = new VehiclePlan();
		plan.calculate(world.availableResearch, w.battle, fleet.statistics.vehicleMax);
		
		// create equipment and upgrade the fleet
		for (Map.Entry<ResearchType, Integer> e : plan.demand.entrySet()) {
			ResearchType rt = e.getKey();
			int count = Math.min(10, e.getValue());
			UpgradeResult r = checkProduction(rt, count, world.inventoryCount(rt));
			if (r == UpgradeResult.ACTION) {
				return true;
			} else
			if (r == UpgradeResult.WAIT) {
				return false;
			} else
			if (r == UpgradeResult.DEPLOY) {
				bringinFleet(fleet);
				return true;
			}
		}
		
		
		return false;
	}
	/**
	 * Bring in the fleet to the closest spaceport for upgrades.
	 * @param fleet the target fleet
	 */
	void bringinFleet(final AIFleet fleet) {
		if (!checkMilitarySpaceport()) {
			final AIPlanet spaceport = findClosestMilitarySpaceport(fleet.x, fleet.y);
			add(new Action0() {
				@Override
				public void invoke() {
					fleet.fleet.task = FleetTask.UPGRADE;
					controls.actionMoveFleet(fleet.fleet, spaceport.planet);
				}
			});
		}
	}
	/**
	 * Check the inventory and production status of the given technology.
	 * @param rt the target technology
	 * @param max the maximum amount
	 * @param currentInventory the current inventory level
	 * @return the result
	 */
	UpgradeResult checkProduction(ResearchType rt, int max, int currentInventory) {
		if (currentInventory < max) {
			int globalInventory = world.inventoryCount(rt);
			int required = max - currentInventory;
			if (required > globalInventory) {
				if (world.productionCount(rt) > 0) {
					return UpgradeResult.WAIT;
				}
				placeProductionOrder(rt, required - globalInventory);
				return UpgradeResult.ACTION;
			} else {
				return UpgradeResult.DEPLOY; 
			}
		}
		return UpgradeResult.CONTINUE;
	}
	/**
	 * Returns 0 if {@code i} is null, or the value itself.
	 * @param value the value
	 * @return the int value
	 */
	int nvl(Integer value) {
		return value != null ? value : 0;
	}
	/**
	 * Returns the list of available technologies matching the given set of categories.
	 * @param categories the category set
	 * @return the list of available research
	 */
	List<ResearchType> availableResearchOf(EnumSet<ResearchSubCategory> categories) {
		List<ResearchType> result = U.newArrayList();
		for (ResearchType rt : world.availableResearch) {
			if (categories.contains(rt.category)) {
				result.add(rt);
			}
		}
		return result;
	}
	/**
	 * Sell old technologies from inventory.
	 * @return true if action taken
	 */
	boolean checkSellOldTech() {
		Set<ResearchType> inuse = U.newHashSet();
		for (Production prod : world.productions.values()) {
			inuse.add(prod.type);
		}
		
		Map<String, Pred1<ResearchType>> filters = U.newHashMap();
		Map<String, ResearchType> bestValue = U.newHashMap();
		filters.put("Tank", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				return value.category == ResearchSubCategory.WEAPONS_TANKS;
			}
		});
		filters.put("Laser", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				return value.category == ResearchSubCategory.WEAPONS_LASERS;
			}
		});
		filters.put("Cannon", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				return value.category == ResearchSubCategory.WEAPONS_CANNONS;
			}
		});
		filters.put("Rocket", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.WEAPONS_PROJECTILES) {
					BattleProjectile e = w.battle.projectiles.get(value.id);
					if (e != null && (e.mode == Mode.ROCKET || e.mode == Mode.MULTI_ROCKET)) {
						return true;
					}
				}
				return false;
			}
		});
		filters.put("Bomb", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.WEAPONS_PROJECTILES) {
					BattleProjectile e = w.battle.projectiles.get(value.id);
					if (e != null && (e.mode == Mode.BOMB || e.mode == Mode.VIRUS)) {
						return true;
					}
				}
				return false;
			}
		});
		filters.put("Radar", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				return value.category == ResearchSubCategory.EQUIPMENT_RADARS;
			}
		});
		filters.put("Shield", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				return value.category == ResearchSubCategory.EQUIPMENT_SHIELDS;
			}
		});
		filters.put("Hyperdrive", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				return value.category == ResearchSubCategory.EQUIPMENT_HYPERDRIVES;
			}
		});
		filters.put("ECM", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.EQUIPMENT_MODULES) {
					if (value.has("ecm")) {
						return true;
					}
				}
				return false;
			}
		});
		filters.put("Storage", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.EQUIPMENT_MODULES) {
					if (value.has("vehicles")) {
						return true;
					}
				}
				return false;
			}
		});
		filters.put("Station", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.SPACESHIPS_STATIONS) {
					return !value.id.equals("OrbitalFactory");
				}
				return false;
			}
		});
		filters.put("OrbitalFactory", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.SPACESHIPS_STATIONS) {
					return value.id.equals("OrbitalFactory");
				}
				return false;
			}
		});
		filters.put("Spysatellites", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.SPACESHIPS_SATELLITES) {
					return value.has("detector");
				}
				return false;
			}
		});
		filters.put("RadarSatellite", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.SPACESHIPS_SATELLITES) {
					return value.has("radar");
				}
				return false;
			}
		});
		filters.put("Sled", new Pred1<ResearchType>() {
			@Override
			public Boolean invoke(ResearchType value) {
				if (value.category == ResearchSubCategory.WEAPONS_VEHICLES) {
					BattleGroundVehicle veh = w.battle.groundEntities.get(value.id);
					if (veh != null && veh.type == GroundwarUnitType.ROCKET_SLED) {
						return true;
					}
				}
				return false;
			}
		});
		
		for (ResearchType rt : world.availableResearch) {
			for (Map.Entry<String, Pred1<ResearchType>> f : filters.entrySet()) {
				if (f.getValue().invoke(rt)) {
					ResearchType best = bestValue.get(f.getKey());
					if (best == null || best.productionCost < rt.productionCost) {
						bestValue.put(f.getKey(), rt);
					}
				}
			}
			if (rt.category == ResearchSubCategory.WEAPONS_VEHICLES) {
				BattleGroundVehicle veh = w.battle.groundEntities.get(rt.id);
				if (veh == null || veh.type != GroundwarUnitType.ROCKET_SLED) {
					inuse.add(rt);
				}
			}
			
			if (rt.category == ResearchSubCategory.SPACESHIPS_FIGHTERS) {
				inuse.add(rt);
			} else
			if (rt.category == ResearchSubCategory.SPACESHIPS_BATTLESHIPS) {
				inuse.add(rt);
			} else
			if (rt.category == ResearchSubCategory.SPACESHIPS_CRUISERS) {
				inuse.add(rt);
			}
		}
		for (AIFleet f : world.ownFleets) {
			for (AIInventoryItem ii : f.inventory) {
				for (InventorySlot is : ii.slots) {
					if (is.type != null) {
						inuse.add(is.type);
					}
				}
			}
		}
		inuse.addAll(bestValue.values());
		
		for (Map.Entry<ResearchType, Integer> e : world.inventory.entrySet()) {
			final ResearchType key = e.getKey();
			final int value = e.getValue();
			if (!inuse.contains(key) && value > 0) {
				add(new Action0() {
					@Override
					public void invoke() {
						p.changeInventoryCount(key, -value);
						
						long money = key.productionCost * 1L * value / 2;
						p.money += money;
						p.statistics.moneyIncome += money;
						p.statistics.moneySellIncome += money;
						p.world.statistics.moneyIncome += money;
						p.world.statistics.moneySellIncome += money;
						
						log("SellOldTechnology, Technology = %s, Amount = %s, Money = %s", key.id, value, money);
					}
				});
				return true;
			}
		}
		
		return false;
	}
}
