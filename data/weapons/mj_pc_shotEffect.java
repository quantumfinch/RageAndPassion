package data.weapons;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.awt.Color;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ArmorGridAPI;
import com.fs.starfarer.api.combat.CollisionClass;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;

import com.fs.starfarer.api.combat.OnFireEffectPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;

import org.apache.log4j.Logger; // *** THIS IS THE CRUCIAL CHANGE ***

/**
 * QF
 * Piercing effect must be done manually given how engine manages on hit effects
 * and collission classes.
 * Using my own code from the deprecated reflector shield hullmod as reference
 * as it checks for every projectile.
 */

public class mj_pc_shotEffect implements OnFireEffectPlugin {
	private static final String PLUGIN_KEY = "Piercing_proj_plugin";
	public static final Logger LOG = Logger.getLogger(mj_pc_shotEffect.class);

	public void onFire(DamagingProjectileAPI projectile, WeaponAPI weapon, CombatEngineAPI engine) {
		// Dont care about projectile type
		// Ship must exist
		ShipAPI ship = null;
		if (weapon != null)
			ship = weapon.getShip();
		if (ship == null)
			return;
		// Onboard shots to script
		if (engine != null) {
			mj_pc_shotScript plugin = (mj_pc_shotScript) engine.getCustomData().get(PLUGIN_KEY);
			// Only add plugin once to engine. We will onboard shots to it, not create a new plugin per shot.
			if (plugin == null) {
				plugin = new mj_pc_shotScript();
				engine.getCustomData().put(PLUGIN_KEY, plugin);
				// add to engine
				engine.addPlugin(plugin);
			}
			// Add piercing shot to list
			plugin.addPiercingProjectile(projectile);
		}
	}

	public static class mj_pc_shotScript implements EveryFrameCombatPlugin {

		private Set<DamagingProjectileAPI> piercingShots = new HashSet<DamagingProjectileAPI>();
		public static final Logger LOG = Logger.getLogger(mj_pc_shotScript.class);
		private float minTime = 5f;
		private float trackingRaduis = 1000f;
		// Range values for different ship hull sizes
		// I dislike hard coding, but the getRadius functions output a variable value, to my surprise.
		public static final float FRIGATE = 70f;
		public static final float DESTROYER = 100f;
		public static final float CRUISER = 130f;
		public static final float CAPITAL_SHIP = 150f;

		mj_pc_shotScript() {
		}

