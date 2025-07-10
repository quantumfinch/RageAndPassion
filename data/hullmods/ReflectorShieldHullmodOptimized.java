// ReflectorShieldHullmod.java
package data.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.CombatListenerManagerAPI;
import com.fs.starfarer.api.combat.listeners.DamageTakenModifier;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.util.Misc;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger; // *** THIS IS THE CRUCIAL CHANGE ***
import org.apache.log4j.Level; // *** AND THIS, if you use levels like Level.INFO ***
import java.util.HashMap;

public class ReflectorShieldHullmodOptimized extends BaseHullMod {
    

    private ReflectorShieldPlugin reflectorListener = new ReflectorShieldPlugin();
    private static final Color reflectShieldColorRing = new Color(15, 128, 128,200);
    private static final Color reflectShieldColorInterior = new Color(15, 128, 128,150);
    private static final HashMap<ShipVariantAPI,ShipColorScheme> registeredColors = new HashMap<ShipVariantAPI,ShipColorScheme>();
    private static final Logger LOG = Logger.getLogger(ReflectorShieldHullmodOptimized.class);
    @Override
    public void applyEffectsBeforeShipCreation(ShipAPI.HullSize hullSize, MutableShipStatsAPI stats, String id) {
        // ShipVariantAPI variant=  stats.getVariant(); 
        // ShipHullSpecAPI spec=  variant.getHullSpec();
        // ShipHullSpecAPI.ShieldSpecAPI shieldspec=spec.getShieldSpec();
        // ShipColorScheme colors = new ShipColorScheme(shieldspec.getInnerColor(),shieldspec.getRingColor());
        // registeredColors.put(variant,colors);
        // if(shieldspec!=null){
        //     shieldspec.setRingColor(Color.cyan);
        //     shieldspec.setInnerColor(reflectShieldColorInterior);
        // }
       
        // stats.getShieldUpkeepMult().modifyMult(id, 1.5f); // Increase shield upkeep
        // stats.getShieldTurnRateMult().modifyMult(id, 0.8f); // Slightly reduce turn rate
    }
    
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id) {
        ShieldAPI shield = ship.getShield();
        
        if(shield!=null){
            shield.setRingColor(Color.cyan);
            shield.setInnerColor(reflectShieldColorInterior);
        }
        // ShipVariantAPI variant = ship.getVariant();
        // LOG.info("Apply called variant");
        // if(variant.getNonBuiltInHullmods().contains("reflector_shield_optimized")){ 
        //     LOG.info("hull mods contain optimized reflector shield");
        // ShipHullSpecAPI spec=  variant.getHullSpec();
        // ShipHullSpecAPI.ShieldSpecAPI shieldspec=spec.getShieldSpec();
        // Color innerColor = new Color(shieldspec.getInnerColor().getRed(),shieldspec.getInnerColor().getGreen(),shieldspec.getInnerColor().getBlue(),shieldspec.getInnerColor().getAlpha());
        // Color ringColor = new Color(shieldspec.getRingColor().getRed(),shieldspec.getRingColor().getGreen(),shieldspec.getRingColor().getBlue(),shieldspec.getRingColor().getAlpha());

        // ShipColorScheme colors = new ShipColorScheme(innerColor,ringColor);
        // registeredColors.put(variant,colors);
        // if(shieldspec!=null){
        //     shieldspec.setRingColor(Color.cyan);
        //     shieldspec.setInnerColor(reflectShieldColorInterior);
        // }
        ship.addListener(reflectorListener);
    }
    
    @Override
    public String getDescriptionParam(int index, ShipAPI.HullSize hullSize) {
        switch (index) {
            case 0: return "10%";
            default: return null;
        }

        
    }

    public class ShipColorScheme {
    private Color innerRing;
    private Color outerRing;

    public ShipColorScheme(Color inner, Color outer) {
        this.innerRing = inner;
        this.outerRing = outer;
    }

    public Color getInnerRing() {
        return innerRing;
    }

    public Color getOuterRing() {
        return outerRing;
    }
}

