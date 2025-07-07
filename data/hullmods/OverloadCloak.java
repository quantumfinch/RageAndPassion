package data.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.FluxTrackerAPI;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.ids.Stats;


// import org.apache.log4j.Logger; // *** THIS IS THE CRUCIAL CHANGE ***
// import org.apache.log4j.Level; // *** AND THIS, if you use levels like Level.INFO ***

public class OverloadCloak extends BaseHullMod {

    String moduleStatus;
 // DECLARE AND INITIALIZE YOUR LOGGER HERE
    // private static final Logger LOG = Global.getLogger(OverloadCloak.class); // This line is crucial!
	public static float MAX_TIME_MULT = 3f;
	private static float effectLevel=0.0f;
	public static float SHIP_ALPHA_MULT = 0.25f;
	
    @Override
    public void advanceInCombat(ShipAPI ship, float amount) {
        FluxTrackerAPI flux = ship.getFluxTracker();
		if(!flux.isOverloaded()){
			effectLevel=0.0f;
            phasingLogic(ship.getMutableStats(),ship.getId(),"OFF",effectLevel);
			return;
		}
        else{
            boolean player = ship == Global.getCombatEngine().getPlayerShip();
            if(effectLevel<1.0){
				effectLevel+=.05;
			}
            if (Global.getCombatEngine().isPaused()) {
			    return;
		    }
            //Here is where the magic happens
            phasingLogic(ship.getMutableStats(),ship.getId(),"ON",effectLevel);

        }

    }

    private void phasingLogic(MutableShipStatsAPI stats, String id, String state,float effectLevel){
		ShipAPI ship = null;
		boolean player = false;
		if (stats.getEntity() instanceof ShipAPI) {
			ship = (ShipAPI) stats.getEntity();
			player = ship == Global.getCombatEngine().getPlayerShip();
			id = id + "_" + ship.getId();
		} else {
			return;
		}
		//TODO: Must implement effect level properly, icons and stuff
		// if (player) {
		// 	maintainStatus(ship, state, effectLevel);
		// }
		
		if (Global.getCombatEngine().isPaused()) {
			return;
		}
		//Does not need to be a "cloak ship"
		//No speed buffs
		
		//No stats applied, none removed
		float level = effectLevel;
		float levelForAlpha = level;

		if (state.equals("ON")) {
			ship.setPhased(true);
			levelForAlpha = level;
		} else if (state.equals("OFF")) {
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
		
	}

	private static float getMaxTimeMult(MutableShipStatsAPI stats) {
		return 1f + (MAX_TIME_MULT - 1f) * stats.getDynamic().getValue(Stats.PHASE_TIME_BONUS_MULT);
	}

    public boolean isApplicableToShip(ShipAPI ship) {
		return ship != null && ship.getPhaseCloak() == null;
	}
	
	public String getUnapplicableReason(ShipAPI ship) {
		return "Unable to apply to a phase ship";
	}

}
