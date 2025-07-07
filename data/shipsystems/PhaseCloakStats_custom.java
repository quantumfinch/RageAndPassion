package data.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.PhaseCloakSystemAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
// import org.apache.log4j.Logger; // *** THIS IS THE CRUCIAL CHANGE ***
// import org.apache.log4j.Level; // *** AND THIS, if you use levels like Level.INFO ***

public class PhaseCloakStats_custom extends BaseShipSystemScript {
	// private static final Logger LOG = Global.getLogger(PhaseCloakStats_custom.class); // This line is crucial!
	public static Color JITTER_COLOR = new Color(255,175,255,255);
	public static float JITTER_FADE_TIME = 0.5f;
	
	public static float SHIP_ALPHA_MULT = 0.25f;
	//public static float VULNERABLE_FRACTION = 0.875f;
	public static float VULNERABLE_FRACTION = 0f;
	public static float INCOMING_DAMAGE_MULT = 0.25f;
	
	
	public static float MAX_TIME_MULT = 3f;
	
//	/**
//	 * Top speed multiplier when at 100% disruption.  
//	 */
//	public static float DISRUPT_SPEED_MULT = 0.33f;
//	/**
//	 * Disruption clears up at this rate. Always clears up fully when cloak is turned off.
//	 */
//	public static float DISRUPT_DECAY_RATE = 0.25f;
//	/**
//	 * Seconds that need to elapse since a disruption increase before it starts to decay.
//	 */
//	public static float DISRUPT_DECAY_DELAY = 2f;
//	
//	// Compared to ship's max flux to figure out how quickly things disrupt.
//	public static float PROJECTILE_DAMAGE_MULT = 3f;
//	public static float BEAM_DAMAGE_MULT = 0.1f;
//	public static float MASS_DAMAGE_MULT = 1f;
	
	public static boolean FLUX_LEVEL_AFFECTS_SPEED = true;
	public static float MIN_SPEED_MULT = 0.33f;
	public static float BASE_FLUX_LEVEL_FOR_MIN_SPEED = 0.5f;
	
	protected Object STATUSKEY1 = new Object();
	protected Object STATUSKEY2 = new Object();
	protected Object STATUSKEY3 = new Object();
	protected Object STATUSKEY4 = new Object();
	
	
	public static float getMaxTimeMult(MutableShipStatsAPI stats) {
		return 1f + (MAX_TIME_MULT - 1f) * stats.getDynamic().getValue(Stats.PHASE_TIME_BONUS_MULT);
	}
	
	protected boolean isDisruptable(ShipSystemAPI cloak) {
		return cloak.getSpecAPI().hasTag(Tags.DISRUPTABLE);
	}
	
	protected float getDisruptionLevel(ShipAPI ship) {
		//return disruptionLevel;
		//if (true) return 0f;
		if (FLUX_LEVEL_AFFECTS_SPEED) {
			float threshold = ship.getMutableStats().getDynamic().getMod(
					Stats.PHASE_CLOAK_FLUX_LEVEL_FOR_MIN_SPEED_MOD).computeEffective(BASE_FLUX_LEVEL_FOR_MIN_SPEED);
			if (threshold <= 0) return 1f;
			float level = ship.getHardFluxLevel() / threshold;
			if (level > 1f) level = 1f;
			return level;
		}
		return 0f;
	}
	
	protected void maintainStatus(ShipAPI playerShip, State state, float effectLevel) {
		float level = effectLevel;
		float f = VULNERABLE_FRACTION;
		
		ShipSystemAPI cloak = playerShip.getPhaseCloak();
		if (cloak == null) cloak = playerShip.getSystem();
		if (cloak == null) return;
		
		if (level > f) {
//			Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY1,
//					cloak.getSpecAPI().getIconSpriteName(), cloak.getDisplayName(), "can not be hit", false);
			Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY2,
					cloak.getSpecAPI().getIconSpriteName(), cloak.getDisplayName(), "time flow altered", false);
		} else {
//			float INCOMING_DAMAGE_MULT = 0.25f;
//			float percent = (1f - INCOMING_DAMAGE_MULT) * getEffectLevel() * 100;
//			Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY3,
//					spec.getIconSpriteName(), cloak.getDisplayName(), "damage mitigated by " + (int) percent + "%", false);
		}
		