public static class ReflectorShieldPlugin implements DamageTakenModifier {
        // private static final Logger LOG = Logger.getLogger(ReflectorShieldPlugin.class);
        
        private Set<DamagingProjectileAPI> processedProjectiles = new HashSet<DamagingProjectileAPI>();
        
        @Override
        public String modifyDamageTaken(Object param, CombatEntityAPI target, 
			DamageAPI damage, Vector2f point, boolean shieldHit){
            if(param instanceof DamagingProjectileAPI&&shieldHit==true&&target instanceof ShipAPI){
            // LOG.info("Applicable projectile hit");
            reflectProjectile((DamagingProjectileAPI) param, (ShipAPI) target);
            processedProjectiles.add((DamagingProjectileAPI) param);
            }
            return null;
        }

        
        private void addReflectionEffect(Vector2f location) {
            CombatEngineAPI engine = Global.getCombatEngine();
            
            // Add reflection flash
            engine.addHitParticle(
                location,
                new Vector2f(0, 0),
                30f,
                1f,
                0.3f,
                Color.CYAN
            );
            
            // Add sparks
            for (int i = 0; i < 8; i++) {
                Vector2f sparkVel = Misc.getUnitVectorAtDegreeAngle(i * 45f);
                sparkVel.scale(50f + (float) Math.random() * 100f);
                
                engine.addHitParticle(
                    location,
                    sparkVel,
                    5f,
                    0.8f,
                    0.5f,
                    Color.WHITE
                );
            }
        }
        
        private void reflectProjectile(DamagingProjectileAPI originalProjectile, ShipAPI thisShip) {
        ShieldAPI shield = thisShip.getShield();
        if(shield!=null){
            shield.setRingColor(Color.cyan);
            shield.setInnerColor(reflectShieldColorInterior);
        }
            CombatEngineAPI engine = Global.getCombatEngine();
            // Get projectile info
            String projectileSpecId = originalProjectile.getProjectileSpecId();
            WeaponAPI sourceWeapon = originalProjectile.getWeapon();
            Vector2f projectilePos = new Vector2f(originalProjectile.getLocation());
            
            // Calculate reflection direction
            Vector2f toShip = Vector2f.sub(thisShip.getLocation(), projectilePos, null);
            toShip.normalise();
            
            Vector2f originalVel = originalProjectile.getVelocity();
            float speed = originalVel.length();
            
            // Reflect velocity: v' = v - 2(v·n)n where n is the normal
            Vector2f normal = new Vector2f(toShip);
            float dotProduct = Vector2f.dot(originalVel, normal);
            Vector2f reflection = new Vector2f(normal);
            reflection.scale(2 * dotProduct);
            Vector2f reflectedVel = Vector2f.sub(originalVel, reflection, null);
            
            // // Apply 2x hard flux damage
            // float damage = originalProjectile.getDamageAmount();
            // ship.getFluxTracker().increaseFlux(damage*1.1f, true);
            // LOG.info(thisShip.getName());
            
            // Remove original projectile
            engine.removeEntity(originalProjectile);
            
            // Create reflected projectile using the original weapon
            // LOG.info("Using original weapon: " + sourceWeapon.getId());
            if(thisShip!=null){
                CombatEntityAPI reflectedProjectile = engine.spawnProjectile(
                    thisShip,
                    sourceWeapon,
                    sourceWeapon.getId(), // Use weapon ID
                    projectilePos,
                    Misc.getAngleInDegrees(reflectedVel),
                    reflectedVel
                );
                // Mark as reflected
            reflectedProjectile.getCustomData().put("reflected", true);
            }
            
            
            // Add visual effect
            addReflectionEffect(projectilePos);
            
            // LOG.info("Reflected projectile: " + projectileSpecId);
        }

    }
    
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return ship.getShield() != null;
    }
    
    @Override
    public String getUnapplicableReason(ShipAPI ship) {
        if (ship.getShield() == null) {
            return "Ship has no shield";
        }
        return null;
    }
}