		@Override
		public void advance(float arg0, List<InputEventAPI> arg1) {
			try {
				for (DamagingProjectileAPI projectile : piercingShots) {

					// Weapon range and pause stuff
					CombatEngineAPI engine = Global.getCombatEngine();
					if (engine.isPaused()) {
						return;
					}

					if (projectile.isExpired() || projectile.getElapsed() > minTime) {
						piercingShots.remove(projectile);
						if (piercingShots.size() == 0) {
							return;
						}
						continue;
					}
					Vector2f projLoc = projectile.getLocation();
					// Iterate through all ships on the battlefield to check for potential targets.
					// Inefficient but necessary.
					for (ShipAPI ship : engine.getShips()) {
						// --- Target Validity Checks ---
						// Ignore the ship that fired the projectile, allied ships, ship hulks, and
						// phased ships.
						if (ship == projectile.getSource() ||
								ship.getOwner() == projectile.getOwner() ||
								ship.isHulk() ||
								ship.isPhased()) {
							continue;
						}

						// --- Proximity and Overlap Checks ---
						// 1. A preliminary distance check to quickly discard ships that are too far
						// away.
						// Efficiency check
						float distanceSquared = Misc.getDistanceSq(projLoc, ship.getLocation());

						// As insane as it sounds, ship radius is NOT a reliable indicator, its a
						// variable even at the hullspec level?!
						// Goes from 30 to 300 depending on some circumstances for the odyssey, for
						// example. Its simply impossible to work with it, setting a manual value
						// instead.
						float combinedRadius = projectile.getCollisionRadius() + trackingRaduis;
						if (distanceSquared > combinedRadius * combinedRadius) {
							continue;
						}

						boolean collide = false;
						if (ship.isPointInBounds(projLoc)) {
							collide = true;
						}
						String hullsize = ship.getHullSize().toString();
						float range = 600;
						if (hullsize.equalsIgnoreCase("FRIGATE")) {
							range = FRIGATE;
						} else if (hullsize.equalsIgnoreCase("DESTROYER")) {
							range = DESTROYER;
						} else if (hullsize.equalsIgnoreCase("CRUISER")) {
							range = CRUISER;
						} else if (hullsize.equalsIgnoreCase("CAPITAL_SHIP")) {
							range = CAPITAL_SHIP;
						}

						// setShieldColorBasedOnRange(ship, range * range, distanceSquared);
						if (distanceSquared > range * range) {
							collide = true;
						}
						if (collide) {
							// 1. Store pre-damage state of the specific impact area
							float initialHull = ship.getHullLevel();
							ArmorGridAPI armorGrid = ship.getArmorGrid();
							int[] cell = armorGrid.getCellAtLocation(projLoc);
							float initialArmor = 0f;
							if (cell != null) {
								initialArmor = armorGrid.getArmorValue(cell[0], cell[1]);
							}

							// 2. Apply damage directly to the ship at the point of overlap.
							engine.applyDamage(
									ship, // Target ship
									projLoc, // Point of impact
									projectile.getDamageAmount(), // Base damage from .proj file
									projectile.getDamageType(), // Damage type (e.g., KINETIC, HIGH_EXPLOSIVE) from
																// .proj file
									projectile.getEmpAmount(), // EMP damage amount
									true, // Set to true if the damage should bypass shields 
									false, // Set to true if damage should ignore armor
									projectile.getSource() // The ship that fired this projectile
							);
						
							// 3. Check post-damage state
							float finalHull = ship.getHullLevel();
							float finalArmor = 0f;
							if (cell != null) {
								finalArmor = armorGrid.getArmorValue(cell[0], cell[1]);
							}

							// 4. If real damage was dealt, mark the ship and stop processing this
							// projectile
							boolean wasDamaged = (finalHull < initialHull) || (finalArmor < initialArmor);

							if (wasDamaged) {
								projectile.setDamageAmount(0);
								projectile.setCollisionClass(CollisionClass.HITS_SHIPS_ONLY_NO_FF);
								//remove projectile from script so it stops being a menace
								piercingShots.remove(projectile);
								engine.addHitParticle(projLoc, Misc.ZERO, 100, 1f, 0.1f, Color.ORANGE);
								break;
							}
						}

					}
				}
			} 
			//Usually this doesn't happen, but we want to be robust for the user experience
			catch (Exception e) {
				piercingShots.clear();
				return;
			}
		}

		public void addPiercingProjectile(DamagingProjectileAPI projectile) {
			piercingShots.add(projectile);
			// LOG.info("Added reflector ship: " + ship.getHullSpec().getHullName());
		}

		public void setShieldColorBasedOnRange(ShipAPI ship, float requiredRange, float currentRange) {
			if (ship == null || ship.getShield() == null) {
				return; // No ship or no shield to modify
			}

			// Define base colors with desired alpha
			Color RED_SHIELD = new Color(255, 0, 0, 150); // Red with alpha 150
			Color BLUE_SHIELD = new Color(0, 0, 255, 150); // Blue with alpha 150

			if (currentRange > requiredRange) {
				ship.getShield().setInnerColor(RED_SHIELD);
				ship.getShield().setRingColor(RED_SHIELD);
			} else {
				ship.getShield().setInnerColor(BLUE_SHIELD);
				ship.getShield().setRingColor(BLUE_SHIELD);
			}
		}

		@Override
		public void init(CombatEngineAPI arg0) {
			piercingShots.clear();
			return;
		}

		@Override
		public void processInputPreCoreControls(float arg0, List<InputEventAPI> arg1) {
			return;
		}

		@Override
		public void renderInUICoords(ViewportAPI arg0) {
			return;
		}

		@Override
		public void renderInWorldCoords(ViewportAPI arg0) {
			return;
		}

	}

}