		if (FLUX_LEVEL_AFFECTS_SPEED) {
			if (level > f) {
				if (getDisruptionLevel(playerShip) <= 0f) {
					Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY3,
							cloak.getSpecAPI().getIconSpriteName(), "phase coils stable", "top speed at 100%", false);
				} else {
					//String disruptPercent = "" + (int)Math.round((1f - disruptionLevel) * 100f) + "%";
					//String speedMultStr = Strings.X + Misc.getRoundedValue(getSpeedMult());
					String speedPercentStr = (int) Math.round(getSpeedMult(playerShip, effectLevel) * 100f) + "%";
					Global.getCombatEngine().maintainStatusForPlayerShip(STATUSKEY3,
							cloak.getSpecAPI().getIconSpriteName(),
							//"phase coils at " + disruptPercent, 
							"phase coil stress", 
							"top speed at " + speedPercentStr, true);
				}
			}
		}
	}
	
	public float getSpeedMult(ShipAPI ship, float effectLevel) {
		if (getDisruptionLevel(ship) <= 0f) return 1f;
		return MIN_SPEED_MULT + (1f - MIN_SPEED_MULT) * (1f - getDisruptionLevel(ship) * effectLevel); 
	}

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
		ShipAPI ship = null;
		boolean player = false;

		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
			player = ship == Global.getCombatEngine().getPlayerShip();
			id = id + "_" + ship.getId();
		} else {
			return;
		}
		
		if (player) {
			maintainStatus(ship, state, effectLevel);
		}
		
		if (Global.getCombatEngine().isPaused()) {
			return;
		}
		
		if (FLUX_LEVEL_AFFECTS_SPEED) {
			if (state == State.ACTIVE || state == State.OUT || state == State.IN) {
				float mult = getSpeedMult(ship, effectLevel);
				if (mult < 1f) {
					stats.getMaxSpeed().modifyMult(id + "_2", mult);
				} else {
					stats.getMaxSpeed().unmodifyMult(id + "_2");
				}
			}
		}
		
		if (state == State.COOLDOWN || state == State.IDLE) {
			unapply(stats, id);
			return;
		}
		
		float speedPercentMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_SPEED_MOD).computeEffective(0f);
		float accelPercentMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_ACCEL_MOD).computeEffective(0f);
		stats.getMaxSpeed().modifyPercent(id, speedPercentMod * effectLevel);
		stats.getAcceleration().modifyPercent(id, accelPercentMod * effectLevel);
		stats.getDeceleration().modifyPercent(id, accelPercentMod * effectLevel);
		
		float speedMultMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_SPEED_MOD).getMult();
		float accelMultMod = stats.getDynamic().getMod(Stats.PHASE_CLOAK_ACCEL_MOD).getMult();
		stats.getMaxSpeed().modifyMult(id, speedMultMod * effectLevel);
		stats.getAcceleration().modifyMult(id, accelMultMod * effectLevel);
		stats.getDeceleration().modifyMult(id, accelMultMod * effectLevel);
		
		float level = effectLevel;

		float levelForAlpha = level;
		
		if (state == State.IN || state == State.ACTIVE) {
			ship.setPhased(true);
			levelForAlpha = level;
		} else if (state == State.OUT) {
			if (level > 0.5f) {
				ship.setPhased(true);
			} else {
				ship.setPhased(false);
			}
			levelForAlpha = level;
		}
		
		ship.setExtraAlphaMult(1f - (1f - SHIP_ALPHA_MULT) * levelForAlpha);
		ship.setApplyExtraAlphaToEngines(true);
		
		
		float extra = 0f;
		float shipTimeMult = 1f + (getMaxTimeMult(stats) - 1f) * levelForAlpha * (1f - extra);
		stats.getTimeMult().modifyMult(id, shipTimeMult);
		if (player) {
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
		} else {
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}

		// Calculate how much flux to refund this specific frame
        float amount = Global.getCombatEngine().getElapsedInLastFrame();
		
        float fluxToRefund = .05f * effectLevel * amount;
		// LOG.info("Refunded: "+fluxToRefund);
        // Give flux back to the ship
        ship.getFluxTracker().decreaseFlux(fluxToRefund);
		
	}

	public void unapply(MutableShipStatsAPI stats, String id) {
		
		ShipAPI ship = null;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
		} else {
			return;
		}
		
		Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getTimeMult().unmodify(id);
		
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxSpeed().unmodifyMult(id + "_2");
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		
		ship.setPhased(false);
		ship.setExtraAlphaMult(1f);
		
		ShipSystemAPI cloak = ship.getPhaseCloak();
		if (cloak == null) cloak = ship.getSystem();
		if (cloak != null) {
			((PhaseCloakSystemAPI)cloak).setMinCoilJitterLevel(0f);
		}
		
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
//		if (index == 0) {
//			return new StatusData("time flow altered", false);
//		}
//		float percent = (1f - INCOMING_DAMAGE_MULT) * effectLevel * 100;
//		if (index == 1) {
//			return new StatusData("damage mitigated by " + (int) percent + "%", false);
//		}
		return null;
	}
}
